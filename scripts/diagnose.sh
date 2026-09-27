#!/bin/bash
# Why am I looking at an old version of the app?
#
# There are five independent ways this app can serve stale pages, and every one
# of them is silent -- the UI looks completely normal, just out of date. They
# have each cost real debugging time, so rather than checking them by hand (and
# guessing wrong), this checks all five and says which one you have.
#
# Usage: scripts/diagnose.sh [port]     (default 8080, the port run.sh uses)

set -u
cd "$(dirname "$0")/.."

PORT=${1:-8080}
VERDICTS=0
verdict() { echo; echo "  >>> $1"; VERDICTS=$((VERDICTS + 1)); }

# The age test. The reference is the scheduler page in THIS checkout; a jar or
# a running server is "current" only if the page it holds is byte-identical to
# it. That is exact and needs no version number, no Java and no marker string.
# (Markers were tried twice -- the product name, then the UI toolkit -- and
# each one went stale itself: the name was changed and changed back, and every
# build since the Tabulator rework carries the same toolkit, so a month-old jar
# read as current.) When a page differs, the wording in it still says roughly
# which era it came from, and that is reported as a hint, never as the verdict.
REF=src/main/resources/shkc/core/WEBROOT/scheduler.html
TMP=$(mktemp -d "${TMPDIR:-/tmp}/eb-diag.XXXXXX") || exit 1
trap 'rm -rf "$TMP"' EXIT

# describe_page FILE -> one line saying how FILE relates to the checkout.
# Returns 0 when identical (current), 1 when it differs (stale, or the checkout
# has uncommitted edits -- section 1 says which).
describe_page() {
    if cmp -s "$1" "$REF"; then
        echo "identical to this checkout's scheduler.html (CURRENT)"
        return 0
    fi
    local era="a different build"
    if grep -q "dhtmlx" "$1"; then
        era="the 2019 dhtmlx interface"
    elif grep -q "Review Board Scheduler" "$1"; then
        era="a mid-2026 build (it still says 'Review Board')"
    elif grep -q "Verify" "$1"; then
        era="a build that still had the Verify step"
    elif grep -q "scheduler_grid.js" "$1"; then
        era="a build with the four-panel grid layout, before the Event page"
    fi
    local lines
    lines=$(diff "$1" "$REF" 2>/dev/null | grep -c '^[<>]')
    echo "$era -- $lines line(s) differ from this checkout (OLD)"
    return 1
}

echo "================================================================"
echo " Eagle Board Scheduler — staleness diagnosis"
echo "================================================================"
echo "working directory : $(pwd)"
echo "git commit        : $(git log --oneline -1 2>/dev/null || echo '(not a git repo)')"
echo "port under test   : $PORT"

# --- 1. source ---------------------------------------------------------------
# Everything below is measured against this checkout, so first say what state
# the checkout is in. Two things make the comparison mean something other than
# "stale": edits you have not committed (the jar was built from an earlier
# state, correctly), and commits on origin you have not pulled (your source is
# the old thing, and everything will agree with it).
echo
echo "1. SOURCE TREE (the reference everything below is compared against)"
if [ ! -f "$REF" ]; then
    echo "   ?: $REF not found — are you in the repo root?"
    echo "   Nothing below can be compared without it."
    exit 1
fi
dirty=$(git status --porcelain -- src/main/resources/shkc/core/WEBROOT 2>/dev/null)
if [ -n "$dirty" ]; then
    echo "   WEBROOT has uncommitted edits:"
    echo "$dirty" | sed 's/^/      /'
    echo "   (a jar built before these edits will read as OLD below; that is"
    echo "    expected until you rebuild)"
else
    echo "   ok: WEBROOT matches the last commit"
fi
behind=$(git rev-list --count HEAD..@{u} 2>/dev/null)
if [ -z "$behind" ]; then
    echo "   ?: no upstream branch to compare against"
elif [ "$behind" -gt 0 ]; then
    echo "   origin is $behind commit(s) ahead of this checkout"
    verdict "Your checkout itself is old. Run: git pull"
else
    echo "   ok: up to date with origin as of the last fetch (run 'git fetch' to be sure)"
fi

# --- 2. WEBROOT shadowing ----------------------------------------------------
# WebServer.sendResponseFile tries `new File("WEBROOT", name)` BEFORE the
# classpath, resolved against the working directory. A folder here therefore
# outranks everything in the jar, forever.
echo
echo "2. WEBROOT/ FOLDER SHADOWING THE JAR"
if [ -d WEBROOT ]; then
    echo "   found: $(pwd)/WEBROOT"
    if [ -f WEBROOT/scheduler.html ]; then
        echo "   its scheduler.html is $(describe_page WEBROOT/scheduler.html)"
    fi
    verdict "A WEBROOT/ folder here is being served INSTEAD of the jar's pages.
      The jar is never consulted, so rebuilding can never fix it.
      Fix: mv WEBROOT WEBROOT.disabled"
else
    echo "   ok: no WEBROOT/ folder here"
fi

# --- 3. which jar ------------------------------------------------------------
# `ls` sorts alphabetically and the version is a date, so a plain `head -1`
# picks the OLDEST build. run.sh uses `ls -t` now, but an old jar still being
# present is worth reporting either way.
echo
echo "3. BUILT JARS IN target/"
jars=$(ls -t target/eagleboardscheduler-*.jar 2>/dev/null | grep -v original-)
if [ -z "$jars" ]; then
    echo "   none — nothing built yet. Run: ./mvnw clean package"
else
    echo "$jars" | while read -r j; do echo "   $(ls -l "$j" | awk '{print $6, $7, $8}')  $j"; done
    JAR=$(echo "$jars" | head -1)
    echo "   run.sh would start: $JAR"
    if unzip -p "$JAR" 'shkc/core/WEBROOT/scheduler.html' > "$TMP/jar.html" 2>/dev/null && [ -s "$TMP/jar.html" ]; then
        desc=$(describe_page "$TMP/jar.html"); rc=$?
        echo "   that jar's scheduler.html is $desc"
        if [ "$rc" -ne 0 ]; then
            if [ -n "$dirty" ]; then
                echo "   (expected: section 1 shows uncommitted edits it predates)"
            else
                verdict "The jar itself is stale. Run: ./mvnw clean package
      (plain 'package' leaves deleted/renamed files in target/classes)"
            fi
        fi
    else
        echo "   ?: could not read scheduler.html from that jar"
    fi
    count=$(echo "$jars" | wc -l | tr -d '[:space:]')
    [ "$count" -gt 1 ] && echo "   note: $count jars present; './mvnw clean package' clears the old ones"
fi

# --- 4. stray inherited binary ----------------------------------------------
echo
echo "4. INHERITED 2019 BINARY IN THE WAY"
stray=$(ls EagleBoardScheduler_*.jar 2>/dev/null)
if [ -n "$stray" ]; then
    echo "   found in repo root: $stray"
    echo "   (run.sh will not pick this up, but starting it by hand serves the 2019 UI)"
else
    echo "   ok: none in the repo root"
fi

# --- 5. a server already holding the port ------------------------------------
# The app EXITS with 'Failed to bind' when the port is taken. A forgotten
# instance therefore keeps serving while every restart you launch dies on
# startup -- the single most convincing way to believe a rebuild did nothing.
echo
echo "5. SOMETHING ALREADY LISTENING ON :$PORT"
listener=""
if command -v lsof >/dev/null 2>&1; then
    listener=$(lsof -nP -iTCP:"$PORT" -sTCP:LISTEN 2>/dev/null | tail -n +2)
elif command -v ss >/dev/null 2>&1; then
    listener=$(ss -lptn "sport = :$PORT" 2>/dev/null | tail -n +2)
elif command -v netstat >/dev/null 2>&1; then
    listener=$(netstat -anv 2>/dev/null | grep "[.:]$PORT " | grep -i listen)
fi
if [ -n "$listener" ]; then
    echo "$listener" | sed 's/^/   /'
else
    echo "   nothing listening (or no tool to check with)"
fi

# --- 6. what is ACTUALLY being served ---------------------------------------
# The only answer that matters. Everything above is a cause; this is the effect.
echo
echo "6. WHAT THE LIVE SERVER ON :$PORT ACTUALLY SERVES"
rc=0
if ! curl -s --max-time 5 "http://127.0.0.1:$PORT/scheduler.html" -o "$TMP/served.html" 2>/dev/null || [ ! -s "$TMP/served.html" ]; then
    echo "   nothing responded — no server running on :$PORT"
else
    desc=$(describe_page "$TMP/served.html"); rc=$?
    echo "   served page is $desc"
fi
if [ "$rc" -ne 0 ]; then
    if [ -n "$listener" ]; then
        pid=$(echo "$listener" | awk 'NR==1{print $2}')
        echo "   the process holding the port is shown in section 5 above"
        verdict "An OLD server is answering on :$PORT.
      If sections 1-4 all say ok, this is a forgotten process from an
      earlier session: the app exits with 'Failed to bind' when the port
      is taken, so every restart you launch dies while this one keeps
      serving. Kill it and start again:
          kill $pid    # then: scripts/run.sh"
    else
        verdict "An OLD server is answering on :$PORT, but nothing here shows
      what process it is. It may be in a container or under another user."
    fi
fi

# --- 6b. every instance on this machine --------------------------------------
# The question none of the checks above can answer: is the page in the browser
# even coming from the server we just inspected? A second instance on another
# port, or a deployed one on another host, looks identical in the tab. List
# every scheduler answering anywhere locally, with its age, so the URL in the
# address bar can be matched against it.
echo
echo "6b. EVERY SCHEDULER INSTANCE ANSWERING ON THIS MACHINE"
found_any=0
if command -v lsof >/dev/null 2>&1; then
    # NAME column is second-to-last ("*:8080"), (LISTEN) is last.
    for p in $(lsof -nP -iTCP -sTCP:LISTEN 2>/dev/null \
               | awk '/^java/ {print $(NF-1)}' | sed 's/.*://' | sort -u); do
        curl -s --max-time 3 "http://127.0.0.1:$p/scheduler.html" -o "$TMP/port-$p.html" 2>/dev/null
        [ -s "$TMP/port-$p.html" ] || continue
        # Only schedulers: any Java process with a listening socket lands here.
        grep -q "scheduler_event.js\|scheduler_grid.js\|dhtmlx" "$TMP/port-$p.html" || continue
        found_any=1
        desc=$(describe_page "$TMP/port-$p.html"); rc=$?
        echo "   http://127.0.0.1:$p  →  $desc"
        if [ "$rc" -ne 0 ] && [ "$p" != "$PORT" ]; then
            verdict "An OLD instance is answering on port $p, which is NOT the
      port checked above. If this is the one your browser is pointed
      at, that is your answer -- stop it and use :$PORT instead."
        fi
    done
fi
[ "$found_any" -eq 0 ] && echo "   none found locally"
echo "   Compare these with the URL in your browser's address bar. If it does"
echo "   not match any line here, you are looking at a DIFFERENT MACHINE (a"
echo "   Raspberry Pi or laptop at the venue, or a stale bookmark) and nothing"
echo "   in this checkout can affect it."

# --- 7. the browser ----------------------------------------------------------
# Cannot be tested from here, and it is the one cause that survives fixing
# every other one, so it gets said out loud rather than checked.
echo
echo "7. BROWSER CACHE (cannot be checked from a script)"
echo "   These are plain static pages, so a browser can serve its own cached"
echo "   copy. If everything above says ok and the tab still looks old, do a"
echo "   hard reload — Cmd-Shift-R on macOS — or open a private window."

echo
echo "================================================================"
if [ "$VERDICTS" -eq 0 ]; then
    echo "No stale-build cause found. If the UI still looks old, it is most"
    echo "likely the browser cache (7). Send this output if not."
else
    echo "$VERDICTS likely cause(s) found — see the >>> lines above."
fi
echo "================================================================"
