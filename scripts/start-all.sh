#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLOUD_ROOT="$PROJECT_ROOT/gouyan-cloud"
RUNTIME_DIR="$PROJECT_ROOT/.runtime"
MYSQL_PASSWORD_VALUE="${MYSQL_PASSWORD:-}"
BACKEND_ONLY=false
SKIP_BUILD=false
HYSTRIX_DEMO=false

for argument in "$@"; do
  case "$argument" in
    --backend-only) BACKEND_ONLY=true ;;
    --skip-build) SKIP_BUILD=true ;;
    --hystrix-demo) HYSTRIX_DEMO=true ;;
    *) echo "未知参数: $argument" >&2; exit 1 ;;
  esac
done

mkdir -p "$RUNTIME_DIR"

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "缺少命令: $1" >&2; exit 1; }
}

port_is_open() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

wait_for_port() {
  local name="$1" port="$2" count=0
  while ! port_is_open "$port"; do
    count=$((count + 1))
    if [ "$count" -ge 90 ]; then
      echo "$name 未在 90 秒内监听端口 ${port}，请查看 $RUNTIME_DIR/${name}.log" >&2
      exit 1
    fi
    sleep 1
  done
  echo "[UP] $name :$port"
}

start_java() {
  local name="$1" port="$2" module="$3" run_arguments starter_pid java_pid
  shift 3
  if port_is_open "$port"; then
    echo "[REUSE] $name :$port 已运行"
    return
  fi
  run_arguments="--spring.datasource.druid.password=$MYSQL_PASSWORD_VALUE"
  for argument in "$@"; do
    run_arguments="$run_arguments $argument"
  done
  (cd "$CLOUD_ROOT/$module" && nohup mvn org.springframework.boot:spring-boot-maven-plugin:2.3.2.RELEASE:run \
    "-Dspring-boot.run.arguments=$run_arguments" >"$RUNTIME_DIR/$name.log" 2>&1 & echo $! >"$RUNTIME_DIR/$name.starter.pid")
  wait_for_port "$name" "$port"
  starter_pid="$(cat "$RUNTIME_DIR/$name.starter.pid")"
  java_pid="$(lsof -nP -t -iTCP:"$port" -sTCP:LISTEN | head -1)"
  echo "$java_pid" >"$RUNTIME_DIR/$name.pid"
  echo "$starter_pid" >"$RUNTIME_DIR/$name.starter.pid"
  echo "$port" >"$RUNTIME_DIR/$name.managed"
}

start_frontend() {
  local name="$1" port="$2" directory="$3"
  if port_is_open "$port"; then
    echo "[REUSE] $name :$port 已运行"
    return
  fi
  if [ ! -d "$directory/node_modules" ]; then
    echo "$name 缺少 node_modules，请先在 $directory 执行 npm install" >&2
    exit 1
  fi
  (cd "$directory" && NODE_OPTIONS=--openssl-legacy-provider nohup npm run serve \
    >"$RUNTIME_DIR/$name.log" 2>&1 & echo $! >"$RUNTIME_DIR/$name.pid")
  wait_for_port "$name" "$port"
  echo "$port" >"$RUNTIME_DIR/$name.managed"
}

require_command java
require_command mvn
require_command mysqladmin
require_command mysql
require_command lsof

if ! MYSQL_PWD="$MYSQL_PASSWORD_VALUE" mysqladmin ping -uroot --silent; then
  echo "MySQL 未运行。Homebrew 环境可执行: brew services start mysql" >&2
  exit 1
fi
if ! MYSQL_PWD="$MYSQL_PASSWORD_VALUE" mysql -uroot -Nse "show databases like 'movie'" | grep -qx movie; then
  echo "数据库 movie 不存在，请先导入 sql2023/movie.sql" >&2
  exit 1
fi

if [ "$SKIP_BUILD" = false ]; then
  (cd "$CLOUD_ROOT" && mvn install -DskipTests)
fi

start_java eureka-peer1 7001 gouyan-eureka --spring.profiles.active=peer1
start_java eureka-peer2 7002 gouyan-eureka --spring.profiles.active=peer2
start_java actor 9000 gouyan-actor
start_java auth 9002 gouyan-auth
if [ "$HYSTRIX_DEMO" = true ]; then
  start_java bill 9004 gouyan-bill --spring.profiles.active=druid,hystrix-demo
else
  start_java bill 9004 gouyan-bill
fi
start_java cinema 9006 gouyan-cinema
start_java hall 9009 gouyan-hall
if [ "$HYSTRIX_DEMO" = true ]; then
  start_java movie-a 9010 gouyan-movie --demo.movie.delay-ms=1500
else
  start_java movie-a 9010 gouyan-movie
fi
start_java movie-b 9011 gouyan-movie --server.port=9011 --eureka.instance.instance-id=gouyan-movie:9011
start_java admin 8183 gouyan-admin
start_java gateway 8181 gouyan-gateway

if [ "$BACKEND_ONLY" = false ]; then
  require_command npm
  start_frontend user-web 8080 "$PROJECT_ROOT/gouyan-web/gouyan-user"
  start_frontend admin-web 8888 "$PROJECT_ROOT/gouyan-web/gouyan-ui"
  echo "启动完成。"
  echo "用户前台: http://127.0.0.1:8080"
  echo "管理后台: http://127.0.0.1:8888"
else
  echo "后端服务启动完成。"
fi

echo "API 网关: http://127.0.0.1:8181（仅供接口调用，根路径无页面）"
