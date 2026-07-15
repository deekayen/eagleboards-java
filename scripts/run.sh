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

exec java -jar "$JAR" -verbose -w \
    -a Master_AdultHistory.csv -c config.csv -port 8080 \
    ${SUG_KEY:+-sugkey "$SUG_KEY"}
