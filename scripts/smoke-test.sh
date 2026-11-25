#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Cross-platform smoke test
# Supports: macOS / Windows (Git Bash, Cygwin, MSYS) / Linux
# ============================================================

BASE_URL="${BASE_URL:-http://127.0.0.1:8181}"
# Default website account (local development only; unrelated to MySQL credentials)
USERNAME="${GOUYAN_USERNAME:-admin}"
PASSWORD="${GOUYAN_PASSWORD:-123456}"

EXPECT_HYSTRIX=false
BACKEND_ONLY=false
WAIT_SECONDS=0

# ------------------------------------------------------------
# 1. Parse arguments
# ------------------------------------------------------------

for argument in "$@"; do
  case "$argument" in
    --expect-hystrix) EXPECT_HYSTRIX=true ;;
    --backend-only) BACKEND_ONLY=true ;;
    --wait) WAIT_SECONDS=90 ;;
    *)
      echo "未知参数: $argument" >&2
      exit 1
      ;;
  esac
done

# ------------------------------------------------------------
# 2. Detect operating system
# ------------------------------------------------------------

OS_TYPE="$(uname -s)"

case "$OS_TYPE" in
  Darwin*) OS_PLATFORM="macos" ;;
  Linux*) OS_PLATFORM="linux" ;;
  MINGW*|MSYS*|CYGWIN*) OS_PLATFORM="windows" ;;
  *)
    echo "不支持的操作系统: $OS_TYPE" >&2
    exit 1
    ;;
esac

echo "[ENV] Platform: $OS_PLATFORM"

# ------------------------------------------------------------
# 3. Command detection
# ------------------------------------------------------------

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "缺少命令: $1" >&2
    exit 1
  }
}

require_command curl

# Never let an unresponsive local service block the rehearsal.
curl() {
  command curl --connect-timeout 3 --max-time 15 "$@"
}

retry_ready() {
  local since=$SECONDS output
  while ! output="$("$@" 2>&1)"; do
    if (( SECONDS - since >= WAIT_SECONDS )); then
      printf '%s\n' "$output" >&2
      return 1
    fi
    sleep 2
  done
  printf '%s\n' "$output"
}

# Python command selection:
# macOS/Linux: python3
# Windows: python3 / python / py -3

PYTHON_KIND=""

if command -v python3 >/dev/null 2>&1 &&
   python3 -c 'import sys; sys.exit(0 if sys.version_info >= (3, 7) else 1)' >/dev/null 2>&1; then
  PYTHON_KIND="python3"
elif command -v python >/dev/null 2>&1 &&
   python -c 'import sys; sys.exit(0 if sys.version_info >= (3, 7) else 1)' >/dev/null 2>&1; then
  PYTHON_KIND="python"
elif command -v py >/dev/null 2>&1 &&
   py -3 -c 'import sys; sys.exit(0 if sys.version_info >= (3, 7) else 1)' >/dev/null 2>&1; then
  PYTHON_KIND="py"
else
  echo "缺少 Python 3，请安装 Python 3。" >&2
  exit 1
fi

# API responses use UTF-8. Windows Python otherwise decodes pipes using the
# system code page (for example GBK), which can corrupt JSON escape sequences.
python_run() {
  case "$PYTHON_KIND" in
    python3)
      python3 -X utf8 "$@"
      ;;
    python)
      python -X utf8 "$@"
      ;;
    py)
      py -3 -X utf8 "$@"
      ;;
  esac
}

# Verify Python version
python_run -c 'import sys; assert sys.version_info >= (3, 7), "Python 3.7+ required"' || {
  echo "需要 Python 3.7 或更高版本。" >&2
  exit 1
}

echo "[ENV] Python: $PYTHON_KIND"
echo "[ENV] curl: $(curl --version | head -1)"

# ------------------------------------------------------------
# 4. JSON helpers
# ------------------------------------------------------------

assert_code_200() {
  local name="$1"
  local body="$2"
  local code

  code="$(
    printf '%s' "$body" |
      python_run -c '
import json, sys
data = json.load(sys.stdin)
print(data.get("code"))
'
  )"

  if [ "$code" != "200" ]; then
    echo "[FAIL] $name: $body" >&2
    exit 1
  fi

  echo "[PASS] $name"
}

# ------------------------------------------------------------
# 5. Eureka registry and Movie instances
# ------------------------------------------------------------

check_registry() {
  local eureka_port="$1" registry
  registry="$(
    curl -fsS \
      -H 'Accept: application/json' \
      "http://127.0.0.1:$eureka_port/eureka/apps"
  )" || return 1

  printf '%s' "$registry" |
    python_run -c '
import json, sys

data = json.load(sys.stdin)

apps = data["applications"]["application"]

if not isinstance(apps, list):
    apps = [apps]

required = {
    "GOUYAN-ACTOR",
    "GOUYAN-AUTH",
    "GOUYAN-BILL",
    "GOUYAN-CINEMA",
    "GOUYAN-HALL",
    "GOUYAN-MOVIE",
    "CLOUD-GATEWAY",
    "GOUYAN-ADMIN"
}

def up_ports(app):
    instances = app["instance"]
    if not isinstance(instances, list):
        instances = [instances]
    return {int(i["port"]["$"]) for i in instances if i["status"] == "UP"}

missing = required - {a["name"] for a in apps if up_ports(a)}
assert not missing, "missing UP registrations: %s" % sorted(missing)

movie = next(a for a in apps if a["name"] == "GOUYAN-MOVIE")
assert up_ports(movie) == {9010, 9011}, "expected Movie UP on 9010 and 9011"
peer = next(a for a in apps if a["name"] == "GOUYAN-EUREKA")
assert up_ports(peer) == {7001, 7002}, "both Eureka peers must be UP"
'
}

for eureka_port in 7001 7002; do
  retry_ready check_registry "$eureka_port"
  echo "[PASS] Eureka $eureka_port 注册表与 Movie 双实例"
done

# ------------------------------------------------------------
# 6. Login
# ------------------------------------------------------------

login_payload="$(GOUYAN_USERNAME="$USERNAME" GOUYAN_PASSWORD="$PASSWORD" python_run -c '
import json, os
print(json.dumps(dict(userName=os.environ["GOUYAN_USERNAME"], password=os.environ["GOUYAN_PASSWORD"])))
')"
login() {
  printf '%s' "$login_payload" | curl -fsS \
    -H 'Content-Type: application/json' \
    --data-binary @- \
    "$BASE_URL/sysUser/login"
}
login_body="$(retry_ready login)"

assert_code_200 "登录与 Gateway 路由" "$login_body"

token="$(
  printf '%s' "$login_body" |
    python_run -c '
import json, sys
print(json.load(sys.stdin)["data"]["token"])
'
)"

if [ -z "$token" ]; then
  echo "[FAIL] 登录接口没有返回有效 Token" >&2
  exit 1
fi

# ------------------------------------------------------------
# 7. Movie query
# ------------------------------------------------------------

movie_body="$(
  curl -fsS \
    -H "Token: $token" \
    "$BASE_URL/sysMovie?pageNum=1&pageSize=10"
)"

assert_code_200 "电影查询" "$movie_body"

movie_id="$(
  printf '%s' "$movie_body" |
    python_run -c '
import json, sys

data = json.load(sys.stdin)["data"]

rows = data.get("rows", data) if isinstance(data, dict) else data

if not rows:
    raise SystemExit("电影列表为空，无法执行 Feign 测试")

print(rows[0]["movieId"])
'
)"

if [ -z "$movie_id" ]; then
  echo "[FAIL] 无法获取电影 ID" >&2
  exit 1
fi

# ------------------------------------------------------------
# 8. Cinema query
# ------------------------------------------------------------

cinema_body="$(
  curl -fsS \
    -H "Token: $token" \
    "$BASE_URL/sysCinema?pageNum=1&pageSize=10"
)"

assert_code_200 "影院查询" "$cinema_body"

# ------------------------------------------------------------
# 9. Hall query
# ------------------------------------------------------------

hall_body="$(
  curl -fsS \
    -H "Token: $token" \
    "$BASE_URL/sysHall?pageNum=1&pageSize=10"
)"

assert_code_200 "影厅与 MyBatis 查询" "$hall_body"

# ------------------------------------------------------------
# 10. Actor query
# ------------------------------------------------------------

actor_body="$(
  curl -fsS \
    -H "Token: $token" \
    "$BASE_URL/sysActor?pageNum=1&pageSize=10"
)"

assert_code_200 "演员服务查询" "$actor_body"

# ------------------------------------------------------------
# 11. Bill query
# ------------------------------------------------------------

bill_body="$(
  curl -fsS \
    -H "Token: $token" \
    "$BASE_URL/sysBill?pageNum=1&pageSize=10"
)"

assert_code_200 "订单服务查询" "$bill_body"

# ------------------------------------------------------------
# 12. OpenFeign and Ribbon / Hystrix
# ------------------------------------------------------------

served_by=()
fallback_count=0
instance_count=0

if [ "$EXPECT_HYSTRIX" = true ]; then
  fallback_count="$(GOUYAN_SMOKE_TOKEN="$token" python_run "$(dirname "$0")/smoke-hystrix.py" "$BASE_URL" "$movie_id")"
else
  previous_instance=""
  for attempt in 1 2 3 4 5 6; do
    feign_body="$(curl -fsS -H "Token: $token" "$BASE_URL/sysBill/movie/$movie_id")"
    assert_code_200 "Feign 调用 $attempt" "$feign_body"

    current_instance="$(
      printf '%s' "$feign_body" |
        python_run -c '
import json, sys
result = json.load(sys.stdin)
assert result["data"]["movieId"] == int(sys.argv[1]), "unexpected Feign movie"
print(result["servedBy"])
' "$movie_id"
    )"

    if [[ ! "$current_instance" =~ ^gouyan-movie:901[01]$ || "$current_instance" == "$previous_instance" ]]; then
      echo "[FAIL] Ribbon 未按 9010/9011 交替: ${served_by[*]} $current_instance" >&2
      exit 1
    fi
    served_by+=("$current_instance")
    previous_instance="$current_instance"
  done

  instance_count=2
  echo "[PASS] Ribbon 轮询实例: ${served_by[*]}"
fi

# ------------------------------------------------------------
# 13. Frontend checks
# ------------------------------------------------------------

if [ "$BACKEND_ONLY" = false ]; then

  user_code="$(
    retry_ready curl -fsS -o /dev/null -w '%{http_code}' \
      http://127.0.0.1:8080/
  )"

  if [ "$user_code" != "200" ]; then
    echo "[FAIL] 用户前端 HTTP $user_code" >&2
    exit 1
  fi

  admin_code="$(
    retry_ready curl -fsS -o /dev/null -w '%{http_code}' \
      http://127.0.0.1:8888/
  )"

  if [ "$admin_code" != "200" ]; then
    echo "[FAIL] 管理前端 HTTP $admin_code" >&2
    exit 1
  fi

  echo "[PASS] 用户前端与管理前端"

fi

# ------------------------------------------------------------
# 14. Final result
# ------------------------------------------------------------

echo ""
echo "======================================"
echo "全系统只读集成回归通过。"
echo "Platform: $OS_PLATFORM"
echo "Ribbon instances: $instance_count"
echo "Hystrix fallbacks: $fallback_count"
echo "======================================"
