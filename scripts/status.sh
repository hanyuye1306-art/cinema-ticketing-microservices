#!/usr/bin/env bash
set -euo pipefail

check_port() {
  local name="$1" port="$2"
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    printf '%-16s %-6s %s\n' "$name" "$port" UP
  else
    printf '%-16s %-6s %s\n' "$name" "$port" DOWN
  fi
}

printf '%-16s %-6s %s\n' SERVICE PORT STATUS
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
