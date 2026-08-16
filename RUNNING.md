# Running the Review Board Scheduler

Setup, build, command-line options, the settings file, and the quirks that have
cost real debugging time. This is the technical companion to
[README.md](README.md), which is the operator's manual for an event night.

## Requirements

- **Java 21 or newer.** The build targets Java 21 bytecode; CI uses Temurin 25.
- **Maven is not required** — use the bundled wrapper, `./mvnw` (`mvnw.cmd` on
  Windows).

### Supported platforms

Every push and pull request is built and smoke-tested on:

| Platform | Notes |
| --- | --- |
| Linux amd64 | |
| Windows amd64 | typical admin laptop |
| Linux arm64 | Raspberry Pi deployment target |

Anything with a JDK 21+ should work. The optional Swing popup is skipped
automatically when there is no display, so a headless Pi is fine.

## Build

```sh
git clone https://github.com/deekayen/eagleboards.git
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

Or invoke it directly:

```sh
java -jar target/eagleboardscheduler-*.jar \
  -verbose -w -a Master_AdultHistory.csv -c config.properties \
  -port 8080 -bind 192.168. -sugkey "$SUG_KEY"
```

Two windows appear: a console and a small grey window showing the URL to open on
the check-in station.

### Screens

| Screen | URL | Who uses it |
| --- | --- | --- |
| Check-in | `http://<ip>:8080/` | Scouts and adults, at the registration station |
| Scheduler | `http://<ip>:8080/scheduler` | The operator assigning boards to rooms |
| Admin | `http://<ip>:8080/admin` | Managing records directly |
| Settings | `http://<ip>:8080/configure` | Colors and warning timings |
| Help | `http://<ip>:8080/help` | Operator instructions |

The board lifecycle is **Registered → Verified → Seated → InProgress →
Completed** (or Postponed), with a result of Approved, Adjourned, or
NotApproved. `Seated` means the members are in the room with the paperwork and
the scout is still outside; `Start Review` moves it to `InProgress`. The
check-in, admin, and scheduler screens refresh themselves on the
`RefreshTimeSecs` interval, so new arrivals appear without anyone pressing
Refresh.

## Command-line options

| Option | Argument | Meaning |
| --- | --- | --- |
| `-d`, `-dir` | directory | Where the event's data files live. Defaults to today's date, `YYYY-MM-DD`. |
| `-a`, `-adults` | file | Adult auto-fill history. Must exist, or startup fails. |
| `-c`, `-config` | file | Config file. `.properties` is parsed as key=value; anything else as CSV. |
| `-p`, `-prereg` | file | Pre-registration CSV from the district website. Takes priority over SignUpGenius. |
| `-port` | number | Listen port. Default `8080`. |
| `-bind` | ip-prefix | Only listen on, and advertise, the interface whose IPv4 starts with this (e.g. `192.168.`). `127.0.0.1` stays reachable either way. Default: every interface. |
| `-sugkey` | key | SignUpGenius API key, enabling the pre-registration import. |
| `-sugid` | id | Specific SignUpGenius signup ID. Optional; otherwise auto-detected. |
| `-w`, `-windows` | | Show the popup dialog with the check-in URL. |
| `-v`, `-verbose` | | Verbose logging. **Prints the API key** — see SECURITY.md. |
| `-debug` | | Write `eagle-board-scheduler.log`. |
| `-h`, `-help`, `-?` | | Print usage. |

## Configuration

`config.properties` (committed; colors and timings only, no personal data):

| Key | Meaning |
| --- | --- |
| `RefreshTimeSecs` | How often the check-in, admin, and scheduler screens poll |
| `ConveneRedMins` | Minutes since **Seat Board** before the room card turns red. Red only, no yellow: it caps the convening phase rather than pacing it |
| `ProjectYellowMins`, `ProjectRedMins` | Minutes since **Start Review** before a project review's room card turns yellow, then red |
| `FinalYellowMins`, `FinalRedMins` | The same, for final boards |
| `*Color`, `*HiColor` | Row and highlight colors per status |

All of it is editable from the Settings page. Note that saving there rewrites the
file and does not preserve `#` comments.

Timers run on the record's last-update time, so the clock restarts by itself at
each transition.

The SignUpGenius key is read from the `SUG_KEY` environment variable or an
untracked `.env` file — copy `.env.example` and fill it in.

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

`scripts/test-seat-conflicts.js` covers these as pure functions under plain
node, no framework and no network. CI runs it on all three platforms.

## Data files

The application writes into the data directory (`-d`, defaulting to today's
date):

| File | Contents |
| --- | --- |
| `scouts.csv` | Youth who signed in tonight, and their board status |
| `adults.csv` | Adults who signed in tonight |
| `rooms.csv` | Rooms and what is in them |
| `scouts_scheduled.csv` | Pre-registration import, if one was loaded |

The adult auto-fill history named by `-a` sits outside that directory and
persists across nights.

**None of it is ever committed.** It contains personal information about adults
and minors. `.gitignore` excludes all CSV and spreadsheet formats, dated
`YYYY-MM-DD/` folders, `Master_AdultHistory*`, `*Board_Results*`, `LOGIN_INFO*`,
the legacy `RunScheduler.*` scripts (they embed an API key), `original/`, and
`.env`. `scripts/hooks/pre-commit` hard-fails any commit that stages one of
those or a literal API key. Install it once per clone with the `git config` line
above, and do not bypass it with `--no-verify`. Full detail in
[SECURITY.md](SECURITY.md).

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
- **The SignUpGenius import filters by calendar month, not by day.** Two board
  nights in the same month both import.
- **A short API key is silently ignored.** The app requires more than 10
  characters, and the launcher scripts skip the flag entirely if the value is
  still `replace-with-real-key`. Both produce "no prereg-file or SignupGenius DB
  loaded" rather than an error.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). In short: install the pre-commit hook,
build with the wrapper, develop against synthetic data, and keep
`scripts/verify-parity.sh` passing — it is this project's acceptance test, not
code review.
