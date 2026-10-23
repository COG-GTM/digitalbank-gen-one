#!/usr/bin/env bash
# Run the Digital Bank estate (broker + credit + bank) with Docker Compose.
#   ./digitalbank.sh up [h2|mysql|postgres|mssql] [--parity]   build images and start (default: h2)
#   ./digitalbank.sh down [h2|mysql|postgres|mssql] [--parity] stop and remove containers + volumes
#   ./digitalbank.sh ps|logs [db] [--parity]                    status / follow logs
# --parity adds the recorded external-service fixture mock (tools/parity/mock_external.py) on
# 127.0.0.1:8099 and points the bank at it, so tools/parity/replay.py can run against the stack.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cmd="${1:-up}"; shift || true
db="h2"; parity=0
for arg in "$@"; do
  case "$arg" in
    h2|mysql|postgres|mssql) db="$arg" ;;
    --parity) parity=1 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

files=(-f "$ROOT/docker-compose.yml")
[ "$db" != "h2" ] && files+=(-f "$ROOT/deploy/docker-compose/docker-compose-$db.yml")
[ "$parity" = 1 ] && files+=(-f "$ROOT/deploy/docker-compose/docker-compose-parity.yml")
compose=(docker compose --project-directory "$ROOT" "${files[@]}")

wait_healthy() {
  local deadline=$((SECONDS + ${STARTUP_TIMEOUT:-600}))
  for svc in broker credit bank; do
    until [ "$(docker inspect -f '{{.State.Health.Status}}' "$svc" 2>/dev/null)" = "healthy" ]; do
      if [ $SECONDS -ge $deadline ]; then
        echo "timed out waiting for $svc to become healthy" >&2
        "${compose[@]}" ps >&2
        exit 1
      fi
      sleep 3
    done
    echo "$svc healthy"
  done
}

case "$cmd" in
  up)
    "${compose[@]}" up -d --build
    wait_healthy
    cat <<MSG
Digital Bank estate is up ($db):
  Bank   https://localhost:8443/bank    (jsmith@demo.io / Demo123!)
  Credit https://localhost:3500/credit
  Broker tcp://localhost:61616, console http://localhost:8161 (admin / admin)
MSG
    ;;
  down) "${compose[@]}" down -v --remove-orphans ;;
  ps)   "${compose[@]}" ps ;;
  logs) "${compose[@]}" logs -f ;;
  *) echo "usage: $0 up|down|ps|logs [h2|mysql|postgres|mssql] [--parity]" >&2; exit 2 ;;
esac
