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

# ------------------------------------------------------------
# 1. Parse arguments
# ------------------------------------------------------------

for argument in "$@"; do
  case "$argument" in
    --expect-hystrix) EXPECT_HYSTRIX=true ;;
    --backend-only) BACKEND_ONLY=true ;;
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

for eureka_port in 7001 7002; do

  registry="$(
    curl -fsS \
      -H 'Accept: application/json' \
      "http://127.0.0.1:$eureka_port/eureka/apps"
  )"

  printf '%s' "$registry" |
    python_run -c '
import json, sys

data = json.load(sys.stdin)

apps = data["applications"]["application"]

if not isinstance(apps, list):
    apps = [apps]

names = {a["name"] for a in apps}

required = {
    "GOUYAN-ACTOR",
    "GOUYAN-AUTH",
    "GOUYAN-BILL",
    "GOUYAN-CINEMA",
    "GOUYAN-HALL",
    "GOUYAN-MOVIE",
    "CLOUD-GATEWAY"
}

missing = required - names

assert not missing, "missing registrations: %s" % sorted(missing)

movie = next(a for a in apps if a["name"] == "GOUYAN-MOVIE")

instances = movie["instance"]

if not isinstance(instances, list):
    instances = [instances]

assert len(instances) >= 2, "movie instance count < 2"
'

  echo "[PASS] Eureka $eureka_port 注册表与 Movie 双实例"

done

# ------------------------------------------------------------
# 6. Login
# ------------------------------------------------------------

login_body="$(
  curl -fsS \
    -H 'Content-Type: application/json' \
    -d "{\"userName\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" \
    "$BASE_URL/sysUser/login"
)"

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

served_by=""
fallback_count=0

for attempt in 1 2 3 4 5 6; do

  feign_body="$(
    curl -fsS \
      -H "Token: $token" \
      "$BASE_URL/sysBill/movie/$movie_id"
  )"

  is_fallback="$(
    printf '%s' "$feign_body" |
      python_run -c '
import json, sys
print(str(json.load(sys.stdin).get("fallback", False)).lower())
'
  )"

  if [ "$is_fallback" = "true" ]; then

    fallback_count=$((fallback_count + 1))

  else

    assert_code_200 "Feign 调用 $attempt" "$feign_body"

    current_instance="$(
      printf '%s' "$feign_body" |
        python_run -c '
import json, sys
print(json.load(sys.stdin).get("servedBy", "unknown"))
'
    )"

    served_by="$served_by $current_instance"

  fi

done

instance_count="$(
  printf '%s\n' $served_by |
    sed '/^$/d' |
    sort -u |
    wc -l |
    tr -d ' '
)"

if [ "$EXPECT_HYSTRIX" = true ]; then

  if [ "$fallback_count" -le 0 ]; then
    echo "[FAIL] 未观察到 Hystrix 降级" >&2
    exit 1
  fi

  hystrix_body="$(
    curl -fsS \
      -H "Token: $token" \
      "$BASE_URL/sysBill/hystrix/movie"
  )"

  assert_code_200 "Hystrix 状态" "$hystrix_body"

  echo "[PASS] Hystrix 降级次数: $fallback_count"

else

  if [ "$instance_count" -lt 2 ]; then
    echo "[FAIL] Ribbon 未轮询到两个 Movie 实例: $served_by" >&2
    exit 1
  fi

  echo "[PASS] Ribbon 轮询实例:$served_by"

fi

# ------------------------------------------------------------
# 13. Frontend checks
# ------------------------------------------------------------

if [ "$BACKEND_ONLY" = false ]; then

  user_code="$(
    curl -sS -o /dev/null -w '%{http_code}' \
      http://127.0.0.1:8080/
  )"

  if [ "$user_code" != "200" ]; then
    echo "[FAIL] 用户前端 HTTP $user_code" >&2
    exit 1
  fi

  admin_code="$(
    curl -sS -o /dev/null -w '%{http_code}' \
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
