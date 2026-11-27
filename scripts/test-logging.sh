#!/usr/bin/env bash
# ------------------------------------------------------------------------
# test-logging.sh: what the event log holds after a real event, and what it
# must never hold.
#
# When something goes wrong at an event, eagleboards.log (in the event's data
# folder) is what a maintainer reads. This runs the program the way run.sh
# does, works a small event through it (sign-in, a board seated, started and
# completed, refusals, an Admin edit, an Undo, a Settings save, a 404, an
# unknown endpoint, an /events stream opened and dropped, a restart), then
# checks the file:
#
#   * the start-up banner says which build ran, on what, started how,
#   * every step of the board lifecycle is there, naming the scout and room,
#   * every refusal says REFUSED and why, and shares a request number with
#     the access line that carries its status,
#   * every POST has an access line, a 404 has one, a stream says when it
#     opened and closed, and a snapshot of counts is written,
#   * a clean stop writes a shutdown line (where the platform lets Java run
#     its shutdown hook),
#   * the things only a real server shows: a request stuck for seconds is
#     reported while it is stuck, an aborted download is not a fault, the
#     pages' own reports reach the log without setting off a re-read, a
#     request that never reached the app is logged, a board stranded by an
#     Admin edit is named after a restart, and a start that fails still
#     leaves a log in the data folder,
#   * and NONE of the canary strings below, which stand in for an email
#     address, a phone number, a birthdate, a free-text note and the
#     SignUpGenius key, appears anywhere in the file or its rollovers.
#
# It also checks the console still prints what it always did.
#
#   ./mvnw package && bash scripts/test-logging.sh
#   EB_PORT=18095 bash scripts/test-logging.sh
#
# Everything is SYNTHETIC: a throwaway folder, a spare port, and never the
# real Master_AdultHistory.csv (CLAUDE.md rule 3). Kept apart from
# test-board-event.sh, which the Windows version runs against its own
# server, so nothing Java-specific such as a log file belongs there.
#
# Portability: bash, curl, awk, and a JDK, no Python (see CONTRIBUTING
# "Running the shell scripts off Linux"). Runs on Linux, macOS, and
# Git-for-Windows. Git Bash cannot stop Java gracefully (kill is
# TerminateProcess there), so the shutdown line is skipped, loudly, on Windows.
# ------------------------------------------------------------------------

set -u

PORT="${EB_PORT:-18095}"
B="http://127.0.0.1:$PORT"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK="${TMPDIR:-/tmp}/eb-logging-$$"
SRV=""
LOG="$WORK/run/eagleboards.log"

PASS=0
FAIL=0
SKIP=0

ok()   { echo "  ok  : $*"; PASS=$((PASS + 1)); }
bad()  { echo "FAIL  : $*"; FAIL=$((FAIL + 1)); }
skip() { echo "  SKIP: $*"; SKIP=$((SKIP + 1)); }

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

for t in curl awk java grep; do
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

WINDOWS=0
case "$(uname -s 2>/dev/null)" in
    MINGW*|MSYS*|CYGWIN*) WINDOWS=1 ;;
esac

mkdir -p "$WORK"
cp "$ROOT/config.properties" "$WORK/config.properties"
echo 'Type,ID,Last,First,Email,Phone,UnitType,Unit,UnitName,ProjectReview,FinalBoard,RegTime,Room,Flags,Sel,BoardHistory' \
    > "$WORK/AdultHistory.csv"

# ------------------------------------------------------- canary strings
# Each stands for something the log must never hold. They are distinctive
# enough that one grep for a fragment finds every spelling used below.
KEY="CNRYKEY9Z4"          # 10 characters: under the 11 a real key needs, so no network call is made
NOTE1="CANARY_NOTE_Q7"    # a free-text note, sent when a board completes
NOTE2="CANARY_NOTE_Q7B"   # a note typed into the Admin table
NOTE3="CANARY_NOTE_Q7R"   # a note sent at sign-in
DOB="2009-07-04"          # a birthdate an old cached page might still send

# ------------------------------------------------------------ helpers
# start <extra java args...>: run.sh's options, without the window, in $WORK.
# The console goes to $WORK/console.N so each run's console can be read.
RUNS=0
start() {
    RUNS=$((RUNS + 1))
    ( cd "$WORK" && exec java -jar "$JAR" \
        -a AdultHistory.csv -c config.properties -port "$PORT" -sugkey "$KEY" "$@" ) > "$WORK/console.$RUNS" 2>&1 &
    SRV=$!
    if ! curl -sf --retry 40 --retry-delay 1 --retry-connrefused --retry-all-errors \
         -o /dev/null "$B/index.html"; then
        bad "the server never became ready"
        cat "$WORK/console.$RUNS"
        exit 1
    fi
}

post() { curl -sf -X POST "$@" -o /dev/null; }

# act <path> [curl args...] -> "HTTPCODE|body", as in test-board-event.sh.
act() {
    _p=$1
    shift
    _out=$(curl -s -w '\n%{http_code}' -X POST "$@" "$B$_p")
    echo "$(echo "$_out" | tail -1)|$(echo "$_out" | sed '$d' | tr -d '\r\n')"
}

# lines_with <fixed string>...: the log lines that hold every one of them.
lines_with() {
    _lw="$WORK/lines_with.tmp"
    grep -F -- "$1" "$LOG" > "$_lw" 2>/dev/null
    shift
    for _s in "$@"; do
        grep -F -- "$_s" "$_lw" > "$_lw.2" 2>/dev/null
        mv "$_lw.2" "$_lw"
    done
    cat "$_lw"
}

count_with() { lines_with "$@" | wc -l | tr -d ' '; }

# Shows what a failed check needed to find: the lines holding its first
# string, and the end of the log.
show_log() {
    echo "          the lines holding '$1':"
    grep -F -- "$1" "$LOG" 2>/dev/null | tail -n 4 | cut -c1-300 | sed 's/^/            /'
    echo "          the end of the log:"
    tail -n 12 "$LOG" 2>/dev/null | cut -c1-300 | sed 's/^/            /'
}

# need <what> <fixed string>...: at least one log line holds all of them.
need() {
    _what=$1
    shift
    if [ "$(count_with "$@")" -ge 1 ]; then
        ok "$_what"
    else
        bad "$_what"
        show_log "$1"
    fi
}

# need_n <what> <count> <fixed string>...
need_n() {
    _what=$1
    _want=$2
    shift 2
    _got=$(count_with "$@")
    if [ "$_got" = "$_want" ]; then
        ok "$_what"
    else
        bad "$_what (found $_got lines, wanted $_want)"
        show_log "$1"
    fi
}

# need_re <what> <extended regex>
need_re() {
    if grep -Eq -- "$2" "$LOG" 2>/dev/null; then
        ok "$1"
    else
        bad "$1"
        echo "          no line matches: $2"
        tail -n 12 "$LOG" 2>/dev/null | cut -c1-300 | sed 's/^/            /'
    fi
}

# context_of <fixed string>...: the request number of the last log line holding them.
context_of() {
    lines_with "$@" | tail -n 1 | awk '{print $4}'
}

# refused <what> <marker> <reason> <access line>: a line holding the marker
# (a handler's "Name: REFUSED", or the Admin edit's own line) and the reason
# says why, and the same request also has the access line that carries the
# status. (A refusal answers HTTP 200 with the reason as the body, so the
# status is not what tells a refusal; the REFUSED line is.)
refused() {
    _what=$1
    _marker=$2
    _reason=$3
    _access=$4
    _ctx=$(context_of "$_marker" "$_reason")
    if [ -z "$_ctx" ]; then
        bad "$_what: no line holds '$_marker' and '$_reason'"
        show_log "$_marker"
        return
    fi
    if lines_with "$_access" | awk -v c="$_ctx" '$4 == c {n++} END {exit n > 0 ? 0 : 1}'; then
        ok "$_what"
    else
        bad "$_what: the REFUSED line is in request $_ctx but no '$_access' line is"
        show_log "$_access"
    fi
}

# ------------------------------------------------------------- 1. start
echo
echo "== 1. the first start =="
start -d run

# The console is what an operator watches. It must still say what it always said.
chk "console still shows the address line" "$(grep -c " URL: http://<ip-address>:$PORT" "$WORK/console.1")" "1"
chk "console still says no pre-registrations were loaded" "$(grep -c "no prereg-file or SignupGenius DB loaded" "$WORK/console.1")" "1"
chk "console now says where the event log is" "$(grep -c "Event log: .*eagleboards.log" "$WORK/console.1")" "1"
chk "the key is not on the console" "$(grep -c "$KEY" "$WORK/console.1")" "0"

[ -f "$LOG" ] && ok "eagleboards.log is in the event's data folder" || { bad "no $LOG"; exit 1; }
chk "the log's first line opens the run" "$(head -n 1 "$LOG" | grep -c "=== Eagle Boards starting ===")" "1"
need "the banner names the version and the jar" "INFO " "version " ", jar "
need_re "the banner names java, the system and the machine" "INFO  - \[main\] java .*, .* cpus, max heap [0-9]+ MB"
need "the banner names the clock and the working directory" "clock " "working directory "
need "the banner shows the arguments, with the key masked" "arguments: " "-sugkey ***" "-port $PORT" "-d run"
need "the banner names the event log" "event log: " "eagleboards.log"
need "the banner names the data folder, adult history and settings" "data folder " "adult history " "settings " "port $PORT"
need "the SignUpGenius key's length is said, never the key" "pre-registrations: none loaded" "10 characters"
need "the server says it is listening" "listening on port $PORT"
need_re "a second snapshot is written after the pre-registrations are imported" "INFO  - \[main\] ready: youth 0 .*rooms 0/0 in use"
need "the boards in rooms are listed at start, even when there are none" "loaded boards: (none seated)"
need_re "a snapshot of counts is written once the files are loaded" "INFO  - \[main\] loaded: youth 0 .*rooms 0/0 in use, adults 0 .*heap [0-9]+/[0-9]+ MB, threads [0-9]+, up [0-9]+ min"

# ------------------------------------------------------------- 2. sign in
echo
echo "== 2. sign-in (canary emails, phone numbers, notes, birthdate) =="

LASTS="Quillfeather Brackenridge Tollefson Marchetti Oyelowo Pennington Zielinski Hargreave"
FIRSTS="Aurelia Barnaby Cordelia Dashiell Evangeline Fitzroy Genevieve Horatio"
nth() {
    _nth_list=$1
    _nth_idx=$2
    set -- $_nth_list
    shift $((_nth_idx - 1))
    echo "$1"
}

# adult <n> <unit> <project-role> <final-role> <phone>: echoes the record id.
adult() {
    _l=$(nth "$LASTS" "$1")
    _f=$(nth "$FIRSTS" "$1")
    post --data-urlencode "Last=$_l" --data-urlencode "First=$_f" \
        --data-urlencode "Email=canary.ad$1@example.test" --data-urlencode "Phone=$5" \
        --data-urlencode "UnitType=Troop" --data-urlencode "Unit=$2" --data-urlencode "UnitName=Troop$2" \
        --data-urlencode "ProjectReview=$3" --data-urlencode "FinalBoard=$4" \
        "$B/register-adult" || echo "seed failed: adult $_l" >&2
    echo "ADULT:$_l:$_f:$2"
}

post --data "!nativeeditor_status=inserted&gr_id=ROOM:101&Room=101&BoardType=Final" "$B/room-update"
post --data "!nativeeditor_status=inserted&gr_id=ROOM:102&Room=102&BoardType=Final" "$B/room-update"

AC=$(adult 1 3001 Chair  Chair  "555-010-0191")
AM1=$(adult 2 3002 Member Member "(555) 010-0192")
AM2=$(adult 3 3003 Member Member "555.010.0193")
AM3=$(adult 4 3004 Member Member "+1 555 010 0194")
AM4=$(adult 5 3005 Member Member "555-010-0195")

# scout <n> <unit>: echoes the record id. Email, phone, birthdate and a note
# at sign-in, as an old cached page or a hand-built request could send them.
scout() {
    _l=$(nth "$LASTS" "$((9 - $1))")
    _f=$(nth "$FIRSTS" "$((9 - $1))")
    post --data-urlencode "Last=$_l" --data-urlencode "First=$_f" \
        --data-urlencode "Email=canary.yt$1@example.test" --data-urlencode "Phone=555-010-029$1" \
        --data-urlencode "DOB=$DOB" --data-urlencode "Notes=$NOTE3" \
        --data-urlencode "UnitType=Troop" --data-urlencode "Unit=$2" --data-urlencode "UnitName=Troop$2" \
        --data-urlencode "BoardType=Final" \
        "$B/register-youth" || echo "seed failed: scout $_l" >&2
    echo "SCOUT:$_l:$_f:$2"
}

Y1=$(scout 1 1001)
Y2=$(scout 2 1002)
Y3=$(scout 3 1003)

need "a youth's sign-in is logged by ID, with the new registration number" "register-youth: scout=$Y1" "regnum=W1" "-> signed in"
need "the next youth gets the next number" "register-youth: scout=$Y2" "regnum=W2"
need "the youth line says a birthdate was discarded, without giving it" "register-youth: scout=$Y1" "discarded="
need "an adult's sign-in is logged by ID, with the roles" "register-adult: adult=$AC" "record=new" "final-board=Chair" "-> signed in"
need "each adult is in the log" "register-adult: adult=$AM4"
need_n "every sign-in has its access line with the status (8 POSTs, 5 adults and 3 youth)" 3 "POST /register-youth -> 200"
need_n "adult sign-ins too" 5 "POST /register-adult -> 200"
need "the access line shows lengths for the email and phone, not the values" "POST /register-youth -> 200" "Email=<23 chars>" "Phone=<12 chars>" "Notes=<15 chars>"
need "the new client is noted with its browser, once" "new client 127.0.0.1, browser: curl/"
need_n "and only once for this address" 1 "new client 127.0.0.1"

# ---------------------------------------------------- 3. refusals, lifecycle
echo
echo "== 3. a board: refusals, then seat, start, complete =="

seat() { # <room> <scout> <chair> <member>...
    _rm=$1; _sc=$2; _ch=$3
    shift 3
    _ids=$_ch
    for _m in "$@"; do _ids="$_ids,$_m"; done
    act /seat-board \
        --data-urlencode "RoomID=ROOM:$_rm" --data-urlencode "ScoutID=$_sc" \
        --data-urlencode "ChairID=$_ch" --data-urlencode "MemberIDs=$_ids"
}

# Refusals that never change anything come first, so what each leaves is a log line.
chk "a board of two is refused" "$(seat 101 "$Y2" "$AC" "$AM1" | cut -d'|' -f2 | cut -c1-6)" "ERROR:"
refused "a board of two: REFUSED with the rule" "SeatBoardHandler: REFUSED" "3 required for Final boards" "POST /seat-board"
need "and the rule line says which rule and the numbers" "rule=size REFUSED" "member(s)"
chk "a Member as chair is refused" "$(seat 101 "$Y2" "$AM1" "$AM2" "$AM3" | cut -d'|' -f2 | cut -c1-6)" "ERROR:"
need "a Member chairing: the rule line names the role" "rule=chair REFUSED" "chair role for Final boards is"
chk "a made-up room is refused" "$(act /seat-board --data-urlencode "RoomID=ROOM:999" --data-urlencode "ScoutID=$Y2" | cut -d'|' -f2 | cut -c1-6)" "ERROR:"
need "a made-up room: the log says the room card was not found" "seat-board: room card not found" "roomid=ROOM:999"
chk "a made-up scout is refused" "$(act /seat-board --data-urlencode "RoomID=ROOM:101" --data-urlencode "ScoutID=SCOUT:No:Body:1" | cut -d'|' -f2 | cut -c1-6)" "ERROR:"
need "a made-up scout: the log says the scout was not found" "seat-board: scout not found" "SCOUT:No:Body:1"

R=$(seat 101 "$Y1" "$AC" "$AM1" "$AM2" "$AM3")
chk "the board is seated" "${R%%|*}" "200"
need "Seat Board: scout, room, chair and members are named, and the status change" \
    "seat-board: scout=$Y1" "room=101" "chair=$AC" "$AM1" "$AM3" "-> seated: status Registered -> Seated"
need "Seat Board: the adults are committed to the room" "seat-board: scout=$Y1" "committed 4 adult(s) to the room"
SEAT_CTX=$(context_of "seat-board: scout=$Y1" "-> seated")
if [ -n "$SEAT_CTX" ] && lines_with "POST /seat-board -> 200" | awk -v c="$SEAT_CTX" '$4 == c {n++} END {exit n > 0 ? 0 : 1}'; then
    ok "the seat's lines and its access line share one request number ($SEAT_CTX)"
else
    bad "the seat's lines and its access line do not share a request number"
    show_log "POST /seat-board -> 200"
fi

R=$(seat 101 "$Y2" "$AC" "$AM1" "$AM2" "$AM3")
chk "seating into an occupied room is refused" "$(echo "$R" | cut -d'|' -f2 | cut -c1-6)" "ERROR:"
need "an occupied room: the log names the room and who holds it" "seat-board: room=101 is occupied by"
refused "an occupied room: REFUSED with the reason the operator saw" "SeatBoardHandler: REFUSED" "is already assigned to" "POST /seat-board"

R=$(seat 102 "$Y1" "$AM4" "$AM4")
chk "seating a scout twice is refused" "$(echo "$R" | cut -d'|' -f2 | cut -c1-6)" "ERROR:"
need "a scout seated twice: the log names the scout and its status" "seat-board: scout=$Y1" "status=Seated" "wanted room=102"
refused "a scout seated twice: REFUSED with the reason" "SeatBoardHandler: REFUSED" "Invalid Status 'Seated'" "POST /seat-board"

R=$(seat 102 "$Y2" "$AC" "$AM4" "$AM1" "$AM2")
chk "an adult already on a board cannot sit on a second" "$(echo "$R" | cut -d'|' -f2 | cut -c1-6)" "ERROR:"
need "a committed adult: the rule line names the adult" "rule=one-board-at-a-time REFUSED" "member=$AC"

R=$(act /complete-board --data-urlencode "ScoutID=$Y1" --data-urlencode "RoomID=ROOM:101" --data-urlencode "Result=Approved" \
    --data-urlencode "Notes=$NOTE1")
chk "completing a scout who is only Seated is refused" "$(echo "$R" | grep -c "Invalid Scout Status 'Seated'")" "1"
refused "completing a Seated scout: REFUSED with the status" "CompleteBoardHandler: REFUSED" "Invalid Scout Status 'Seated'" "POST /complete-board"
need "completing a Seated scout: the log names the scout and room" "complete-board: scout=$Y1" "status=Seated" "room card=101"

R=$(act /complete-board --data-urlencode "ScoutID=$Y1" --data-urlencode "Result=Fantastic")
refused "a made-up result is refused" "CompleteBoardHandler: REFUSED" "Invalid Result 'Fantastic'" "POST /complete-board"

R=$(act /inprogress-board --data-urlencode "ScoutID=$Y1")
chk "Start Review answers" "${R%%|*}" "200"
need "Start Review: scout, room, chair and the status change" \
    "start-review: scout=$Y1" "room=101" "chair=$AC" "-> started: status Seated -> InProgress"
R=$(act /inprogress-board --data-urlencode "ScoutID=$Y1")
refused "Start Review twice: REFUSED with the status" "InProgressBoardHandler: REFUSED" "Invalid Status 'InProgress'" "POST /inprogress-board"

R=$(act /complete-board --data-urlencode "ScoutID=$Y1" --data-urlencode "RoomID=ROOM:101" --data-urlencode "Result=Approved" \
    --data-urlencode "Notes=$NOTE1" --data-urlencode "Cost=250" --data-urlencode "BSAHours=40")
chk "Complete answers" "${R%%|*}" "200"
need "Complete: scout, result, status, room and the adults released" \
    "complete-board: scout=$Y1" "result=Approved" "-> completed: status InProgress -> Completed" "room 101: occupied -> free" "released 4 adult(s)"
need "Complete: the notes are logged as a size, never their text" "complete-board: scout=$Y1" "-> completed" "notes "
need "the room's adults are released, by room" "release-adults: room=101" "released=4"
need "Complete's access line has the result and the note's size, not its text" "POST /complete-board -> 200" "Result=Approved" "Notes=<14 chars>"

# ------------------------------------------------------ 4. Undo, postpone
echo
echo "== 4. Undo, Postpone, Admin edit, Settings =="

R=$(act /restore-board)
chk "Undo answers" "${R%%|*}" "200"
need "Undo says what it reversed" "undo: reversing" "Complete board"
need "Undo says it was restored" "undo: reversed" "-> restored"
need "Undo names each field it put back" "undo:   " "Status"
R=$(act /restore-board)
refused "Undo with nothing left is REFUSED" "RestoreBoardHandler: REFUSED" "Nothing to undo" "POST /restore-board"

R=$(act /complete-board --data-urlencode "ScoutID=$Y1" --data-urlencode "RoomID=ROOM:101" --data-urlencode "Result=Approved")
chk "after the Undo the review completes again" "${R%%|*}" "200"
need_n "so the log shows two completions of the scout" 2 "complete-board: scout=$Y1" "-> completed"

R=$(act /postpone-board --data-urlencode "ScoutID=$Y3")
chk "Postpone answers" "${R%%|*}" "200"
need "Postpone: scout and the status change" "postpone-board: scout=$Y3" "-> postponed: status Registered -> Postponed"
R=$(act /postpone-board --data-urlencode "ScoutID=$Y1")
refused "Postpone on a finished scout is REFUSED" "PostponeBoardHandler: REFUSED" "Invalid Status 'Completed'" "POST /postpone-board"

R=$(act /youth-update --data-urlencode "!nativeeditor_status=updated" --data-urlencode "gr_id=$Y2" \
    --data-urlencode "Notes=$NOTE2" --data-urlencode "Email=canary.edit@example.test" --data-urlencode "Phone=555-010-0155")
chk "an Admin edit of a youth answers" "${R%%|*}" "200"
need "the Admin edit is logged with the row and the action" "admin-edit: kind=" "action=updated" "id=$Y2" "-> updated"
need_re "and the edit's columns appear by name, with sizes for what is personal" "admin-edit: .*id=SCOUT:.*Notes <[0-9]+ chars>|admin-edit: .*Notes=<[0-9]+ chars>|admin-edit: .*Notes '<[0-9]+ chars>'|admin-edit: .*Notes.*<[0-9]+ chars>"
R=$(act /adult-update --data-urlencode "!nativeeditor_status=updated" --data-urlencode "gr_id=$AM4" \
    --data-urlencode "Phone=(555) 010-0178" --data-urlencode "Email=canary.edit2@example.test")
chk "an Admin edit of an adult answers" "${R%%|*}" "200"
need "an Admin edit of an adult is logged" "admin-edit: kind=" "id=$AM4" "-> updated"
R=$(act /youth-update --data-urlencode "!nativeeditor_status=updated" --data-urlencode "gr_id=SCOUT:No:Body:1" --data-urlencode "Notes=x")
refused "an Admin edit of a row that is not there is REFUSED" "admin-edit: kind=Scout action=updated id=SCOUT:No:Body:1" "REFUSED: no such record" "POST /youth-update"

R=$(act /update-config --data-urlencode "ID=DEFAULT" --data-urlencode "Name=DEFAULT" --data-urlencode "FinalYellowMins=45")
chk "a Settings save answers" "${R%%|*}" "200"
need "the Settings save says what changed" "update-config: " "FinalYellowMins" "-> saved"
need "a yellow stage not below its red one is warned about, not refused" "WARN " "update-config: the saved settings are odd: FinalYellowMins 45 is not below FinalRedMins 45"

R=$(act /room-update --data-urlencode "!nativeeditor_status=inserted" --data-urlencode "gr_id=ROOM:102" --data-urlencode "Room=102")
refused "inserting a room that is already there is REFUSED" "admin-edit: kind=Room action=inserted id=ROOM:102" "REFUSED: a record with this ID already exists" "POST /room-update"

# ---------------------------------------------------- 5. api, 404, events
echo
echo "== 5. the check-in API, a 404, an unknown endpoint, an event stream =="

curl -s -X POST --data-urlencode "email=canary.api@example.test" "$B/api/adult-lookup" -o /dev/null
curl -s -X POST --data-urlencode "email=canary.ad1@example.test" "$B/api/adult-lookup" -o /dev/null
need "an API call is logged by path, with its status, and no email" "POST /api/adult-lookup -> 200"
need "the API lookup says it found nobody, by the length of the email" "adult-lookup: email=<" "not found"
need "the API lookup that found someone names the record" "adult-lookup: email=<" "found in" "id=$AC"
need_re "an ordinary read's lines carry the sender's address after the request number" "INFO  r[0-9]+/127\.0\.0\.1 \[[^]]*\] adult-lookup: email=<"
chk "the API access lines have no parameters" "$(lines_with "POST /api/" | grep -cE ' [[A-Za-z!]+=')" "0"

curl -s -o /dev/null "$B/no-such-page.html"
need "a missing page is logged as a 404" "GET /no-such-page.html -> 404"
curl -s -o /dev/null -X POST --data "x=1" "$B/no-such-endpoint"
need "a POST to an unknown endpoint is logged as a 404" "POST /no-such-endpoint -> 404" "x=<1 chars>"
chk "an ordinary page read is not in the log (the pages would bury it)" "$(count_with "GET /index.html")" "0"

# Open the change feed, then drop it. The server only learns the page has
# gone when it next writes to it, so two changes are announced.
curl -s -N -m 2 "$B/events" -o "$WORK/stream.out" >/dev/null 2>&1
need "an event stream opened is logged with the address" "event stream opened by 127.0.0.1"
act /seat-board --data-urlencode "RoomID=ROOM:999" >/dev/null
sleep 1
act /seat-board --data-urlencode "RoomID=ROOM:999" >/dev/null
for _ in $(seq 1 30); do
    [ "$(count_with "event stream closed after")" -ge 1 ] && break
    sleep 1
done
need "and its closing is logged with how long it was open and how many are left" "event stream closed after" "(0 open)"

chk "none of this was an error or a failed request" "$(grep -c -E ' ERROR | -> 5[0-9][0-9] ' "$LOG")" "0"
chk "the console printed no ERROR either" "$(grep -c "ERROR" "$WORK/console.1")" "0"

# ---------------------------------------------------- 6. stop, restart
echo
echo "== 6. a clean stop and a restart =="

stop
if [ "$WINDOWS" = 1 ]; then
    skip "shutdown line: Git Bash stops Java with TerminateProcess, which skips its shutdown hook (checked on Linux and macOS)"
else
    need "a clean stop writes a shutdown line" "=== shutting down after "
fi

start -d run
need_n "a restart appends: the banner is in the file twice" 2 "=== Eagle Boards starting ==="
need_re "the restart's snapshot shows the event's counts (3 youth, 2 rooms, 5 adults)" "loaded: youth 3 .*rooms 0/2 in use, adults 5 "
need "the restart warns again about the odd saved setting" "WARN " "settings: FinalYellowMins 45 is not below FinalRedMins 45"
chk "the first run's lines are still there" "$(count_with "register-youth: scout=$Y1" "regnum=W1")" "1"
chk "the second console also names the log" "$(grep -c "Event log: .*eagleboards.log" "$WORK/console.2")" "1"
stop
if [ "$WINDOWS" != 1 ]; then
    need_n "the second stop adds a second shutdown line" 2 "=== shutting down after "
    need "a clean stop writes a closing snapshot first" "closing: youth 3 "
fi

# ------------------------------------------------------- 7. the privacy rule
echo
echo "== 7. nothing personal in the log or its rollovers =="

ALL=""
for f in "$WORK"/run/eagleboards.log "$WORK"/run/eagleboards.log.[1-5]; do
    [ -f "$f" ] && ALL="$ALL $f"
done
[ -n "$ALL" ] && ok "the log files to search: $(echo "$ALL" | wc -w | tr -d ' ')" || bad "no log files"

# leaks <what> <extended regex>
leaks() {
    _n=$(cat $ALL 2>/dev/null | grep -c -E -- "$2")
    if [ "$_n" = "0" ]; then
        ok "no $1 in the log"
    else
        bad "$_n log line(s) hold $1"
        cat $ALL | grep -E -- "$2" | head -n 3 | cut -c1-300 | sed 's/^/            /'
    fi
}

leaks "canary email address" 'canary\.'
leaks "email address of any kind" '[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+\.[A-Za-z]{2,}'
leaks "example.test host" '@example\.test'
leaks "canary phone number" '555[-. )]+010|010[-. ]0[0-9]{3}|\(555\)'
leaks "phone number of any kind" '(^|[^0-9-])\(?[0-9]{3}\)?[-. ][0-9]{3}[-. ][0-9]{4}([^0-9-]|$)'
leaks "free-text note" 'CANARY_NOTE'
leaks "birthdate" "$DOB"
leaks "SignUpGenius key" "$KEY"

# What the log must hold instead: the people by ID.
need "people are named by ID instead (so a refusal can be traced)" "$AC"
need "and the scout by ID and registration number" "$Y1 (W1)"

# ------------------------------------------------------- 8. -debug
echo
echo "== 8. -debug adds DEBUG lines and says so =="
LOG="$WORK/debugrun/eagleboards.log"
start -d debugrun -debug
curl -s -o /dev/null "$B/admin.html"
need "the banner says DEBUG detail is on" "DEBUG detail is on"
need_re "DEBUG lines are in the file, marked" "DEBUG .*LocalDefaultHandler: target=/admin.html"
need "and the banner still masks the key" "arguments: " "-sugkey ***" "-debug"
chk "the key is not in a DEBUG log either" "$(cat "$WORK"/debugrun/eagleboards.log | grep -c "$KEY")" "0"
chk "nor on that console" "$(grep -c "$KEY" "$WORK/console.3")" "0"
stop

# --------------------------------------------- 9. what a real event meets
echo
echo "== 9. a stuck request, a dropped download, the pages' own reports, a bad request =="
LOG="$WORK/stallrun/eagleboards.log"
start -d stallrun

# A POST whose body trickles in keeps its request open: the servlet is entered at
# the headers and reads the form when it looks at the parameters. Left running
# while the rest of this section goes on.
( printf 'a=1'; sleep 13 ) | curl -s -X POST -T - -H "Expect:" -H "Content-Type: application/x-www-form-urlencoded" \
    "$B/seat-board" -o /dev/null &
SLOW=$!

# A tablet that leaves the page while a file is still coming is not a fault.
for _ in 1 2 3; do
    curl -s --max-time 1 --limit-rate 20k -o /dev/null "$B/tabulator/tabulator.min.js"
done
sleep 2
chk "an aborted download is not logged as a fault" "$(count_with "could not send the file")" "0"

# A refusal whose reason holds an accented letter (a surname such as Munoz with a tilde) is
# longer in bytes than in characters. It once failed with a 500 ("too much content written")
# and the operator saw the exception, not the rule. The expected text is built with printf so no
# shell or editor has to carry the characters, and the requests are percent-encoded UTF-8 as a
# browser sends them (a native curl.exe re-encodes a raw non-ASCII argument in the ANSI code
# page, which is not UTF-8, and the server then reads no parameters at all).
N_TILDE=$(printf '\303\261')
E_ACUTE=$(printf '\303\251')
post --data "!nativeeditor_status=inserted&gr_id=ROOM:109&Room=109&BoardType=Final" "$B/room-update"
for _u in 1 2 3 4; do
    if [ "$_u" = 1 ]; then _names="Last=Mu%C3%B1oz&First=Jos%C3%A9"; _role=Chair; else _names="Last=Garc%C3%ADa&First=Ana$_u"; _role=Member; fi
    post --data "$_names&Email=accent.ad$_u@example.test&Phone=555-010-04$_u$_u&UnitType=Troop&Unit=310$_u&UnitName=Troop310$_u&ProjectReview=$_role&FinalBoard=$_role" "$B/register-adult"
done
ACCENT_YOUTH=$(scout 5 1105)
R=$(act /seat-board --data "RoomID=ROOM:109&ScoutID=$ACCENT_YOUTH&ChairID=ADULT:Mu%C3%B1oz:Jos%C3%A9:3101&MemberIDs=ADULT:Garc%C3%ADa:Ana2:3102,ADULT:Garc%C3%ADa:Ana3:3103,ADULT:Garc%C3%ADa:Ana4:3104")
chk "a refusal naming an accented person is answered, not a 500" "${R%%|*}" "200"
chk "and it carries the whole reason, accents intact" "${R#*|}" "ERROR: Chair Jos${E_ACUTE} Mu${N_TILDE}oz is not one of the board members"
chk "and no write failed in the log" "$(count_with "too much content written")" "0"

# A request that never reaches the app: not HTTP at all, as a tablet pointed at the wrong port sends.
if ( exec 3<>"/dev/tcp/127.0.0.1/$PORT" && printf 'THIS IS NOT HTTP\r\n\r\n' >&3 && sleep 1 ) 2>/dev/null; then
    need "a request that never reached the app is logged with its address and status" "request never reached the app" "from 127.0.0.1"
else
    skip "a request that never reached the app: this bash cannot open /dev/tcp"
fi

# The sign-in pages' reads carry the address (there is no access line for a read that succeeds).
curl -s -o /dev/null "$B/api/scout-choices"
need_re "a read with no access line still says which address sent it" "INFO  r[0-9]+/127\.0\.0\.1 \[[^]]*\] scout-choices: "

# A board, and an Admin edit that strands it.
post --data "!nativeeditor_status=inserted&gr_id=ROOM:101&Room=101&BoardType=Final" "$B/room-update"
AC=$(adult 1 3001 Chair  Chair  "555-010-0191")
AM1=$(adult 2 3002 Member Member "555-010-0192")
AM2=$(adult 3 3003 Member Member "555-010-0193")
YS=$(scout 1 1001)
R=$(seat 101 "$YS" "$AC" "$AM1" "$AM2")
chk "a board is seated for the stranding test" "${R%%|*}" "200"
R=$(act /room-update --data-urlencode "!nativeeditor_status=deleted" --data-urlencode "gr_id=ROOM:101")
chk "the Admin table deletes the occupied room card (nothing stops it)" "${R%%|*}" "200"
need "the delete says the card was occupied, and by whom" "admin-edit: kind=Room action=deleted id=ROOM:101" "occupied=yes by $YS" "-> deleted"

# The slow POST is reported while it is stuck, then ends.
for _ in $(seq 1 30); do
    [ "$(count_with "stalled: request")" -ge 1 ] && break
    sleep 1
done
need "a request that has not finished is reported while it is stuck, with the state of its thread" \
    "stalled: request r" "POST /seat-board" "has run for" "its thread" "is "
need "and the stack of that thread follows" "stack of "
wait "$SLOW" 2>/dev/null
for _ in $(seq 1 10); do
    [ "$(count_with "slow: POST /seat-board")" -ge 1 ] && break
    sleep 1
done
need "when it ends, the access line says it was slow" "slow: POST /seat-board"

# A second copy on the same port must not claim to be listening (Windows lets two sockets share a port).
if [ "$WINDOWS" != 1 ]; then
    ( cd "$WORK" && java -jar "$JAR" -a AdultHistory.csv -c config.properties -port "$PORT" -d portrun ) > "$WORK/console.port" 2>&1
    chk "a second copy on a port in use exits with an error" "$?" "1"
    PORTLOG="$WORK/portrun/eagleboards.log"
    chk "it never says it is listening" "$(grep -c "listening on port" "$PORTLOG")" "0"
    chk "and says the port is in use" "$(grep -c "the program could not start.*is in use" "$PORTLOG")" "1"
else
    skip "a second copy on a port in use: Windows lets two sockets share a port"
fi

# After a restart the log says which boards came back, and that this one has no room card.
stop
start -d stallrun
need "after a restart the log lists the boards the program came back with" "loaded boards: room 101: $YS" "Seated" "chair $AC"
need "and warns that the board's room has no card" "consistency (loaded): $YS (Seated) sits in room 101, which has no room card"
stop

# ------------------------------ 10. a start that fails still leaves a log
echo
echo "== 10. starts that fail, and files that are not what they should be =="

# -c names a file that is not there. The folder is fine, so the log goes in it.
mkdir -p "$WORK/failrun"
( cd "$WORK" && java -jar "$JAR" -a AdultHistory.csv -c no-such-settings.properties -port "$PORT" -d failrun ) > "$WORK/console.fail" 2>&1
chk "a start that cannot go ahead exits with an error" "$?" "1"
LOG="$WORK/failrun/eagleboards.log"
[ -f "$LOG" ] && ok "the failed start left eagleboards.log in the data folder" || bad "the failed start left no log"
need "the failure is in the log, not only on the console" "the program could not start"
need "with the line that says why" "config file " "REFUSED: it does not exist"
need "and the banner that says which build ran" "=== Eagle Boards starting ==="

# A mistyped -sugkey=KEY: the parser refuses it, and its message must not spell the key out.
( cd "$WORK" && java -jar "$JAR" -sugkey=QRSTXYZ123456789 -port "$PORT" -d keyrun ) > "$WORK/console.key" 2>&1
chk "a mistyped -sugkey=KEY exits with an error" "$?" "1"
chk "its message does not spell out the key, letter by letter or whole" "$(grep -c "Q R S\|QRSTXYZ\|1 2 3 4 5" "$WORK/console.key")" "0"
chk "it names the argument with the key masked" "$(grep -c -- "-sugkey=\*\*\*" "$WORK/console.key")" "1"
chk "and no data folder or log was made for a command line that did not parse" "$([ -e "$WORK/keyrun" ] && echo yes || echo no)" "no"

# A spreadsheet with no heading row is refused, and its first row is a person.
printf 'CanaryLast,CanaryFirst,canary.pre@example.test,5550100155,Troop,77,CANARY_NOTE_PRE\n' > "$WORK/headless.csv"
( cd "$WORK" && java -jar "$JAR" -a AdultHistory.csv -c config.properties -port "$PORT" -d prerun -p headless.csv ) > "$WORK/console.pre" 2>&1
chk "a spreadsheet with no heading row stops the start" "$?" "1"
LOG="$WORK/prerun/eagleboards.log"
need "it is refused with how many cells it had, not what they held" "REFUSED: expected column" "7 cells, none of them a column this version knows"

# Data files whose first line is a record, and an ID rewritten as it loads.
mkdir -p "$WORK/hdrrun"
printf 'ADULT:Canarysmith:Zed:123,Canarysmith,Zed,canary.hdr@example.test,5550100199,Troop,123,CANARY_NOTE_HDR\n' > "$WORK/hdrrun/scouts.csv"
printf 'Type,ID,Last,First,Unit\nADULT,Smith:Alex,Quillson,Zed,3001\n' > "$WORK/hdrrun/adults.csv"
LOG="$WORK/hdrrun/eagleboards.log"
start -d hdrrun
need "a data file whose first line is a record says how many cells, not what they hold" \
    "load: file=scouts.csv first line has 8 cells and none is a column this version knows"
need "an ID replaced as the file loads is reported with both IDs" "load: ID Smith:Alex was replaced by ADULT:Quillson:Zed:3001"
stop

# Nothing from these runs holds what the privacy rule keeps out.
ALL=""
for f in "$WORK"/stallrun/eagleboards.log "$WORK"/failrun/eagleboards.log "$WORK"/prerun/eagleboards.log "$WORK"/hdrrun/eagleboards.log "$WORK"/portrun/eagleboards.log; do
    [ -f "$f" ] && ALL="$ALL $f"
done
leaks "canary email address in these runs" 'canary\.|Canary(smith|Last|First)'
leaks "email address of any kind in these runs" '[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+\.[A-Za-z]{2,}'
leaks "phone number of any kind in these runs" '(^|[^0-9-])\(?[0-9]{3}\)?[-. ]?[0-9]{3}[-. ]?[0-9]{4}([^0-9-]|$)'
leaks "free-text note in these runs" 'CANARY_NOTE'
leaks "SignUpGenius key in these runs" "$KEY"

echo
if [ "$FAIL" -gt 0 ]; then
    echo "LOGGING: FAIL ($FAIL of $((PASS + FAIL)) checks failed, $SKIP skipped)"
    exit 1
fi
echo "LOGGING: PASS ($PASS checks, $SKIP skipped)"
