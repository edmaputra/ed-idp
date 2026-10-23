#!/usr/bin/env bash
#
# Helper script to run both ed-idp Authorization Server (port 9000)
# and ed-idp Account Portal (port 9001) concurrently for local development.
#
# Usage:
#   ./run-local.sh
#
# To stop:
#   Type 'q', 'quit', 'stop', or press Ctrl+C.
#

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Color constants
CYAN='\033[0;36m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

is_port_in_use() {
  local port=$1
  if command -v lsof >/dev/null 2>&1; then
    lsof -i :"$port" -sTCP:LISTEN >/dev/null 2>&1
  elif command -v nc >/dev/null 2>&1; then
    nc -z 127.0.0.1 "$port" >/dev/null 2>&1
  else
    (echo > /dev/tcp/127.0.0.1/"$port") >/dev/null 2>&1
  fi
}

# Pre-flight port check
if is_port_in_use 9000; then
  echo -e "${RED}[ERROR] Port 9000 is already in use.${NC} Please stop the service on 9000 before running."
  if command -v lsof >/dev/null 2>&1; then
    echo "Process holding port 9000:"
    lsof -i :9000
  fi
  exit 1
fi

if is_port_in_use 9001; then
  echo -e "${RED}[ERROR] Port 9001 is already in use.${NC} Please stop the service on 9001 before running."
  if command -v lsof >/dev/null 2>&1; then
    echo "Process holding port 9001:"
    lsof -i :9001
  fi
  exit 1
fi

IDP_PID=""
PORTAL_PID=""

cleanup() {
  echo -e "\n${YELLOW}===================================================================${NC}"
  echo -e "${YELLOW}Stopping all running services...${NC}"

  # Terminate processes and their sub-processes (Maven -> Java)
  if [ -n "$IDP_PID" ] && kill -0 "$IDP_PID" 2>/dev/null; then
    pkill -P "$IDP_PID" 2>/dev/null || true
    kill "$IDP_PID" 2>/dev/null || true
  fi

  if [ -n "$PORTAL_PID" ] && kill -0 "$PORTAL_PID" 2>/dev/null; then
    pkill -P "$PORTAL_PID" 2>/dev/null || true
    kill "$PORTAL_PID" 2>/dev/null || true
  fi

  # Grace period for clean termination
  sleep 1

  # Force kill any remaining processes holding ports 9000 or 9001
  for port in 9000 9001; do
    if command -v lsof >/dev/null 2>&1; then
      local remaining_pids
      remaining_pids=$(lsof -ti :"$port" 2>/dev/null || true)
      if [ -n "$remaining_pids" ]; then
        echo -e "${YELLOW}Releasing port $port (PIDs: $remaining_pids)...${NC}"
        kill -9 $remaining_pids 2>/dev/null || true
      fi
    fi
  done

  echo -e "${GREEN}All services successfully stopped.${NC}"
  echo -e "${YELLOW}===================================================================${NC}"
}

trap cleanup EXIT INT TERM

echo -e "${CYAN}===================================================================${NC}"
echo -e "${CYAN} ed-idp Local Development Environment${NC}"
echo -e "${CYAN}===================================================================${NC}"
echo -e "Starting services:"
echo -e "  [1] ${BLUE}Identity Provider & Admin UI${NC}  -> http://localhost:9000"
echo -e "  [2] ${GREEN}User Account Portal Client${NC}    -> http://localhost:9001"
echo -e ""
echo -e "${YELLOW}Commands: Type 'q', 'quit', 'stop', or press [Ctrl+C] to exit.${NC}"
echo -e "${CYAN}===================================================================${NC}"
echo ""

# Launch ed-idp-ui (Authorization Server + Admin Console) on port 9000
./mvnw spring-boot:run -pl ed-idp-ui > >(sed -e $'s/^/\033[0;34m[idp]   \033[0m /') 2>&1 &
IDP_PID=$!

# Launch ed-idp-account-portal (OAuth 2.1 Client) on port 9001
./mvnw spring-boot:run -pl ed-idp-account-portal > >(sed -e $'s/^/\033[0;32m[portal]\033[0m /') 2>&1 &
PORTAL_PID=$!

# Interactive input loop to stop services
while true; do
  read -r input || break
  # Convert input to lowercase
  cmd=$(echo "$input" | tr '[:upper:]' '[:lower:]' | xargs)
  case "$cmd" in
    q|quit|stop|exit)
      echo -e "${YELLOW}Stop command received: '$cmd'${NC}"
      break
      ;;
    "")
      # Ignore empty enters from user looking at logs
      ;;
    help)
      echo -e "${CYAN}Available commands: 'q', 'quit', 'stop', 'exit' (or press Ctrl+C)${NC}"
      ;;
    *)
      echo -e "${YELLOW}Unknown command: '$input'. Type 'q' or 'stop' to shut down.${NC}"
      ;;
  esac
done
