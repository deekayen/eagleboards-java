# Review Board Scheduler

Check-in and room-scheduling application for Eagle Scout Boards of Review.

On an event night it runs a small web server on the local network. Scouts and
board members check themselves in from a browser at the registration station,
while an administrator manages people and assigns them to rooms from the admin
computer. Data is stored as CSV files next to the application; each event night
writes its own dated folder and a board-results spreadsheet.

This repository reconstructs the application's source from an inherited binary
and modernizes it to build and run on current Java LTS releases. See
[PROVENANCE.md](PROVENANCE.md) for that history, and
[ARCHITECTURE.md](ARCHITECTURE.md) for how the pieces fit together.

## Documentation

| Document | What it covers |
| --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | How the app is built: web layer, data layer, UI, board lifecycle |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Setup, build, the parity gate, house style, PR process |
| [SECURITY.md](SECURITY.md) | Handling participant data and the API key |
| [PROVENANCE.md](PROVENANCE.md) | Where the code came from and how it is authenticated |
| [CLAUDE.md](CLAUDE.md) | Condensed working notes for anyone (human or AI) editing the repo |
| [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | Expected conduct, including youth protection |

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

## Quick start

```sh
git clone https://github.com/deekayen/eagleboards.git
cd eagleboards
git config core.hooksPath scripts/hooks   # required; see SECURITY.md
./mvnw package
```

That produces a self-contained jar at `target/eagleboardscheduler-*.jar`.

Then run it:

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

## Using it on an event night

| Screen | URL | Who uses it |
| --- | --- | --- |
| Check-in | `http://<ip>:8080/` | Scouts and adults, at the registration station |
| Scheduler | `http://<ip>:8080/scheduler` | The operator assigning boards to rooms |
| Admin | `http://<ip>:8080/admin` | Managing records directly |
| Settings | `http://<ip>:8080/configure` | Colors and warning timings |
| Help | `http://<ip>:8080/help` | Operator instructions |

The board lifecycle is **Registered → InProgress → Completed** (or Postponed),
with a result of Approved, Adjourned, or NotApproved. The check-in, admin, and
scheduler screens refresh themselves on the `RefreshTimeSecs` interval, so new
arrivals appear without anyone pressing Refresh.

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
| `ProjectYellowMins`, `ProjectRedMins` | Minutes since seating before a Project review's room card turns yellow, then red |
| `FinalYellowMins`, `FinalRedMins` | The same, for Final boards |
| `*Color`, `*HiColor` | Row and highlight colors per status |

All of it is editable from the Settings page. Note that saving there rewrites the
file and does not preserve `#` comments.

The SignUpGenius key is read from the `SUG_KEY` environment variable or an
untracked `.env` file — copy `.env.example` and fill it in.

## Data and privacy — read before committing

The application's data files sit in the working directory and contain personal
information about adults **and minors**. They are never committed.

- `.gitignore` excludes all CSV and spreadsheet formats, dated `YYYY-MM-DD/`
  folders, `Master_AdultHistory*`, `*Board_Results*`, `LOGIN_INFO*`, the legacy
  `RunScheduler.*` scripts (they embed an API key), `original/`, and `.env`.
- `scripts/hooks/pre-commit` hard-fails any commit that stages one of those or a
  literal API key. Install it once per clone with the `git config` line above,
  and do not bypass it with `--no-verify`.

Full detail in [SECURITY.md](SECURITY.md).

## Known quirks

Things that have cost real debugging time:

- **`-c` with an absolute path is silently ignored.** The path is validated and
  then rebuilt relative to the data directory, so it falls back to
  `config.properties` in the working directory. Pass a relative path and run
  from the directory that holds it.
- **`./mvnw package` does not remove deleted resources** from `target/classes`,
  so a deleted WEBROOT file keeps being packaged and served. Use `clean package`
  after deleting or renaming anything.
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

## License

GPL-2.0 (see [LICENSE](LICENSE)). This was originally dictated by the bundled
dhtmlxSuite UI library; that library has since been replaced by MIT-licensed
Tabulator, so the project license can be revisited — but only after the rights
to the reconstructed application code are settled with its original author.

**The repository is private until then. Do not redistribute it.**
