#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUNTIME_DIR="$PROJECT_ROOT/.runtime"

if [ ! -d "$RUNTIME_DIR" ]; then
  echo "没有由启动脚本创建的运行记录。"
  exit 0
fi

for pid_file in "$RUNTIME_DIR"/*.pid; do
  case "$pid_file" in *.starter.pid) continue ;; esac
  [ -e "$pid_file" ] || continue
  name="$(basename "$pid_file" .pid)"
  pid="$(cat "$pid_file")"
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid"
    echo "[STOP] $name PID=$pid"
    for attempt in 1 2 3 4 5; do
      kill -0 "$pid" 2>/dev/null || break
      sleep 1
    done
  fi
  rm -f "$pid_file"
done

for pid_file in "$RUNTIME_DIR"/*.starter.pid; do
  [ -e "$pid_file" ] || continue
  pid="$(cat "$pid_file")"
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
  fi
  rm -f "$pid_file"
done

# Maven/npm 的启动进程可能先退出而实际服务子进程仍存活；只处理启动脚本标记的端口。
for managed_file in "$RUNTIME_DIR"/*.managed; do
  [ -e "$managed_file" ] || continue
  port="$(cat "$managed_file")"
  pid="$(lsof -nP -t -iTCP:"$port" -sTCP:LISTEN | head -1 || true)"
  if [ -n "$pid" ]; then
    kill "$pid" 2>/dev/null || true
    echo "[STOP] port=$port PID=$pid"
  fi
  rm -f "$managed_file"
done

echo "停止信号已发送；脚本不会停止启动前已存在或由其他方式启动的进程。"
