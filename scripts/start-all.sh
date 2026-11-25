#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Cinema Ticketing Microservices
# Cross-platform launcher:
# Windows (Git Bash / Cygwin / MSYS)
# macOS
# Linux
# ============================================================

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLOUD_ROOT="$PROJECT_ROOT/gouyan-cloud"
RUNTIME_DIR="$PROJECT_ROOT/.runtime"

MYSQL_PASSWORD_VALUE="${MYSQL_PASSWORD:-}"

BACKEND_ONLY=false
SKIP_BUILD=false
HYSTRIX_DEMO=false

# ------------------------------------------------------------
# 1. Parse arguments
# ------------------------------------------------------------

for argument in "$@"; do
  case "$argument" in
    --backend-only) BACKEND_ONLY=true ;;
    --skip-build) SKIP_BUILD=true ;;
    --hystrix-demo) HYSTRIX_DEMO=true ;;
    *)
      echo "未知参数: $argument" >&2
      exit 1
      ;;
  esac
done

mkdir -p "$RUNTIME_DIR"

# ------------------------------------------------------------
# 2. Detect operating system
# ------------------------------------------------------------

OS_TYPE="$(uname -s)"

case "$OS_TYPE" in
  Darwin*)
    OS_PLATFORM="macos"
    ;;
  Linux*)
    OS_PLATFORM="linux"
    ;;
  MINGW*|MSYS*|CYGWIN*)
    OS_PLATFORM="windows"
    ;;
  *)
    echo "不支持的操作系统: $OS_TYPE" >&2
    exit 1
    ;;
esac

echo "[ENV] Platform: $OS_PLATFORM"

# ------------------------------------------------------------
# 3. Basic command checks
# ------------------------------------------------------------

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "缺少命令: $1" >&2
    exit 1
  }
}

require_command java
require_command mvn
require_command mysql

if [ "$BACKEND_ONLY" = false ]; then
  require_command npm
  require_command curl
fi

# ------------------------------------------------------------
# 4. Windows PowerShell helper
# ------------------------------------------------------------

if [ "$OS_PLATFORM" = "windows" ]; then

  require_command powershell.exe
  require_command cygpath

  WIN_PROJECT_ROOT="$(cygpath -w "$PROJECT_ROOT")"
  WIN_CLOUD_ROOT="$(cygpath -w "$CLOUD_ROOT")"
  WIN_RUNTIME_DIR="$(cygpath -w "$RUNTIME_DIR")"

  # Create a native PowerShell process launcher.
  PS_HELPER="$RUNTIME_DIR/windows-launch.ps1"

  cat > "$PS_HELPER" <<'POWERSHELL'
param(
    [Parameter(Mandatory=$true)]
    [string]$Executable,

    [Parameter(Mandatory=$true)]
    [string]$Arguments,

    [Parameter(Mandatory=$true)]
    [string]$WorkingDirectory,

    [Parameter(Mandatory=$true)]
    [string]$LogFile,

    [Parameter(Mandatory=$true)]
    [string]$PidFile
)

$ErrorActionPreference = "Stop"

try {
    $errorFile = "$LogFile.stderr"

    if ((Split-Path $WorkingDirectory -Leaf) -eq 'gouyan-auth') {
        . (Join-Path (Split-Path $PSScriptRoot -Parent) 'scripts/mail-env.ps1')
        Import-GouyanMailEnvironment -RuntimeDirectory $PSScriptRoot
    }

    $process = Start-Process `
        -FilePath $Executable `
        -ArgumentList $Arguments `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $LogFile `
        -RedirectStandardError $errorFile `
        -PassThru `
        -WindowStyle Hidden

    $process.Id | Set-Content -Path $PidFile -Encoding ASCII

    Write-Output $process.Id
}
catch {
    Write-Error $_
    exit 1
}
POWERSHELL

fi

# ------------------------------------------------------------
# 5. Convert paths for PowerShell
# ------------------------------------------------------------

to_windows_path() {
  cygpath -w "$1"
}

# ------------------------------------------------------------
# 6. Check whether a TCP port is listening
# ------------------------------------------------------------

port_is_open() {
  local port="$1"

  case "$OS_PLATFORM" in

    macos)
      lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
      ;;

    linux)
      if command -v lsof >/dev/null 2>&1; then
        lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
      elif command -v ss >/dev/null 2>&1; then
        ss -ltn "sport = :$port" 2>/dev/null | grep -q LISTEN
      else
        echo "Linux 环境缺少 lsof 或 ss" >&2
        return 1
      fi
      ;;

    windows)
      powershell.exe -NoProfile -NonInteractive -Command \
        "if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }" \
        >/dev/null 2>&1
      ;;

  esac
}

# ------------------------------------------------------------
# 7. Get PID listening on a port
# ------------------------------------------------------------

get_port_pid() {
  local port="$1"

  case "$OS_PLATFORM" in

    macos|linux)
      lsof -nP -t -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | head -1
      ;;

    windows)
      powershell.exe -NoProfile -NonInteractive -Command \
        "(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty OwningProcess)" \
        2>/dev/null | tr -d '\r' | head -1
      ;;

  esac
}

# ------------------------------------------------------------
# 8. Wait for service startup
# ------------------------------------------------------------

wait_for_port() {
  local name="$1"
  local port="$2"
  local deadline=$((SECONDS + 180))

  while ! port_is_open "$port"; do

    if (( SECONDS >= deadline )); then
      echo "[FAIL] $name 未在 180 秒内监听端口 $port" >&2
      echo "请查看日志: $RUNTIME_DIR/$name.log 和 $RUNTIME_DIR/$name.log.stderr" >&2
      exit 1
    fi

    sleep 1
  done

  echo "[UP] $name :$port"
}

require_managed_port() {
  local name="$1" port="$2" pid
  pid="$(get_port_pid "$port")"
  if [[ -z "$pid" || ! -f "$RUNTIME_DIR/$name.pid" || "$pid" != "$(cat "$RUNTIME_DIR/$name.pid")" ]]; then
    echo "[FAIL] $name 端口 $port 已占用（PID: ${pid:-未知}）。请核对端口占用后重试。" >&2
    exit 1
  fi
}

# ------------------------------------------------------------
# 9. Launch process on Windows
# ------------------------------------------------------------

windows_start_process() {
  local name="$1"
  local executable="$2"
  local arguments="$3"
  local directory="$4"

  local log_file="$WIN_RUNTIME_DIR\\$name.log"
  local pid_file="$WIN_RUNTIME_DIR\\$name.starter.pid"
  local launcher_log="$RUNTIME_DIR/$name.launch.log"
  local helper_file

  helper_file="$(to_windows_path "$PS_HELPER")"
  directory="$(to_windows_path "$directory")"

  MSYS2_ARG_CONV_EXCL='*' MSYS_NO_PATHCONV=1 powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$helper_file" \
    -Executable "$executable" \
    -Arguments "$arguments" \
    -WorkingDirectory "$directory" \
    -LogFile "$log_file" \
    -PidFile "$pid_file" </dev/null >"$launcher_log" 2>&1 || {
      echo "[FAIL] Windows 后台启动失败: $name" >&2
      cat "$launcher_log" >&2
      exit 1
    }
}

# ------------------------------------------------------------
# 10. Start Java microservice
# ------------------------------------------------------------

start_java() {
  local name="$1"
  local port="$2"
  local module="$3"

  shift 3

  local run_arguments
  local java_pid
  local module_dir
  local mode_file=""
  local mode="normal"

  module_dir="$CLOUD_ROOT/$module"
  if [[ "$name" == bill || "$name" == movie-a ]]; then
    mode_file="$RUNTIME_DIR/$name.mode"
    if [[ "$HYSTRIX_DEMO" == true ]]; then
      mode="hystrix-demo"
    fi
  fi

  if port_is_open "$port"; then
    require_managed_port "$name" "$port"
    if [[ -n "$mode_file" ]] &&
       [[ ! -f "$mode_file" || "$(cat "$mode_file")" != "$mode" ]]; then
      echo "[FAIL] $name 的模式与本次启动不一致或无法确认。先执行 bash scripts/stop-all.sh bill movie-a。" >&2
      exit 1
    fi
    echo "[REUSE] $name :$port 已运行"
    return
  fi

  run_arguments="$*"

  echo "[START] $name :$port"

  case "$OS_PLATFORM" in

    windows)

      local command_arguments

      command_arguments="/c mvn org.springframework.boot:spring-boot-maven-plugin:2.3.2.RELEASE:run \"-Dspring-boot.run.arguments=$run_arguments\""

      windows_start_process \
        "$name" \
        "cmd.exe" \
        "$command_arguments" \
        "$module_dir"

      ;;

    macos|linux)

      (
        cd "$module_dir"

        nohup mvn \
          org.springframework.boot:spring-boot-maven-plugin:2.3.2.RELEASE:run \
          "-Dspring-boot.run.arguments=$run_arguments" \
          >"$RUNTIME_DIR/$name.log" 2>&1 &

        echo $! > "$RUNTIME_DIR/$name.starter.pid"
      )

      ;;

  esac

  wait_for_port "$name" "$port"

  java_pid="$(get_port_pid "$port")"

  if [ -z "$java_pid" ]; then
    echo "[FAIL] 无法获取 $name 的监听 PID" >&2
    exit 1
  fi

  echo "$java_pid" > "$RUNTIME_DIR/$name.pid"
  echo "$port" > "$RUNTIME_DIR/$name.managed"
  if [[ -n "$mode_file" ]]; then
    printf '%s\n' "$mode" > "$mode_file"
  fi

  echo "[PID] $name = $java_pid"
}

# ------------------------------------------------------------
# 11. Start frontend
# ------------------------------------------------------------

start_frontend() {
  local name="$1"
  local port="$2"
  local directory="$3"

  if port_is_open "$port"; then
    require_managed_port "$name" "$port"
    echo "[REUSE] $name :$port 已运行"
    return
  fi

  if [ ! -d "$directory/node_modules" ]; then
    echo "[FAIL] $name 缺少 node_modules" >&2
    echo "请在 $directory 执行 npm ci" >&2
    exit 1
  fi

  echo "[START] $name :$port"

  case "$OS_PLATFORM" in

    windows)

      windows_start_process \
        "$name" \
        "cmd.exe" \
        "/c npm run serve" \
        "$directory"

      ;;

    macos|linux)

      (
        cd "$directory"

        nohup npm run serve \
          >"$RUNTIME_DIR/$name.log" 2>&1 &

        echo $! > "$RUNTIME_DIR/$name.pid"
      )

      ;;

  esac

  wait_for_port "$name" "$port"

  local process_pid
  process_pid="$(get_port_pid "$port")"

  if [ -n "$process_pid" ]; then
    echo "$process_pid" > "$RUNTIME_DIR/$name.pid"
  fi

  echo "$port" > "$RUNTIME_DIR/$name.managed"

  local deadline=$((SECONDS + 180))
  until curl -fsS --connect-timeout 2 --max-time 5 "http://127.0.0.1:$port/" -o /dev/null 2>/dev/null; do
    if (( SECONDS >= deadline )); then
      echo "[FAIL] $name 的 HTTP 页面未就绪，请查看 $RUNTIME_DIR/$name.log 和对应 .stderr 文件。" >&2
      exit 1
    fi
    sleep 2
  done
  echo "[READY] $name HTTP :$port"
}

# ------------------------------------------------------------
# 12. Check MySQL
# ------------------------------------------------------------

echo "[CHECK] MySQL / movie"
if ! MYSQL_PWD="$MYSQL_PASSWORD_VALUE" mysql \
  --connect-timeout=5 --host=127.0.0.1 --port=3306 --user=root --database=movie \
  --execute='SELECT movie_id FROM sys_movie LIMIT 0'; then
  echo "[FAIL] 无法读取 movie.sys_movie。请检查 MySQL 服务、MYSQL_PASSWORD 和初始化状态。" >&2
  exit 1
fi
echo "[PASS] MySQL 已认证，movie.sys_movie 可读"

# ------------------------------------------------------------
# 13. Build backend
# ------------------------------------------------------------

if [ "$SKIP_BUILD" = false ]; then

  echo "[BUILD] Maven 多模块构建"

  (
    cd "$CLOUD_ROOT"
    mvn install -DskipTests
  )

  echo "[PASS] Maven 构建完成"

else
  echo "[SKIP] Maven 构建"
fi

# ------------------------------------------------------------
# 14. Start Eureka
# ------------------------------------------------------------

start_java eureka-peer1 7001 gouyan-eureka \
  --spring.profiles.active=peer1

start_java eureka-peer2 7002 gouyan-eureka \
  --spring.profiles.active=peer2

# ------------------------------------------------------------
# 15. Start business services
# ------------------------------------------------------------

start_java actor 9000 gouyan-actor

start_java auth 9002 gouyan-auth

if [ "$HYSTRIX_DEMO" = true ]; then

  start_java bill 9004 gouyan-bill \
    --spring.profiles.active=druid,hystrix-demo

else

  start_java bill 9004 gouyan-bill

fi

start_java cinema 9006 gouyan-cinema

start_java hall 9009 gouyan-hall

if [ "$HYSTRIX_DEMO" = true ]; then

  start_java movie-a 9010 gouyan-movie \
    --demo.movie.delay-ms=1500

else

  start_java movie-a 9010 gouyan-movie

fi

start_java movie-b 9011 gouyan-movie \
  --server.port=9011 \
  --eureka.instance.instance-id=gouyan-movie:9011

start_java admin 8183 gouyan-admin

# ------------------------------------------------------------
# 16. Start Gateway
# ------------------------------------------------------------

start_java gateway 8181 gouyan-gateway

# ------------------------------------------------------------
# 17. Start frontend
# ------------------------------------------------------------

if [ "$BACKEND_ONLY" = false ]; then

  start_frontend user-web 8080 \
    "$PROJECT_ROOT/gouyan-web/gouyan-user"

  start_frontend admin-web 8888 \
    "$PROJECT_ROOT/gouyan-web/gouyan-ui"

  echo ""
  echo "启动完成。"
  echo "用户前台: http://127.0.0.1:8080"
  echo "管理后台: http://127.0.0.1:8888"

else

  echo ""
  echo "后端服务启动完成。"

fi

echo ""
echo "API 网关: http://127.0.0.1:8181"
echo "（仅供接口调用，根路径无页面）"
echo ""
echo "运行状态: bash scripts/status.sh"
echo "集成测试: bash scripts/smoke-test.sh"
echo "停止服务: bash scripts/stop-all.sh"
