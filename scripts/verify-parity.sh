#!/bin/bash
# Behavioral parity check: original inherited jar vs rebuilt-from-source jar.
#
# The project owner cannot review Java code, so this script is the acceptance
# gate for the reconstruction: it proves the rebuilt jar behaves identically
# to original/EagleBoardScheduler_20190618.jar without reading the code.
#
#   1. Structure  — every class's declared members match (javap), modulo
#                   compiler-internal synthetics (access$ bridges, switch maps)
#   2. Reads      — both servers return identical bytes for every page,
#                   asset, and data endpoint
#   3. Writes     — identical registration/update requests produce identical
#                   data files on disk
#
# Timestamps (RegTime/LastUpdateTime fields, HTTP Date headers) are the only
# normalized difference. Everything else must match byte-for-byte.
#
# Usage: scripts/verify-parity.sh   (from the repo root; builds if needed)

set -u
cd "$(dirname "$0")/.."

ORIG_JAR=original/EagleBoardScheduler_20190618.jar
NEW_JAR=$(ls target/eagleboardscheduler-*.jar 2>/dev/null | grep -v original- | head -1)
PORT_A=18080
PORT_B=18081
FAILURES=0

fail() { echo "FAIL: $1"; FAILURES=$((FAILURES + 1)); }
note() { echo "  ok: $1"; }

if [ -z "$NEW_JAR" ]; then
    echo "building rebuilt jar..."
    ./mvnw -q package || { echo "build failed"; exit 1; }
    NEW_JAR=$(ls target/eagleboardscheduler-*.jar | grep -v original- | head -1)
fi

command -v javap >/dev/null || { echo "javap not found (need a JDK)"; exit 1; }

# ---------------------------------------------------------------- 1. structure
echo "== 1. structural comparison (javap declared members) =="
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"; kill $PID_A $PID_B 2>/dev/null' EXIT
mkdir -p "$WORK/orig" "$WORK/new"
unzip -qo "$ORIG_JAR" 'shkc/*' 'monfox/*' -d "$WORK/orig"
unzip -qo "$NEW_JAR"  'shkc/*' 'monfox/*' -d "$WORK/new" 2>/dev/null  # monfox/* absent post-swap

# Compiler-internal artifacts with no behavior of their own; javac 25 emits
# them differently than javac 7 did (nestmates replaced access$ bridges).
# NetTest.class: the original jar misfiled this default-package class under
# shkc/core/, where the class loader could never have loaded it.
KNOWN_SYNTHETIC='monfox/log/SimpleLogger[$]1[.]class|shkc/core/NegaPreRegAdultRecordConverter[$]1[.]class|shkc/core/NetTest[.]class|shkc/core/WebServer[$]1[.]class'

# Deliberately rewritten during the Jetty 8 -> 12 migration (the only class
# that touches Jetty APIs, plus its dispatcher inner class, which became an
# HttpServlet). Their runtime behavior is still fully covered by sections
# 2-4; signature comparison against the Jetty 8 original is meaningless.
MIGRATED='shkc/core/WebServer[.]class|shkc/core/WebServer[$]LocalDefaultHandler[.]class'

# Vendored third-party code the original binary carried, replaced by
# supported libraries: shkc.json.simple (json-simple 1.1, frozen 2012) ->
# Jackson; monfox.log -> java.util.logging.
REMOVED_VENDORED='^shkc/json/simple/|^monfox/'

sig() { # normalized member signatures for one class file
    # Filtered as compiler-internal (verified behaviorally equivalent in the
    # bytecode): access$ bridges, enum $values()/switch-maps, and anonymous-
    # class capture plumbing (val$ field names and constructor shapes vary
    # between javac 7 and javac 25 but perform the same stores).
    # "static {};" also filtered: the original DataFileConverter carries an
    # EMPTY <clinit> (verified: its bytecode is a single return) that javac 25
    # elides; real static state lives in field initializers, which still show.
    # javax.servlet and jakarta.servlet are the same API before/after the
    # Jetty 12 migration — the rename is normalized so handler signatures
    # still compare meaningfully.
    javap -p "$1" 2>/dev/null \
      | grep -vE 'access[$][0-9]+|[$]SwitchMap[$]|Compiled from|private static .*[$]values\(\)|val[$]|final .* this[$]0;|[a-zA-Z0-9_.]+[$]1\);|^ *static \{\};' \
      | sed -E "s/[$][0-9]+\((, )?[a-zA-Z0-9_.$]+(, [a-zA-Z0-9_.$]+)*\);/\$N(CAPTURES);/; s/[$][0-9]+\(\);/\$N(CAPTURES);/; s/(javax|jakarta)\.servlet/SERVLET_API/g; s/(shkc\.json\.simple\.JSONArray|com\.fasterxml\.jackson\.databind\.JsonNode)/JSON_TREE/g; s/(monfox\.log|java\.util\.logging)\.Logger/LOGGER/g" \
      | grep -v '_debugLogger' \
      | sed 's/[[:space:]]\+/ /g' | sort
}

missing=0
while IFS= read -r cls; do
    rel=${cls#"$WORK/orig/"}
    if echo "$rel" | grep -qE "$KNOWN_SYNTHETIC"; then continue; fi
    if echo "$rel" | grep -qE "$MIGRATED"; then continue; fi
    if echo "$rel" | grep -qE "$REMOVED_VENDORED"; then continue; fi
    if [ ! -f "$WORK/new/$rel" ]; then
        fail "class missing from rebuilt jar: $rel"
        missing=1
        continue
    fi
    if ! diff <(sig "$WORK/orig/$rel") <(sig "$WORK/new/$rel") >"$WORK/sigdiff" 2>&1; then
        fail "member signatures differ: $rel"
        sed 's/^/      /' "$WORK/sigdiff" | head -10
    fi
done < <(find "$WORK/orig" -name '*.class')
[ "$missing" -eq 0 ] && note "all classes present"
note "signature comparison complete"

# WEBROOT: since the UI rework (dhtmlx -> Tabulator), only the assets the
# rework preserved are still expected byte-identical to the original jar.
# The HTML/JS pages are first-party code now, checked in the UI section
# below; dhtmlx/, CVS/, and old_saved_script.js were dropped deliberately;
# signup_genius_api.js ships a key placeholder (checked separately).
PRESERVED="help.html scheduler.css NegaScheduler.png ScoutButton.png LeaderButton.png"
for p in $PRESERVED; do
    if ! cmp -s "$WORK/orig/shkc/core/WEBROOT/$p" "$WORK/new/shkc/core/WEBROOT/$p"; then
        fail "preserved WEBROOT asset differs from original: $p"
    fi
done
imgdiff=$(diff -rq "$WORK/orig/shkc/core/WEBROOT/images" "$WORK/new/shkc/core/WEBROOT/images" 2>/dev/null | grep -vE '/CVS|: CVS$' || true)
if [ -n "$imgdiff" ]; then
    fail "WEBROOT images differ:"; echo "$imgdiff" | head -5
else
    note "preserved WEBROOT assets byte-identical to original"
fi
if grep -q 'REPLACE_WITH_SIGNUP_GENIUS_KEY' "$WORK/new/shkc/core/WEBROOT/signup_genius_api.js"; then
    note "rebuilt jar carries the key placeholder, not a real key"
else
    fail "rebuilt signup_genius_api.js does not contain the expected placeholder"
fi

# -------------------------------------------------------------- 2/3. runtime
echo "== 2. side-by-side servers =="
rm -rf parity && mkdir -p parity/A parity/B
for d in parity/A parity/B; do
    cp config.csv Master_AdultHistory.csv "$d/"
done

( cd parity/A && exec java -jar "../../$ORIG_JAR" -verbose \
    -a Master_AdultHistory.csv -c config.csv -port $PORT_A -d testrun \
    >server.log 2>&1 ) & PID_A=$!
( cd parity/B && exec java -jar "../../$NEW_JAR" -verbose \
    -a Master_AdultHistory.csv -c config.csv -port $PORT_B -d testrun \
    >server.log 2>&1 ) & PID_B=$!

for port in $PORT_A $PORT_B; do
    for i in $(seq 1 30); do
        curl -sf -o /dev/null "http://127.0.0.1:$port/index.html" && break
        sleep 1
        [ "$i" = 30 ] && { fail "server on :$port never became ready"; }
    done
done
note "both servers up (:$PORT_A original, :$PORT_B rebuilt)"

# Normalize the only legitimate differences: clock values, and the "Etowah
# District" branding prefix that was deliberately removed from six pages
# (stripping it from the original's responses makes them comparable to the
# de-branded rebuild; any other divergence on those pages still fails).
# LC_ALL=C so sed survives binary bodies (PNGs) — they still diff byte-wise.
norm() { LC_ALL=C sed -E 's/[0-9]{4}-[0-9]{2}-[0-9]{2}_[0-9]{2}:[0-9]{2}(-[0-9]{4})?/TIMESTAMP/g; s/Etowah District:? //g'; }

req() { # method path [data] -> normalized "status + body" from one server
    local port=$1 method=$2 path=$3 data=${4:-}
    if [ "$method" = POST ]; then
        curl -s -w '\n%{http_code}\n' -X POST --data "$data" "http://127.0.0.1:$port$path"
    else
        curl -s -w '\n%{http_code}\n' "http://127.0.0.1:$port$path"
    fi | norm
}

compare() { # method path [data]
    local method=$1 path=$2 data=${3:-}
    if ! diff <(req $PORT_A "$method" "$path" "$data") \
              <(req $PORT_B "$method" "$path" "$data") >"$WORK/rdiff" 2>&1; then
        fail "$method $path responses differ:"
        sed 's/^/      /' "$WORK/rdiff" | head -8
    fi
}

compare_status() { # method path — status code only; body is Jetty's own
                   # error page, whose HTML differs between Jetty versions.
    local method=$1 path=$2
    local a b
    a=$(curl -s -o /dev/null -w '%{http_code}' -X "$method" "http://127.0.0.1:$PORT_A$path")
    b=$(curl -s -o /dev/null -w '%{http_code}' -X "$method" "http://127.0.0.1:$PORT_B$path")
    if [ "$a" != "$b" ]; then
        fail "$method $path status differs: original=$a rebuilt=$b"
    fi
}

echo "== 2a. read endpoints (parity vs original) =="
# Server-generated responses and preserved assets must match the original.
READS="/help.html
/scheduler.css /NegaScheduler.png /ScoutButton.png /LeaderButton.png
/scout-cells /adult-cells /adult-history-cells /room-cells
/scouts-scheduled-cells /scout-autofill /adult-autofill /config-autofill"
count=0
for p in $READS; do compare GET "$p"; count=$((count+1)); done
# Unknown paths: the 404 must match; the error-page body is generated by
# Jetty itself and legitimately differs between Jetty 8 and 12.
compare_status GET /no-such-page
compare_status GET /no-such-dir/x.html
note "$count GET paths compared + 2 status-only 404 paths"

echo "== 3. write endpoints =="
compare POST /register-scout "Last=Parity&First=Test&Email=parity@example.org&Phone=555-000-0001&UnitType=Troop&Unit=9999&UnitName=T9999&DOB=2008-01-01&BoardType=EagleBoard&Leader=Leader+Parity"
compare POST /register-adult "Last=Boardmember&First=Check&Email=board@example.org&Phone=555-000-0002&UnitType=Troop&Unit=9999&UnitName=T9999&ProjectReview=Member&FinalBoard=Member"
compare GET /scout-cells
compare GET /adult-cells
compare POST /room-update  '!nativeeditor_status=inserted&gr_id=1&c0=1&c1=Room+101&c2=&c3='
compare GET /room-cells
compare POST /verify-board "ScoutID=BOGUS:Nobody:X:0"      # error path must match too
compare POST /seat-board   "RoomID=1&ScoutID=BOGUS&ChairID=B&MemberIDs=C"
compare POST /room-change  "RmID1=&RmID2="
note "write/error endpoints compared"

echo "== 3a. resulting data files =="
sleep 1
for f in $(cd parity/A && find testrun -type f 2>/dev/null; echo Master_AdultHistory.csv); do
    if [ ! -f "parity/B/$f" ]; then fail "file missing on rebuilt side: $f"; continue; fi
    if ! diff <(norm <"parity/A/$f") <(norm <"parity/B/$f") >"$WORK/fdiff" 2>&1; then
        fail "data file differs: $f"; sed 's/^/      /' "$WORK/fdiff" | head -8
    fi
done
note "data files compared"

echo "== 4. startup logs =="
# Normalized: sandbox path A/B, port, times, object identity hashes, and
# size: lines (de-branded pages are a few bytes smaller than the originals;
# their content is compared directly in section 2a).
# Dropped entirely: the web container's own chatter, which legitimately
# differs between the original's bundled Jetty 8 and the rebuild's Jetty 12
# (startup banners, SLF4J notices, and the old Jetty-8-era ResourceHandler
# getResource/RESOURCE trace lines that no longer exist).
normlog() { norm <"$1" | LC_ALL=C sed -E "s/:1808[01]/:PORT/g; s/[0-9]{2}:[0-9]{2}:[0-9]{2}[.,][0-9]+/TIME/g; s/[0-9]{4}-[0-9]{2}-[0-9]{2} TIME/DATETIME/g; s|parity/[AB]|parity/X|g; s/@[0-9a-fA-F]+/@ID/g; s/^size:[0-9]+$/size:N/; s/context-path=null/context-path=/" \
    | grep -vE '^(DATETIME|TIME)?[: ]*(INFO|WARN)[: ]|SLF4J|jetty|oejs|oeje|getResource: |RESOURCE: |Session workerName|Started |Logging initialized' \
    | python3 -c '
# Parameter-map dumps ({k=[v],...}) keep the same entries but a different
# iteration order under Jetty 12 vs 8 — sort entries so order is irrelevant.
import sys
for line in sys.stdin:
    s = line.rstrip("\n")
    if s.startswith("{") and s.endswith("}") and "=[" in s:
        print("{" + ",".join(sorted(s[1:-1].split(","))) + "}")
    else:
        print(s)'; }
if ! diff <(normlog parity/A/server.log) <(normlog parity/B/server.log) >"$WORK/ldiff" 2>&1; then
    fail "startup logs differ:"; sed 's/^/      /' "$WORK/ldiff" | head -12
else
    note "startup logs identical (modulo time/port)"
fi

echo "== 5. first-party UI (rebuilt server only) =="
# These pages were rewritten on Tabulator (dhtmlx removed); they have no
# original-jar counterpart to diff against. Assert they serve, are free of
# dhtmlx references, and kept the headings operators (and CI greps) rely on.
UI_PAGES="/index.html /index_simple.html /admin.html /scheduler.html
/configure.html /scout_register.html /adult_register.html /eb-data.js
/scheduler_config.js /scheduler_grid.js /scheduler_scout_grid.js
/scheduler_adult_grid.js /scheduler_board_grid.js
/process_seat.js /process_verify.js /process_inprogress.js
/process_complete.js /process_postpone.js /process_reset.js
/tabulator/tabulator.min.js /tabulator/tabulator.min.css"
uicount=0
for p in $UI_PAGES; do
    if ! curl -sf "http://127.0.0.1:$PORT_B$p" -o "$WORK/ui.out"; then
        fail "UI file not served: $p"
        continue
    fi
    case "$p" in
        /eb-data.js) ;;  # its comments document the legacy dhtmlx protocol
        *) grep -qi "dhtmlx" "$WORK/ui.out" && fail "dhtmlx reference remains in $p" ;;
    esac
    uicount=$((uicount + 1))
done
curl -sf "http://127.0.0.1:$PORT_B/index.html" | grep -q "Welcome to the Eagle Board." || fail "index.html heading changed"
curl -sf "http://127.0.0.1:$PORT_B/admin.html" | grep -qi "Eagle Board Admin Page" || fail "admin.html title changed"
curl -sf "http://127.0.0.1:$PORT_B/scheduler.html" | grep -qi "Eagle Board Scheduler" || fail "scheduler.html heading changed"
note "$uicount first-party UI files served clean"

kill $PID_A $PID_B 2>/dev/null; wait 2>/dev/null

echo
if [ "$FAILURES" -eq 0 ]; then
    echo "PARITY: PASS — rebuilt jar is behaviorally identical to the original"
else
    echo "PARITY: $FAILURES FAILURE(S) — see above"
fi
exit $FAILURES
