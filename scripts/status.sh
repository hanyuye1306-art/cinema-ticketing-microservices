#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Cinema Ticketing Microservices
# Cross-platform service status checker
# Supports macOS / Windows (Git Bash, Cygwin, MSYS) / Linux
# ============================================================

# ------------------------------------------------------------
# 1. Detect operating system
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
    echo "Unsupported operating system: $OS_TYPE" >&2
    exit 1
    ;;
esac

echo "[ENV] Platform: $OS_PLATFORM"
echo ""

# ------------------------------------------------------------
# 2. Check required commands
# ------------------------------------------------------------

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "缺少命令: $1" >&2
    exit 1
  }
}

if [ "$OS_PLATFORM" = "windows" ]; then
  require_command powershell.exe
else
  if ! command -v lsof >/dev/null 2>&1 &&
     ! command -v ss >/dev/null 2>&1; then
    echo "缺少端口查询工具: lsof 或 ss" >&2
    exit 1
  fi
fi

# ------------------------------------------------------------
# 3. Check port
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
      else
        ss -ltn "sport = :$port" 2>/dev/null |
          grep -q LISTEN
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
# 4. Get PID listening on port
# ------------------------------------------------------------

get_port_pid() {
  local port="$1"

  case "$OS_PLATFORM" in

    macos|linux)
      if command -v lsof >/dev/null 2>&1; then
        lsof -nP -t -iTCP:"$port" -sTCP:LISTEN 2>/dev/null |
          head -1
      else
        ss -ltnp "sport = :$port" 2>/dev/null |
          sed -n 's/.*pid=\([0-9]*\).*/\1/p' |
          head -1
      fi
      ;;

    windows)
      powershell.exe -NoProfile -NonInteractive -Command \
        "(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty OwningProcess)" \
        2>/dev/null |
        tr -d '\r' |
        head -1
      ;;

  esac
}

# ------------------------------------------------------------
# 5. Print service status
# ------------------------------------------------------------

check_port() {
  local name="$1"
  local port="$2"
  local status
  local pid="-"

  if port_is_open "$port"; then
    status="UP"
    pid="$(get_port_pid "$port" || true)"
    [ -n "$pid" ] || pid="-"
  else
    status="DOWN"
  fi

  printf '%-16s %-6s %-8s %s\n' \
    "$name" "$port" "$status" "$pid"
}

# ------------------------------------------------------------
# 6. Print all service statuses
# ------------------------------------------------------------

printf '%-16s %-6s %-8s %s\n' \
  SERVICE PORT STATUS PID

printf '%s\n' \
  "---------------------------------------------"

check_port eureka-peer1 7001
check_port eureka-peer2 7002

check_port actor 9000
check_port auth 9002
check_port bill 9004
check_port cinema 9006
check_port hall 9009

check_port movie-a 9010
check_port movie-b 9011

check_port admin 8183
check_port gateway 8181

check_port user-web 8080
check_port admin-web 8888

echo ""
echo "状态检查完成。"
