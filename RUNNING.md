# Running the Eagle Board Scheduler

Setup, build, command-line options, the settings file, and the quirks that have
cost real debugging time. This is the technical companion to
[README.md](README.md), which is the operator's manual for an event.

## Requirements

- **Java 21 or newer.** The build targets Java 21 bytecode; CI uses Temurin 25.
- **Maven is not required**; use the bundled wrapper, `./mvnw` (`mvnw.cmd` on
  Windows).

### Supported platforms

Every push and pull request is built and smoke-tested on:

| Platform | Notes |
| --- | --- |
| Linux amd64 | |
| Windows amd64 | typical admin laptop |
| Linux arm64 | Raspberry Pi deployment target |
| macOS arm64 | Apple silicon |

Anything with a JDK 21+ should work. The optional Swing popup is skipped
automatically when there is no display, so a headless Pi is fine.

## Build

```sh
git clone https://github.com/deekayen/eagleboards-java.git
cd eagleboards
git config core.hooksPath scripts/hooks   # required; see SECURITY.md
./mvnw package
```

That produces a self-contained jar at `target/eagleboardscheduler-*.jar`.

## Run

```sh
scripts/run.sh        # Linux / macOS / Raspberry Pi
scripts\run.bat       # Windows
```

Both start with a quiet console. The event log (below) is written either way, so
nothing needs turning on to diagnose a problem afterward. To also print every
request and the records being read and written on the console while chasing one,
set `EB_VERBOSE=1` first (`EB_VERBOSE=1 scripts/run.sh`, or `set EB_VERBOSE=1`
on Windows). The API key is masked there (SECURITY.md says exactly where), but
the output includes personal details and each SignUpGenius registrant, so turn
it off again before an event.

Or invoke it directly:

```sh
java -jar target/eagleboardscheduler-*.jar \
  -w -a Master_AdultHistory.csv -c config.properties \
  -port 8080 -bind 192.168. -sugkey "$SUG_KEY"
```

Two windows appear: a console and a small gray window showing the URL to open on
the check-in station. The console also prints `Event log: <path>`, the full path
of the log file for this run.

The first time, there is no `Master_AdultHistory.csv` yet: the program starts a
new, empty one and prints its full path, so check that the path is where you
meant it to be. Every adult who signs in is added to it, and later events
recognize them. If the program cannot start, it prints why, closes the URL window, and
exits with a non-zero code.

### Screens

| Screen | URL | Who uses it |
| --- | --- | --- |
| Check-in | `http://<ip>:8080/` | Scouts and adults, at the registration station |
| Scheduler | `http://<ip>:8080/scheduler` | The operator assigning boards to rooms |
| Admin | `http://<ip>:8080/admin` | Managing records directly |
| Settings | `http://<ip>:8080/configure` | Room timer warnings |
| Help | `http://<ip>:8080/help` | Operator instructions |

The board lifecycle is **Registered → Verified → Seated → InProgress →
Completed** (or Postponed), with a result of Approved, Adjourned, or
NotApproved. `Seated` means the members are in the room with the paperwork and
the scout is still outside; `Start Review` moves it to `InProgress`. The
Event page and the Admin tables update the moment anything changes, so new
arrivals appear without anyone pressing Refresh. The check-in page shows who
has signed in each time it opens.

## Command-line options

| Option | Argument | Meaning |
| --- | --- | --- |
| `-d`, `-dir` | directory | Where the event's data files live. Defaults to today's date, `YYYY-MM-DD`. |
| `-a`, `-adults` | file | Adult auto-fill history. Must exist, or startup fails. |
| `-c`, `-config` | file | Config file. `.properties` is parsed as key=value; anything else as CSV. |
| `-p`, `-prereg` | file | Pre-registration CSV from the district website. Takes priority over SignUpGenius. |
| `-port` | number | Listen port. Default `8080`. |
| `-bind` | ip-prefix | Only listen on, and advertise, the interface whose IPv4 starts with this (e.g. `192.168.`). `127.0.0.1` stays reachable either way. Default: every interface. |
| `-sugkey` | key | SignUpGenius API key. Turns on the pre-registration import. |
| `-sugid` | id | Specific SignUpGenius signup ID. Optional; otherwise auto-detected. |
| `-w`, `-windows` | | Show the popup dialog with the check-in URL. |
| `-v`, `-verbose` | | Print the verbose messages (every request, the records read and written) on the console. Includes personal details; the API key is masked. See SECURITY.md. |
| `-debug` | | Also write the verbose messages into the event log, marked `DEBUG`. Includes personal details; use it to reproduce one problem. Independent of `-verbose`. |
| `-h`, `-help`, `-?` | | Print usage. |

## Configuration

`config.properties` (committed; timings only, no personal data):

| Key | Meaning |
| --- | --- |
| `RefreshTimeSecs` | No longer used: nothing polls. Kept so older builds can read the file |
| `ConveneRedMins` | Minutes since **Seat Board** before the room card's timer shows overdue. No running-long stage: it caps the convening phase instead of pacing it |
| `ProjectYellowMins`, `ProjectRedMins` | Minutes since **Start Review** before a project review's timer shows running long, then overdue |
| `FinalYellowMins`, `FinalRedMins` | The same, for final boards |

Status colors are not settings (shared SPEC.md D-19): the `*Color` and
`*HiColor` keys an older file may still have are ignored and dropped on the
next save.

All of it is editable from the Settings page. Saving there rewrites the
file and does not preserve `#` comments.

Timers run on the record's last-update time, so the clock restarts by itself at
each transition.

The SignUpGenius key is read from the `SUG_KEY` environment variable or an
untracked `.env` file; copy `.env.example` and fill it in.

## Board composition rules

The scheduler enforces these when seating, from the Guide to Advancement:

| Rule | Behavior |
| --- | --- |
| Final board, fewer than 3 members | Refused |
| Final board, 4–6 members | Confirm first |
| Final board, more than 6 (GTA 8.0.0.3) | Refused, no override |
| Project review, fewer than 2 members | Refused |
| Project review, more than 2 | Confirm first |
| Members from the scout's own unit | Overridable warning naming every one of them |
| **Every** member from the scout's own unit | Refused, no override |
| Room type not matching the board type | Confirm first |

`scripts/test-cases.js` covers these as pure functions under plain node, no
framework and no network, from the cases all three versions share
(`scripts/cases/`, copied from eagleboards-shared). CI runs it on every
platform.

## Data files

The application writes into the data directory (`-d`, defaulting to today's
date):

| File | Contents |
| --- | --- |
| `scouts.csv` | Youth who signed in at the event, and their board status |
| `adults.csv` | Adults who signed in at the event |
| `rooms.csv` | Rooms and what is in them |
| `scouts_scheduled.csv` | Pre-registration import, if one was loaded |
| `eagleboards.log` | The event log, and `eagleboards.log.1` to `.5` once it has rolled over (see below) |

The adult auto-fill history named by `-a` sits outside that directory and
persists across events.

**None of it is ever committed.** It contains personal information about adults
and minors. `.gitignore` excludes all CSV and spreadsheet formats, dated
`YYYY-MM-DD/` folders, `Master_AdultHistory*`, `*Board_Results*`, `LOGIN_INFO*`,
the legacy `RunScheduler.*` scripts (they embed an API key), `original/`,
`.env`, and every `*.log` file. `scripts/hooks/pre-commit` hard-fails any commit that stages one of
those or a literal API key. Install it once per clone with the `git config` line
above, and do not bypass it with `--no-verify`. Full detail in
[SECURITY.md](SECURITY.md).

## The event log

Every run writes an event log, so a problem found at an event can be diagnosed
afterward. It is written whatever flags the program was started with; there is
nothing to turn on.

### Where it is

`eagleboards.log` in the event's data folder (`-d`, which defaults to today's
date), beside `scouts.csv` and the other event files. At startup the console
prints its full path:

```
   Event log: /home/pi/eagleboards/2026-11-14/eagleboards.log
```

The first lines of a run are logged before the data folder is known, so they are
held and written to the file as soon as it opens. A start that fails after that
(a mistyped `-c`, a spreadsheet the program refuses) leaves its reason in the log
as well as on the console, as long as the data folder exists. If the data folder
cannot be used, the program does not start and the console is the only record. If
the file cannot be opened at all, the console says so once and the event carries on
without it. If a write fails later (a full disk, a file locked by another program),
the console says so once, the newest 1000 lines wait in memory, and the next write
that works puts them in the file in order, followed by a `WARN` line that says the
log recovered and how many lines it held. A rollover that fails (see below) does
not stop the log: the file grows past 5 MB and the roll is tried again a minute
later.

### What is logged

| Level | Written | What it is |
| --- | --- | --- |
| `INFO` | file only | What happened: startup details, every POST, every refusal and the rule behind it, each board step with its before and after, Admin table edits, Undo, sign-ins, the SignUpGenius import, the first request from each device, event streams opening and closing (and why one closed), and the heartbeat |
| `WARN` | file and console | Something unexpected that the program recovered from: a request that took 2 seconds or longer (`slow:`), one still running after 10 seconds (`stalled:`, with its thread's stack), one that answered 500 (`failed:`), one that never reached the program (`request never reached the app`), an odd line in a data file, an ID rewritten on load, a SignUpGenius failure or a youth's RSVP dropped for sharing another's email, a timer setting that cannot work (`settings:`), a board and a room that disagree (`consistency`), a long garbage collection, a script error in an operator page, or the computer having slept or its clock having moved |
| `ERROR` | file and console, with the stack | A failure nothing recovered from: an unhandled exception in a request, a file that could not be stored, the program failing to start |
| `DEBUG` | file, only with `-debug` | What `-verbose` prints, and a line for every successful page or data read |

An ordinary successful page or data read (a GET) is not in the file unless
`-debug` is on: the pages re-read after every change, and those lines would bury
the ones that matter. Every POST is in the file whatever its answer, and so is any
request that answered 400 or higher or ran slowly.

The first lines of each run record the build (version, jar path, size, and date),
Java, the operating system, the clock, the working directory, the command-line
arguments (with the value after `-sugkey` shown as `***`), the data folder, the
port, and `-bind`. They answer the questions asked first about any report: which
version was that, and was the clock right.

### The console, `-verbose`, and `-debug`

- **The console** shows what it always did, plus one line for each `WARN` and
  `ERROR` (an `ERROR` also prints its stack). `INFO` lines stay in the file.
- **`-verbose`** prints the verbose messages on the console only: each request,
  the records as they are loaded, registered, or imported, and a board's notes. It
  does not put them in the file. The SignUpGenius key is masked in what it prints
  (SECURITY.md lists what is and is not masked).
- **`-debug`** writes those same verbose messages into the event log as `DEBUG`
  lines, and adds a line for every successful GET. The two flags are independent:
  `-debug` does not turn the console output on. A `DEBUG` line is masked for
  secrets only, so unlike the other levels it can hold email addresses, phone
  numbers, and notes. The startup lines say `DEBUG detail is on` when it is.
- **The old `-debug` file is gone.** `-debug` used to write
  `eagle-board-scheduler.log` in the working directory through
  `java.util.logging`, which carried only the file-lookup messages of
  `FileLocator` and nothing from the board logic. Nothing writes that file now.

### Rolling over

When `eagleboards.log` passes 5 MB it becomes `eagleboards.log.1`, the old `.1`
becomes `.2`, and so on up to `.5`; the oldest is deleted. The log therefore keeps
up to five rolled-over files of about 5 MB each, plus the live one. The size is
checked when the program starts and before each line is written. A restart
**appends** to the live file, so one event is one file with a `starting` banner at
each run, until it rolls over. The first line of each new live file says it
continues a rolled-over one (`log continued: ...`).

### Reading it

Each entry is one line:

```
2026-11-14 09:41:55.204 INFO  r212/192.168.1.23 [qtp1042-31] seat-board: scout=SCOUT:Smith:Alex:3401 (P3) room=Room 4 type=Final ... -> seated: status Registered -> Seated, ...
```

The fields are the date and time to the millisecond (the computer's own clock),
the level, the request number and the address of the device that sent it, the
thread, and the message.

- **Request number and address** (`r212/192.168.1.23`). Every request gets the next
  number as it arrives, and every line written while the program handles it
  carries that number and the sender's address, so one tablet's tap can be followed
  through the file, and a read that succeeds (which has no access line) can still
  be tied to the device that made it. A `-` in its place means the line is not part
  of a request (startup, the heartbeat). The count starts again at `r1` at each
  start, so after a restart, read the number together with the time.
- **The access line** is written when a request ends, after the lines its handler
  wrote: `POST /seat-board -> 200 (14 ms) from 192.168.1.23 [ChairID=... MemberIDs=... RoomID=... ScoutID=...]`.
  The bracket holds the request's identifier, status, and setting parameters. Any
  other parameter (an email address, a phone number, a note) shows only its
  length, as `Notes=<28 chars>`. The check-in pages' `/api/` calls show the path
  only. A board step that is refused answers 200 with the reason as the body,
  so its access line shows `-> 200`. Find refusals by the `REFUSED` line that has
  the same request number.
- **`REFUSED`** is in every line that records the program saying no, with the rule
  or the reason given to the operator: `rule=chair REFUSED: ...`,
  `rule=one-board-at-a-time REFUSED: ...`, `SeatBoardHandler: REFUSED: ...`,
  `... REFUSED: no such record ...`. It is the first thing to search for.
- **People** appear as record IDs, `Type:Last:First:Unit` (`SCOUT:Smith:Alex:3401`,
  `ADULT:Hale:Dana:3401`), and a youth also by registration number (`P3`, or `W5`
  for a walk-in). Boards appear by room.
- **A line that begins with four spaces** is the stack trace of the line above it.
  A line break inside a value is written as `?`, so one entry is one line.
- **The heartbeat** is written every 5 minutes, and at start (`loaded:`, before the
  pre-registrations are imported, then `ready:`, after) and at a clean stop
  (`closing:`). It holds counts only, never a name:
  `heartbeat: youth 14 {Completed=6, InProgress=1, Registered=7}, rooms 3/12 in use, adults 30 (6 on a board, 2 gone home), scheduled 20, history 412; heap 96/2048 MB, threads 27, up 62 min`.
  It runs on its own thread (`[heartbeat]`). It shows what state the event was in
  at any time and, if the program stopped, when it was last alive. At start and
  stop a `loaded boards:` or `closing boards:` line lists each board in a room by ID
  (`room 101: SCOUT:Smith:Alex:3401 (P3) Seated, chair ADULT:Hale:Dana:3401, members [...]`),
  so after a restart the log says what the program came back with.
- **Consistency lines.** The monitor checks that the boards and rooms agree: a youth
  on a board with no room card, a card naming a youth with no board, two boards in
  one room, an adult committed to a room no board holds. The Admin tables can
  create each of these. A new disagreement is a `WARN` (`consistency (heartbeat): ...`),
  and one that cleared is an `INFO`, so it is not repeated every five minutes.
- **A stuck request.** A request still running after 10 seconds is reported once while
  it is stuck (`stalled: request r212 POST /seat-board has run for 10 s ...`), with the
  stack of the thread serving it under the line. `BLOCKED` means it is waiting for the
  lock another request holds, and that request is older, so it is reported too. The
  access line is only written when a request ends.
- **Start and stop.** `=== Eagle Boards starting ===` opens each run.
  `=== shutting down after N s ===` closes a run that was stopped normally (Ctrl-C,
  or closing the window). A log that ends with neither a shutdown line nor an
  `ERROR` means the process was killed or the power went.

### Searching it

These work in a terminal on Linux and macOS, and in Git Bash on Windows. In
PowerShell, `Select-String -Pattern <text> eagleboards.log` does the same job.
Write `eagleboards.log*` to include the rolled-over files.

| To find | Run |
| --- | --- |
| Everything the program refused | `grep REFUSED eagleboards.log` |
| One request, start to finish (the slash keeps `r21` from matching `r212`) | `grep ' r212/' eagleboards.log` |
| Every warning and error | `grep -E ' (WARN\|ERROR) ' eagleboards.log` |
| Slow requests (2 seconds or more) and failed ones (500) | `grep -E '\] (slow\|failed): ' eagleboards.log` |
| A request that hung, and where | `grep -A 12 'stalled: request' eagleboards.log` |
| What an operator was told by the page ("it would not let me") | `grep 'client ' eagleboards.log` |
| A board and a room that disagree | `grep 'consistency' eagleboards.log` |
| Requests that never reached the program (a tablet that typed `https://`) | `grep 'never reached the app' eagleboards.log` |
| Which board was in which room at start and stop | `grep -E '(loaded\|closing) boards:' eagleboards.log` |
| Which tablets and browsers connected, and when each first did | `grep 'new client' eagleboards.log` |
| What happened to one person, from the `:Last:First:` part of the ID | `grep -F ':Smith:Alex:' eagleboards.log*` |
| The board steps taken for one youth | `grep -E '(seat-board\|start-review\|complete-board\|postpone-board): ' eagleboards.log \| grep -F ':Smith:Alex:'` |
| The last heartbeat before a crash, then how the file ended | `grep 'heartbeat:' eagleboards.log \| tail -1` and `tail -n 20 eagleboards.log` |
| Every restart | `grep -n 'starting ===' eagleboards.log` |
| A laptop that went to sleep (the line says for how long, and the room timers read wrong for that long) | `grep 'was not running' eagleboards.log` |
| A clock set back (the room timers read short by that much) | `grep 'clock moved back' eagleboards.log` |

To find a request to follow, search for its action first
(`grep 'POST /seat-board' eagleboards.log`), read the number `rNNN` from the line,
then search for the number followed by a slash (`grep ' rNNN/' eagleboards.log`).

### If something went wrong at an event

1. **Note the time,** and what the operator was doing: which screen, which button,
   which youth or room. The log is searched by time and by person, so this is
   what makes the right line quick to find.
2. **Before restarting, copy the whole data folder** to somewhere else (a USB
   stick will do). The log is in it with its rolled-over files, and so are the
   CSVs as they stood when the problem happened.
3. **Then restart if the event needs it.** The program appends to the same
   `eagleboards.log` and writes a new `starting` banner, so the restart shows in
   the file.
4. **Afterward, read the log from the time you noted.** Look for `REFUSED`,
   `WARN`, and `ERROR` lines near it, take the request number of the action that
   went wrong, and read every line with that number. A gap between heartbeats, or
   a `was not running` line, means the computer slept or the program was not
   running.
5. **If you report it,** give the version (the first lines of the run name it), the
   time, and what happened, and quote only the few lines that matter.

### Privacy

The event log is participant data, handled like the CSV files beside it. It names
youth and adults through their record IDs, and it holds the address and browser
name of each tablet. It never holds an email address, a phone number, a
birthdate, a free-text note, or the SignUpGenius key, except in the `DEBUG`
lines that `-debug` adds. It is never committed (`.gitignore` and the pre-commit
hook both exclude `*.log`). **Do not attach it to an issue, an email, or a chat
without reading it first,** and delete it with the rest of the event's data when
that data is deleted. SECURITY.md says what enforces the rule and what is checked
automatically.

## Known quirks

Things that have cost real debugging time:

- **`-c` with an absolute path is silently ignored.** The path is validated and
  then rebuilt relative to the data directory, so it falls back to
  `config.properties` in the working directory. Pass a relative path and run
  from the directory that holds it.
- **`./mvnw package` does not remove deleted resources** from `target/classes`,
  so a deleted WEBROOT file keeps being packaged and served. Use `clean package`
  after deleting or renaming anything.
- **A `WEBROOT/` directory beside the jar shadows the entire UI.** The server
  checks the filesystem before the classpath, so a stray copy there is served in
  preference to the one built into the jar, and every rebuild is both real and
  completely ignored. `scripts/run.sh` warns about this; `scripts/diagnose.sh`
  identifies it among the other causes of a stale-looking page.
- **The SignUpGenius import filters by calendar month, not by day.** Two events
  in the same month both import.
- **A short API key is silently ignored.** The app requires more than 10
  characters, and the launcher scripts skip the flag entirely if the value is
  still `replace-with-real-key`. Both produce "no prereg-file or SignupGenius DB
  loaded" instead of an error.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). In short: install the pre-commit hook,
build with the wrapper, develop against synthetic data, and keep
CI green on all four platforms; that is this project's acceptance test, not
code review.
