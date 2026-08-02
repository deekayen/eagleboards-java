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

# The rebrand markers. The app was renamed from "Eagle Board Scheduler" to
# "Review Board Scheduler", so the old name in a page is a reliable age test
# that needs no version number and no Java.
OLD_MARK="Eagle Board Scheduler"
NEW_MARK="Review Board Scheduler"

echo "================================================================"
echo " Review Board Scheduler — staleness diagnosis"
echo "================================================================"
echo "working directory : $(pwd)"
echo "git commit        : $(git log --oneline -1 2>/dev/null || echo '(not a git repo)')"
echo "port under test   : $PORT"

# --- 1. source ---------------------------------------------------------------
echo
echo "1. SOURCE TREE"
src=src/main/resources/shkc/core/WEBROOT/scheduler.html
if [ -f "$src" ]; then
    if grep -q "$NEW_MARK" "$src"; then
        echo "   ok: source says '$NEW_MARK' (current)"
    else
        echo "   source says '$OLD_MARK' (OLD)"
        verdict "Your checkout itself is old. Run: git pull"
    fi
else
    echo "   ?: $src not found — are you in the repo root?"
fi

# --- 2. WEBROOT shadowing ----------------------------------------------------
# WebServer.sendResponseFile tries `new File("WEBROOT", name)` BEFORE the
# classpath, resolved against the working directory. A folder here therefore
# outranks everything in the jar, forever.
echo
echo "2. WEBROOT/ FOLDER SHADOWING THE JAR"
if [ -d WEBROOT ]; then
    echo "   found: $(pwd)/WEBROOT"
    if [ -f WEBROOT/scheduler.html ] && grep -q "$OLD_MARK" WEBROOT/scheduler.html 2>/dev/null; then
        echo "   and its scheduler.html says '$OLD_MARK'"
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
    page=$(unzip -p "$JAR" 'shkc/core/WEBROOT/scheduler.html' 2>/dev/null)
    if echo "$page" | grep -q "$NEW_MARK"; then
        echo "   ok: that jar's scheduler.html says '$NEW_MARK' (current)"
    elif echo "$page" | grep -q "$OLD_MARK"; then
        echo "   that jar's scheduler.html says '$OLD_MARK' (OLD)"
        verdict "The jar itself is stale. Run: ./mvnw clean package
      (plain 'package' leaves deleted/renamed files in target/classes)"
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
served=$(curl -s --max-time 5 "http://127.0.0.1:$PORT/scheduler.html" 2>/dev/null)
if [ -z "$served" ]; then
    echo "   nothing responded — no server running on :$PORT"
elif echo "$served" | grep -q "$NEW_MARK"; then
    echo "   ok: serving '$NEW_MARK' — this is a CURRENT build"
elif echo "$served" | grep -q "$OLD_MARK"; then
    echo "   serving '$OLD_MARK' — this is an OLD build"
    if [ -n "$listener" ]; then
        pid=$(echo "$listener" | awk 'NR==1{print $2}')
        echo "   the process holding the port is shown in section 5 above"
        verdict "An OLD server is answering on :$PORT.
      If sections 1-4 all say ok, this is a forgotten process from an
      earlier session: the app exits with 'Failed to bind' when the port
      is taken, so every restart you launch dies while this one keeps
      serving. Kill it and start again:
          kill $pid    # then: scripts/run.sh"
    fi
else
    echo "   responded, but with neither marker — check by hand"
fi

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
