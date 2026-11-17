#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Cinema Ticketing Microservices
# Cross-platform service shutdown
# Supports:
#   - macOS
#   - Linux
#   - Windows (Git Bash / Cygwin / MSYS)
# ============================================================

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUNTIME_DIR="$PROJECT_ROOT/.runtime"

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
    echo "不支持的操作系统: $OS_TYPE" >&2
    exit 1
    ;;
esac

echo "[ENV] Platform: $OS_PLATFORM"

# ------------------------------------------------------------
# 2. Check runtime directory
# ------------------------------------------------------------

if [ ! -d "$RUNTIME_DIR" ]; then
  echo "没有由启动脚本创建的运行记录。"
  exit 0
fi

# ------------------------------------------------------------
# 3. Windows PowerShell helper
# ------------------------------------------------------------

if [ "$OS_PLATFORM" = "windows" ]; then

  command -v powershell.exe >/dev/null 2>&1 || {
    echo "缺少 powershell.exe" >&2
    exit 1
  }

  command -v cygpath >/dev/null 2>&1 || {
    echo "缺少 cygpath" >&2
    exit 1
  }

  WIN_RUNTIME_DIR="$(cygpath -w "$RUNTIME_DIR")"

  # Stop a process only when it matches the recorded PID
  # and its executable command line belongs to this project.
  stop_windows_pid() {
    local pid="$1"
    local expected_name="${2:-}"

    powershell.exe -NoProfile -NonInteractive -Command '
      param($ProcessId, $ExpectedName, $ProjectPath)

      $ErrorActionPreference = "Stop"

      $process = Get-CimInstance Win32_Process `
        -Filter "ProcessId = $ProcessId" `
        -ErrorAction SilentlyContinue

      if ($null -eq $process) {
        exit 0
      }

      $commandLine = [string]$process.CommandLine
      $executable = [string]$process.ExecutablePath

      $isProjectProcess = (
        $commandLine -like "*$ProjectPath*"
      )

      if (-not $isProjectProcess) {
        Write-Output "SKIP: PID $ProcessId 不属于当前项目"
        exit 2
      }

      if ($ExpectedName -and
          $commandLine -notlike "*$ExpectedName*") {
        Write-Output "SKIP: PID $ProcessId 与预期服务不匹配"
        exit 2
      }

      try {
        Stop-Process -Id $ProcessId -Force -ErrorAction Stop
        Write-Output "STOPPED: PID $ProcessId"
      }
      catch {
        Write-Output "FAILED: PID $ProcessId : $_"
        exit 1
      }
    ' -Args "$pid" "$expected_name" "$WIN_RUNTIME_DIR" 2>/dev/null
  }

  # Get PID listening on a port
  get_windows_port_pid() {
    local port="$1"

    powershell.exe -NoProfile -NonInteractive -Command \
      "(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty OwningProcess)" \
      2>/dev/null |
      tr -d '\r' |
      head -1
  }

fi

# ------------------------------------------------------------
# 4. Stop a recorded PID
# ------------------------------------------------------------

stop_recorded_pid() {
  local pid="$1"
  local name="$2"

  if [ -z "$pid" ]; then
    return 0
  fi

  case "$OS_PLATFORM" in

    windows)

      if stop_windows_pid "$pid" "$name"; then
        echo "[STOP] $name PID=$pid"
        return 0
      else
        echo "[SKIP] $name PID=$pid 未确认归属或停止失败"
        return 1
      fi
      ;;

    macos|linux)

      if ! kill -0 "$pid" 2>/dev/null; then
        return 0
      fi

      # Verify the process belongs to this project.
      local process_info

      process_info="$(ps -p "$pid" -o command= 2>/dev/null || true)"

      if [[ "$process_info" != *"$PROJECT_ROOT"* ]]; then
        echo "[SKIP] PID=$pid 不属于当前项目"
        return 1
      fi

      kill "$pid" 2>/dev/null || return 1

      for attempt in 1 2 3 4 5; do
        kill -0 "$pid" 2>/dev/null || break
        sleep 1
      done

      if kill -0 "$pid" 2>/dev/null; then
        echo "[WARN] PID=$pid 未正常退出"
        return 1
      fi

      echo "[STOP] $name PID=$pid"
      ;;

  esac
}

# ------------------------------------------------------------
# 5. Stop primary recorded PIDs
# ------------------------------------------------------------

shopt -s nullglob

failed=false

for pid_file in "$RUNTIME_DIR"/*.pid; do

  case "$pid_file" in
    *.starter.pid)
      continue
      ;;
  esac

  [ -f "$pid_file" ] || continue

  name="$(basename "$pid_file" .pid)"
  pid="$(cat "$pid_file")"

  if stop_recorded_pid "$pid" "$name"; then
    rm -f "$pid_file"
  else
    failed=true
  fi

done

# ------------------------------------------------------------
# 6. Stop Maven / npm starter processes
# ------------------------------------------------------------

for pid_file in "$RUNTIME_DIR"/*.starter.pid; do

  [ -f "$pid_file" ] || continue

  name="$(basename "$pid_file" .starter.pid)"
  pid="$(cat "$pid_file")"

  if stop_recorded_pid "$pid" "$name"; then
    rm -f "$pid_file"
  else
    failed=true
  fi

done

# ------------------------------------------------------------
# 7. Stop only explicitly managed ports
# ------------------------------------------------------------

for managed_file in "$RUNTIME_DIR"/*.managed; do

  [ -f "$managed_file" ] || continue

  name="$(basename "$managed_file" .managed)"
  port="$(cat "$managed_file")"

  case "$OS_PLATFORM" in

    windows)

      pid="$(get_windows_port_pid "$port" || true)"

      if [ -n "$pid" ]; then

        if stop_windows_pid "$pid" "$name"; then
          echo "[STOP] $name port=$port PID=$pid"
        else
          echo "[SKIP] $name port=$port PID=$pid 未确认归属"
          failed=true
          continue
        fi

      else
        echo "[INFO] $name port=$port 已停止"
      fi
      ;;

    macos|linux)

      if command -v lsof >/dev/null 2>&1; then
        pid="$(lsof -nP -t -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | head -1 || true)"
      elif command -v ss >/dev/null 2>&1; then
        pid="$(ss -ltnp "sport = :$port" 2>/dev/null |
          sed -n 's/.*pid=\([0-9]*\).*/\1/p' |
          head -1 || true)"
      else
        echo "[WARN] 无法查询端口 $port 的 PID"
        failed=true
        continue
      fi

      if [ -n "$pid" ]; then
        if stop_recorded_pid "$pid" "$name"; then
          echo "[STOP] $name port=$port PID=$pid"
        else
          failed=true
          continue
        fi
      else
        echo "[INFO] $name port=$port 已停止"
      fi
      ;;

  esac

  rm -f "$managed_file"

done

# ------------------------------------------------------------
# 8. Final result
# ------------------------------------------------------------

echo ""

if [ "$failed" = true ]; then
  echo "[WARN] 部分进程未能安全停止。"
  echo "未确认归属或停止失败的运行记录已保留，请检查 .runtime。"
  exit 1
fi

echo "[SUCCESS] 已发送停止信号。"
echo "没有停止启动前已存在或无法确认属于当前项目的进程。"
