#!/bin/bash
# Launch the Eagle Board Scheduler (rebuilt jar) with the same options the
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

JAR=$(ls target/eagleboardscheduler-*.jar 2>/dev/null | grep -v original- | head -1)
if [ -z "$JAR" ]; then
    echo "No built jar found — building with ./mvnw package ..."
    ./mvnw -q package
    JAR=$(ls target/eagleboardscheduler-*.jar | grep -v original- | head -1)
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
