#!/bin/bash
# ------------------------------------------------------------------------
# test-first-run.sh -- a new install, started the way run.sh starts it.
#
# A new install has no Master_AdultHistory.csv, and no release ships one (it
# would hold participant data). run.sh, run.bat and RUNNING.md all pass
# `-a Master_AdultHistory.csv`, so the program must start a new, empty one
# rather than refuse (SPEC.md D-9). A start that fails for any other reason
# must end the process with a non-zero exit code.
#
#   ./mvnw package && bash scripts/test-first-run.sh
#
# Synthetic people only, in a throwaway folder on a spare port. Kept apart
# from test-board-event.sh, which the Windows version runs against its own
# server and so cannot start this jar a second time.
# ------------------------------------------------------------------------

set -u

PORT="${EB_PORT:-18097}"
B="http://127.0.0.1:$PORT"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK="${TMPDIR:-/tmp}/eb-first-run-$$"
SRV=""

PASS=0
FAIL=0

ok()  { echo "  ok  : $*"; PASS=$((PASS + 1)); }
bad() { echo "FAIL  : $*"; FAIL=$((FAIL + 1)); }

# chk <what> <got> <want>
chk() {
    if [ "$2" = "$3" ]; then
        ok "$1"
    else
        bad "$1"
        echo "          got:  '$2'"
        echo "          want: '$3'"
    fi
}

stop() {
    if [ -n "$SRV" ]; then
        kill "$SRV" 2>/dev/null
        wait "$SRV" 2>/dev/null
        SRV=""
    fi
}

cleanup() {
    stop
    rm -rf "$WORK"
}
trap cleanup EXIT INT TERM

for t in curl java; do
    command -v "$t" >/dev/null 2>&1 || { echo "MISSING: $t"; exit 1; }
done

JAR=""
for j in "$ROOT"/target/eagleboardscheduler-*.jar; do
    case "$j" in
        *original-*|*-shaded.jar) continue ;;
    esac
    [ -f "$j" ] && JAR="$j"
done
if [ -z "$JAR" ]; then
    echo "MISSING: no built jar in target/. Run './mvnw package' first."
    exit 1
fi
echo "testing: ${JAR#"$ROOT"/}"

mkdir -p "$WORK"
cp "$ROOT/config.properties" "$WORK/config.properties"

# start <log> -- run.sh's options, without the window, in $WORK.
start() {
    ( cd "$WORK" && exec java -jar "$JAR" \
        -a Master_AdultHistory.csv -c config.properties -port "$PORT" -d 2026-09-27 ) > "$WORK/$1" 2>&1 &
    SRV=$!
    if ! curl -sf --retry 40 --retry-delay 1 --retry-connrefused --retry-all-errors \
         -o /dev/null "$B/index.html"; then
        bad "the server never became ready"
        cat "$WORK/$1"
        exit 1
    fi
}

echo
echo "== a new install: no adult history yet =="
start first.log
ok "it starts without Master_AdultHistory.csv"
# One line, the adult columns in the order every version writes them.
chk "and makes one holding only the header row" \
    "$(wc -l < "$WORK/Master_AdultHistory.csv" 2>/dev/null | tr -d ' ')|$(cut -d, -f1-6 "$WORK/Master_AdultHistory.csv" 2>/dev/null)" \
    "1|Type,ID,Last,First,Email,Phone"
chk "and says where, in full" \
    "$(grep -c "Started a new, empty adult history: .*Master_AdultHistory.csv" "$WORK/first.log")" "1"

curl -s -o /dev/null --data "Last=Firstrun&First=Frances&Email=frances@example.org&Phone=555-0110&UnitType=Troop&Unit=4410&ProjectReview=Member&FinalBoard=Member" \
     "$B/register-adult"
chk "an adult who signs in is kept in it" "$(grep -c '^ADULT,.*,Firstrun,Frances,' "$WORK/Master_AdultHistory.csv")" "1"
stop

echo
echo "== the next start keeps it =="
start second.log
chk "no new history is started over it" "$(grep -c 'Started a new, empty adult history' "$WORK/second.log")" "0"
chk "and the adult is still there" "$(grep -c '^ADULT,.*,Firstrun,Frances,' "$WORK/Master_AdultHistory.csv")" "1"
stop

echo
echo "== a start that fails ends the process =="
# A config file named with -c must exist; this one does not. The process
# must exit (not linger, as it did with the -w window up) and say it failed.
( cd "$WORK" && exec java -jar "$JAR" \
    -a Master_AdultHistory.csv -c missing.properties -port "$PORT" -d 2026-09-27 ) > "$WORK/failed.log" 2>&1 &
SRV=$!
for _ in $(seq 1 30); do
    kill -0 "$SRV" 2>/dev/null || break
    sleep 1
done
if kill -0 "$SRV" 2>/dev/null; then
    bad "the failed start was still running after 30 seconds"
    stop
else
    wait "$SRV"
    code=$?
    SRV=""
    if [ "$code" -ne 0 ]; then
        ok "a failed start exits non-zero ($code)"
    else
        bad "a failed start exited 0"
    fi
fi
if grep -q "config  file 'missing.properties' does not exist" "$WORK/failed.log"; then
    ok "and says why"
else
    bad "the failed start did not say why"
    cat "$WORK/failed.log"
fi

echo
if [ "$FAIL" -gt 0 ]; then
    echo "FIRST RUN: FAIL -- $FAIL of $((PASS + FAIL)) checks failed"
    exit 1
fi
echo "FIRST RUN: PASS -- $PASS checks"
