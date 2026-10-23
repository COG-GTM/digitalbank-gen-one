#!/usr/bin/env bash
# One-shot parity run: fresh legacy stack -> replay -> fresh migrated stack -> replay -> diff. Artefacts in $OUT.
# Exit: 0 parity, 1 differences, 2 missing steps, 3 a stack failed to start, 4 a replay aborted.
set -uo pipefail
P="$(cd "$(dirname "$0")" && pwd)"; OUT="${OUT:-/tmp/parity}"; mkdir -p "$OUT"
. "$P/env.sh" legacy;   LB=$BANK_URL; LC=$CREDIT_URL; LM="http://127.0.0.1:$MOCK_PORT"
. "$P/env.sh" migrated; MB=$BANK_URL; MC=$CREDIT_URL; MM="http://127.0.0.1:$MOCK_PORT"
"$P/start_stack.sh" legacy || { "$P/stop_stack.sh" legacy; exit 3; }
python3 "$P/replay.py" --label legacy --bank "$LB" --credit "$LC" --mock "$LM" --out "$OUT/legacy.json"; RL=$?
"$P/stop_stack.sh" legacy
"$P/start_stack.sh" migrated ${BUILD_MIGRATED:+--build} || { "$P/stop_stack.sh" migrated; exit 3; }
python3 "$P/replay.py" --label migrated --bank "$MB" --credit "$MC" --mock "$MM" --out "$OUT/migrated.json"; RM=$?
"$P/stop_stack.sh" migrated
python3 "$P/diff.py" "$OUT/legacy.json" "$OUT/migrated.json" --out "$OUT/diff.json"; RD=$?
(( RL == 0 && RM == 0 )) || { echo "replay aborted (legacy=$RL migrated=$RM)" >&2; exit 4; }
exit $RD
