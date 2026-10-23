#!/usr/bin/env bash
# Stop one side (legacy|migrated) or both (no argument).
set -uo pipefail
modes=("${1:-legacy}"); [[ $# -eq 0 ]] && modes=(legacy migrated)
for m in "${modes[@]}"; do
  . "$(dirname "$0")/env.sh" "$m"
  for svc in bank credit mock broker; do
    pidf="$STATE_DIR/$svc.pid"
    [[ -f "$pidf" ]] || continue
    pid=$(cat "$pidf")
    if kill -0 "$pid" 2>/dev/null; then
      pkill -TERM -P "$pid" 2>/dev/null; kill "$pid" 2>/dev/null
      for _ in $(seq 1 20); do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
      kill -9 "$pid" 2>/dev/null
    fi
    rm -f "$pidf"; echo "[parity:$m] stopped $svc"
  done
done
