#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8181}"
USERNAME="${GOUYAN_USERNAME:?请设置 GOUYAN_USERNAME}"
PASSWORD="${GOUYAN_PASSWORD:?请设置 GOUYAN_PASSWORD}"
EXPECT_HYSTRIX=false
BACKEND_ONLY=false

for argument in "$@"; do
  case "$argument" in
    --expect-hystrix) EXPECT_HYSTRIX=true ;;
    --backend-only) BACKEND_ONLY=true ;;
    *) echo "未知参数: $argument" >&2; exit 1 ;;
  esac
done

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "缺少命令: $1" >&2; exit 1; }
}

assert_code_200() {
  local name="$1" body="$2" code
  code="$(printf '%s' "$body" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("code"))')"
  if [ "$code" != "200" ]; then
    echo "[FAIL] $name: $body" >&2
    exit 1
  fi
  echo "[PASS] $name"
}

require_command curl
require_command python3

for eureka_port in 7001 7002; do
  registry="$(curl -fsS -H 'Accept: application/json' "http://127.0.0.1:$eureka_port/eureka/apps")"
  printf '%s' "$registry" | python3 -c 'import json,sys; d=json.load(sys.stdin); apps=d["applications"]["application"]; names={a["name"] for a in apps}; required={"GOUYAN-ACTOR","GOUYAN-AUTH","GOUYAN-BILL","GOUYAN-CINEMA","GOUYAN-HALL","GOUYAN-MOVIE","CLOUD-GATEWAY"}; missing=required-names; assert not missing, "missing registrations: %s" % sorted(missing); movie=next(a for a in apps if a["name"]=="GOUYAN-MOVIE"); instances=movie["instance"] if isinstance(movie["instance"],list) else [movie["instance"]]; assert len(instances)>=2, "movie instance count < 2"'
  echo "[PASS] Eureka $eureka_port 注册表与 Movie 双实例"
done

login_body="$(curl -fsS -H 'Content-Type: application/json' \
  -d "{\"userName\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" \
  "$BASE_URL/sysUser/login")"
assert_code_200 "登录与 Gateway 路由" "$login_body"
token="$(printf '%s' "$login_body" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["token"])')"

movie_body="$(curl -fsS -H "Token: $token" "$BASE_URL/sysMovie?pageNum=1&pageSize=10")"
assert_code_200 "电影查询" "$movie_body"
movie_id="$(printf '%s' "$movie_body" | python3 -c 'import json,sys; d=json.load(sys.stdin)["data"]; rows=d.get("rows",d) if isinstance(d,dict) else d; print(rows[0]["movieId"])')"

cinema_body="$(curl -fsS -H "Token: $token" "$BASE_URL/sysCinema?pageNum=1&pageSize=10")"
assert_code_200 "影院查询" "$cinema_body"

hall_body="$(curl -fsS -H "Token: $token" "$BASE_URL/sysHall?pageNum=1&pageSize=10")"
assert_code_200 "影厅与 MyBatis 查询" "$hall_body"
actor_body="$(curl -fsS -H "Token: $token" "$BASE_URL/sysActor?pageNum=1&pageSize=10")"
assert_code_200 "演员服务查询" "$actor_body"
bill_body="$(curl -fsS -H "Token: $token" "$BASE_URL/sysBill?pageNum=1&pageSize=10")"
assert_code_200 "订单服务查询" "$bill_body"

served_by=""
fallback_count=0
for attempt in 1 2 3 4 5 6; do
  feign_body="$(curl -fsS -H "Token: $token" "$BASE_URL/sysBill/movie/$movie_id")"
  if [ "$(printf '%s' "$feign_body" | python3 -c 'import json,sys; print(str(json.load(sys.stdin).get("fallback",False)).lower())')" = "true" ]; then
    fallback_count=$((fallback_count + 1))
  else
    assert_code_200 "Feign 调用 $attempt" "$feign_body"
    current_instance="$(printf '%s' "$feign_body" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("servedBy","unknown"))')"
    served_by="$served_by $current_instance"
  fi
done

instance_count="$(printf '%s\n' $served_by | sed '/^$/d' | sort -u | wc -l | tr -d ' ')"
if [ "$EXPECT_HYSTRIX" = true ]; then
  [ "$fallback_count" -gt 0 ] || { echo "[FAIL] 未观察到 Hystrix 降级" >&2; exit 1; }
  hystrix_body="$(curl -fsS -H "Token: $token" "$BASE_URL/sysBill/hystrix/movie")"
  assert_code_200 "Hystrix 状态" "$hystrix_body"
  echo "[PASS] Hystrix 降级次数: $fallback_count"
else
  [ "$instance_count" -ge 2 ] || { echo "[FAIL] Ribbon 未轮询到两个 Movie 实例: $served_by" >&2; exit 1; }
  echo "[PASS] Ribbon 轮询实例:$served_by"
fi

if [ "$BACKEND_ONLY" = false ]; then
  [ "$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/)" = "200" ] || { echo "[FAIL] 用户前端" >&2; exit 1; }
  [ "$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8888/)" = "200" ] || { echo "[FAIL] 管理前端" >&2; exit 1; }
  echo "[PASS] 用户前端与管理前端"
fi

echo "全系统只读集成回归通过。"
