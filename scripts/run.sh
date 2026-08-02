#!/bin/bash
# Launch the Review Board Scheduler (rebuilt jar) with the same options the
# legacy RunScheduler script used, minus the hardcoded API key.
#
# The SignUpGenius API key is read from the SUG_KEY environment variable,
# or from an untracked .env file beside this repo (copy .env.example).
set -eu
cd "$(dirname "$0")/.."

if [ -z "${SUG_KEY:-}" ] && [ -f .env ]; then
    # shellcheck disable=SC1091
    . ./.env
fi

# `ls -t` (newest first), not a plain `ls`: the version is a DATE, so sorting
# alphabetically puts the OLDEST jar first. `./mvnw package` never removes the
# previous version's jar, so they accumulate in target/ and a plain `head -1`
# quietly launches a months-old build -- which looks by every outward sign like
# the current one, because the window, the port and the pages are all the same.
pick_jar() { ls -t target/eagleboardscheduler-*.jar 2>/dev/null | grep -v original- | head -1; }

JAR=$(pick_jar)
if [ -z "$JAR" ]; then
    echo "No built jar found — building with ./mvnw package ..."
    ./mvnw -q package
    JAR=$(pick_jar)
fi

# Say which build is starting, and name any older ones still sitting there.
# Picking the newest is a good guess, not a guarantee -- the only way to be
# certain target/ holds exactly what the source says is a clean build.
others=$(ls target/eagleboardscheduler-*.jar 2>/dev/null | grep -v original- | grep -vF "$JAR" || true)
if [ -n "$others" ]; then
    echo "NOTE: target/ holds more than one build. Starting the newest:"
    echo "        $JAR"
    echo "      Older jars are still there; './mvnw clean package' clears them:"
    echo "$others" | sed 's/^/        /'
fi

# WebServer.sendResponseFile checks the FILESYSTEM before the classpath: it
# tries `new File("WEBROOT", name)` first and only falls back to the copy
# packaged in the jar when that does not exist. The path is relative, so it
# resolves against this working directory -- the repo root, thanks to the cd
# above. A stray WEBROOT/ here therefore shadows the entire UI, and every
# rebuild is both real and completely ignored. Original inherited behaviour
# (it let operators patch a page without rebuilding), so this warns rather
# than refusing, and does not touch the folder.
if [ -d WEBROOT ]; then
    echo "WARNING: a WEBROOT/ directory exists in $(pwd)."
    echo "         The server serves pages from there IN PREFERENCE to the ones"
    echo "         built into the jar, so what you see will be whatever that"
    echo "         folder holds no matter how many times you rebuild."
    echo "         Move it aside if you did not put it there deliberately."
    echo "         (The parity gate does not see this: it runs the servers in"
    echo "         parity/A and parity/B, where no WEBROOT/ exists.)"
fi

# -w pops up the Swing window with the check-in URL. The app force-disables
# java.awt.headless, so on a machine with no display (e.g. a headless
# Raspberry Pi) -w would crash at startup — only add it when a display exists.
W_FLAG="-w"
if [ "$(uname)" != "Darwin" ] && [ -z "${DISPLAY:-}" ] && [ -z "${WAYLAND_DISPLAY:-}" ]; then
    W_FLAG=""
    echo "No display detected — skipping the URL popup window (-w)."
fi

exec java -jar "$JAR" -verbose $W_FLAG \
    -a Master_AdultHistory.csv -c config.properties -port 8080 \
    ${SUG_KEY:+-sugkey "$SUG_KEY"}
