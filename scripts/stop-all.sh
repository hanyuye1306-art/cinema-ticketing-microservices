#!/usr/bin/env bash
set -euo pipefail

# Cinema Ticketing Microservices - safe cross-platform shutdown
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
RUNTIME_DIR="$PROJECT_ROOT/.runtime"
SERVICES=(eureka-peer1 eureka-peer2 actor auth bill cinema hall movie-a movie-b admin gateway user-web admin-web)
PORTS=(7001 7002 9000 9002 9004 9006 9009 9010 9011 8183 8181 8080 8888)
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) OS_PLATFORM=windows ;;
  Darwin*) OS_PLATFORM=macos ;;
  Linux*) OS_PLATFORM=linux ;;
  *) echo '[ERROR] Unsupported platform' >&2; exit 1 ;;
esac
echo "[ENV] Platform: $OS_PLATFORM"

if [[ "$OS_PLATFORM" == windows ]]; then
  command -v powershell.exe >/dev/null
  command -v cygpath >/dev/null
  export STOP_PROJECT_ROOT="$(cygpath -w "$PROJECT_ROOT")"
  # Use environment variables rather than powershell -Command ... -Args.
  # Disable MSYS argument rewriting for native PowerShell.
  PS_TEMP="$(mktemp "${TMPDIR:-/tmp}/gouyan-stop.XXXXXXXX")"
  # Windows PowerShell -File requires a .ps1 extension.
  mv -- "$PS_TEMP" "$PS_TEMP.ps1"
  PS_TEMP="$PS_TEMP.ps1"
  trap 'rm -f -- "$PS_TEMP"' EXIT
  cat > "$PS_TEMP" <<'POWERSHELL_SCRIPT'
    $ErrorActionPreference = "Stop"
    $root = [IO.Path]::GetFullPath($env:STOP_PROJECT_ROOT).TrimEnd([char]92)
    $runtime = Join-Path $root ".runtime"
    $services = [ordered]@{"eureka-peer1"=7001;"eureka-peer2"=7002;"actor"=9000;"auth"=9002;"bill"=9004;"cinema"=9006;"hall"=9009;"movie-a"=9010;"movie-b"=9011;"admin"=8183;"gateway"=8181;"user-web"=8080;"admin-web"=8888}
    function Get-Owners($port) {
      @(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object {$_.LocalPort -eq $port} | Select-Object -ExpandProperty OwningProcess -Unique)
    }
    function Get-Proc($id) {
      if (-not $id -or $id -eq 0) { return $null }
      Get-CimInstance Win32_Process -Filter "ProcessId = $id" -ErrorAction Stop
    }
    function Is-Project($proc) {
      if ($null -eq $proc) { return $false }
      $line = ([string]$proc.CommandLine).Replace([char]47,[char]92)
      if ($proc.Name -notmatch "^(javaw?|node|cmd|bash|sh|npm|pnpm)\.exe$") { return $false }
      $pattern = [regex]::Escape($root) + "(?=\\|[\s;" + [char]34 + "]|$)"
      return ($line -match $pattern)
    }
    function Is-Service($proc, $name) {
      if (-not (Is-Project $proc)) { return $false }
      $line = ([string]$proc.CommandLine).Replace([char]47,[char]92)
      $module = switch -Regex ($name) {
        '^eureka-' { 'gouyan-cloud\gouyan-eureka'; break }
        '^movie-' { 'gouyan-cloud\gouyan-movie'; break }
        '^user-web$' { 'gouyan-web\gouyan-user'; break }
        '^admin-web$' { 'gouyan-web\gouyan-admin'; break }
        default { 'gouyan-cloud\gouyan-' + $name }
      }
      $expected = [regex]::Escape($root + '\' + $module) + '(?=\\|[\s;" ]|$)'
      return ($line -match $expected)
    }
    # Discover launchers from verified live service processes, even if runtime records are missing.
    $launchers = @{}
    foreach ($service in $services.Keys) {
      foreach ($owner in @(Get-Owners $services[$service])) {
        $child = Get-Proc $owner
        if (-not (Is-Service $child $service)) { continue }
        for ($depth=0; $depth -lt 8; $depth++) {
          $parent = Get-Proc $child.ParentProcessId
          if ($null -eq $parent -or $parent.CreationDate -gt $child.CreationDate) { break }
          $line = [string]$parent.CommandLine
          if (-not (Is-Project $parent) -or $parent.Name -notmatch "^(javaw?|node|cmd)\.exe$" -or $line -notmatch "(?i)(spring-boot-maven-plugin|spring-boot:run|org\.codehaus\.plexus\.classworlds|npm(?:\.cmd)?\s+run\s+serve|npm-cli\.js|vue-cli-service)") { break }
          $launchers[[string]$parent.ProcessId] = $parent
          $child = $parent
        }
      }
    }
    $failed = $false
    foreach ($entry in @($launchers.Values | Sort-Object CreationDate)) {
      $id = $entry.ProcessId
      $current = Get-Proc $id
      if ($null -eq $current) { continue }
      if ($current.CreationDate -ne $entry.CreationDate) { continue }
      try {
        Stop-Process -Id $id -Force -ErrorAction Stop
        Write-Output "[STOP] Verified launcher PID=$id"
      } catch { if (Get-Proc $id) { Write-Output "[WARN] Launcher PID=$id : $_"; $failed=$true } }
    }
    # Stop verified launchers first to avoid a child being restarted.
    foreach ($name in $services.Keys) {
      $record = Join-Path $runtime ($name + ".starter.pid")
      if (-not (Test-Path -LiteralPath $record)) { continue }
      $raw = (Get-Content -LiteralPath $record -Raw).Trim()
      if ($raw -notmatch "^[1-9][0-9]*$") { Write-Output "[WARN] Invalid record: $record"; $failed=$true; continue }
      $proc = Get-Proc $raw
      if ($null -eq $proc) { Remove-Item -LiteralPath $record; continue }
      if (-not (Is-Service $proc $name) -or ([string]$proc.CommandLine) -notmatch "(?i)(spring-boot-maven-plugin|spring-boot:run|org\.codehaus\.plexus\.classworlds|npm-cli\.js|vue-cli-service|npm(?:\.cmd)?\s+run\s+serve)") { Write-Output "[SKIP] Unverified starter $name PID=$raw (record retained)"; $failed=$true; continue }
      try {
        Stop-Process -Id $raw -Force -ErrorAction Stop
        Wait-Process -Id $raw -Timeout 10 -ErrorAction SilentlyContinue
        if (Get-Proc $raw) { throw "Process still exists" }
        Remove-Item -LiteralPath $record
        Write-Output "[STOP] Starter $name PID=$raw"
      } catch { Write-Output "[WARN] Starter $name : $_"; $failed=$true }
    }
    foreach ($name in $services.Keys) {
      $port = $services[$name]
      foreach ($id in @(Get-Owners $port)) {
        $proc = Get-Proc $id
        if ($null -eq $proc) { continue }
        if (-not (Is-Service $proc $name)) { Write-Output "[SKIP] $name port=$port PID=$id ownership not verified"; $failed=$true; continue }
        try {
          # Recheck identity and port ownership before termination.
          $current = Get-Proc $id
          if ($null -eq $current) { continue }
          if ($current.CreationDate -ne $proc.CreationDate -or -not (Is-Service $current $name) -or $id -notin @(Get-Owners $port)) { throw "Process identity changed" }
          Stop-Process -Id $id -Force -ErrorAction Stop
          Write-Output "[STOP] $name port=$port PID=$id"
        } catch { Write-Output "[WARN] $name : $_"; $failed=$true }
      }
    }
    for ($i=0; $i -lt 10; $i++) {
      $remaining = @(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object {$_.LocalPort -in @($services.Values)})
      if ($remaining.Count -eq 0) { break }
      Start-Sleep -Seconds 1
    }
    foreach ($name in $services.Keys) {
      $port = $services[$name]
      $owners = @(Get-Owners $port)
      if ($owners.Count -gt 0) { Write-Output "[WARN] $name port=$port still listening PID=$owners"; $failed=$true; continue }
      Write-Output "[FREE] $name port=$port"
      $record = Join-Path $runtime ($name + ".pid")
      if (Test-Path -LiteralPath $record) {
        $raw = (Get-Content -LiteralPath $record -Raw).Trim()
        if ($raw -match "^[1-9][0-9]*$" -and $null -eq (Get-Proc $raw)) { Remove-Item -LiteralPath $record }
        else { Write-Output "[WARN] Unresolved PID record: $record"; $failed=$true }
      }
      $record = Join-Path $runtime ($name + ".managed")
      if (Test-Path -LiteralPath $record) { Remove-Item -LiteralPath $record }
    }
    if ($failed) { Write-Output "[WARN] Shutdown incomplete; inspect remaining processes and records."; exit 1 }
    Write-Output "[SUCCESS] All configured service ports are free."
    exit 0
POWERSHELL_SCRIPT
  WIN_PS_TEMP="$(cygpath -w "$PS_TEMP")"
  if MSYS2_ARG_CONV_EXCL='*' MSYS_NO_PATHCONV=1 powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$WIN_PS_TEMP"; then
    exit 0
  else
    exit $?
  fi
fi

# macOS / Linux: verify process ownership by absolute command path or cwd.
command -v lsof >/dev/null || { echo '[ERROR] lsof is required' >&2; exit 1; }
failed=0
get_owners() {
  local out rc=0
  out="$(lsof -nP -t -iTCP:"$1" -sTCP:LISTEN)" || rc=$?
  if (( rc > 1 )); then return "$rc"; fi
  if [[ -n "$out" ]]; then printf '%s\n' "$out" | sort -u; fi
}
is_project() {
  local pid="$1" cmd cwd
  [[ "$pid" =~ ^[1-9][0-9]*$ ]] || return 1
  cmd="$(ps -ww -p "$pid" -o command= 2>/dev/null)" || return 1
  cwd="$(lsof -a -p "$pid" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p' | head -1)" || true
  [[ "$cmd" == *"$PROJECT_ROOT/"* || "$cwd" == "$PROJECT_ROOT" || "$cwd" == "$PROJECT_ROOT/"* ]]
}
stop_pid() {
  local pid="$1" name="$2" i
  [[ "$pid" =~ ^[1-9][0-9]*$ ]] || return 1
  if ! ps -p "$pid" -o pid= >/dev/null 2>&1; then return 0; fi
  if ! is_project "$pid"; then echo "[SKIP] $name PID=$pid ownership not verified"; return 1; fi
  kill -TERM "$pid" 2>/dev/null || return 1
  for i in {1..10}; do
    if ! kill -0 "$pid" 2>/dev/null; then echo "[STOP] $name PID=$pid"; return 0; fi
    sleep 1
  done
  echo "[WARN] $name PID=$pid did not exit"; return 1
}
for name in "${SERVICES[@]}"; do
  file="$RUNTIME_DIR/$name.starter.pid"
  if [[ -f "$file" ]]; then
    if stop_pid "$(cat "$file")" "$name starter"; then rm -f "$file"; else failed=1; fi
  fi
done
for i in "${!SERVICES[@]}"; do
  name="${SERVICES[$i]}"; port="${PORTS[$i]}"
  if ! owners="$(get_owners "$port")"; then echo "[WARN] Cannot inspect port $port"; failed=1; continue; fi
  for pid in $owners; do stop_pid "$pid" "$name" || failed=1; done
done
for i in "${!SERVICES[@]}"; do
  name="${SERVICES[$i]}"; port="${PORTS[$i]}"
  if ! owners="$(get_owners "$port")"; then failed=1; continue; fi
  if [[ -n "$owners" ]]; then echo "[WARN] $name port=$port still listening: $owners"; failed=1; continue; fi
  echo "[FREE] $name port=$port"
  file="$RUNTIME_DIR/$name.pid"
  if [[ -f "$file" ]]; then
    pid="$(cat "$file")"
    if [[ "$pid" =~ ^[1-9][0-9]*$ ]] && ! ps -p "$pid" -o pid= >/dev/null 2>&1; then rm -f "$file"; else echo "[WARN] Unresolved PID record: $file"; failed=1; fi
  fi
  rm -f "$RUNTIME_DIR/$name.managed"
done
if (( failed )); then echo '[WARN] Shutdown incomplete.'; exit 1; fi
echo '[SUCCESS] All configured service ports are free.'
