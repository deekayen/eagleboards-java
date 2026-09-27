# Architecture

A tour of how the Eagle Board Scheduler fits together, for someone about to
change it. For *why* the code looks the way it does, read
[PROVENANCE.md](PROVENANCE.md); for workflow rules, [CONTRIBUTING.md](CONTRIBUTING.md).

## Shape of the thing

One self-contained jar. No database, no application server, no install. It runs
a small web server on the local network; every screen is a browser page.

```
  Check-in station (browser)          Admin computer (browser)
        |  /  /youth_register               |  /admin   /scheduler   /configure
        |  /adult_register                  |
        +---------------+-------------------+
                        |  HTTP, LAN only
                +-------v--------+
                |  Embedded      |   Jetty 12 (ee11 / jakarta.servlet)
                |  Jetty         |   ONE servlet: WebServer$LocalDefaultHandler
                +-------+--------+
                        |  dispatch by path
                +-------v--------------------------+
                |  EagleBoardScheduler             |  handler registry + app logic
                +-------+--------------------------+
                        |
        +---------------+----------------+-------------------+
        |               |                |                   |
   DataRecordFile   WEBROOT assets   SignUpGeniusPlugin   PopupDialog
   (CSV on disk)    (served from     (optional import)    (optional, -w)
                     the classpath)
```

## Entry point

`shkc.core.EagleBoardScheduler` — the jar's `Main-Class`.

`main()` parses the command line (see the README for the option table),
enumerates network interfaces to decide which URL to advertise, optionally shows
the Swing `PopupDialog` with that URL, then constructs the app and starts the
server. The constructor resolves the data directory, opens each
`DataRecordFile`, and registers every HTTP handler.

Everything after startup is request-driven; there is no background thread other
than Jetty's.

## Web layer

`WebServer` wraps Jetty 12. A single servlet, `WebServer$LocalDefaultHandler`,
receives every request and dispatches it to a handler registered by path in
`EagleBoardScheduler`. Static files are resolved by `FileLocator`, which checks
the filesystem first and then the classpath — which is why the UI can be served
straight out of the jar.

Two things worth knowing before you touch this:

- **All request handling is wrapped in a `Throwable` safety net** in
  `WebServer.service`. A handler that throws logs a stack trace and returns a
  clean 500 for that one request instead of killing the server. Do not rely on
  it — null-guard your inputs — but know it is there.
- `-bind <ip-prefix>` pins the public connector to a single local address so the
  socket never appears on virtual or VPN adapters, and always adds a second
  connector on `127.0.0.1` so the admin machine keeps working.

## Data layer

There is no database. Records live in CSV files, read and written through
`DataRecordFile<T>` (the file) and `DataRecord` (a row).

| Record | File | Lifetime |
| --- | --- | --- |
| `ScoutRecord` | `scouts.csv` | one event night |
| `AdultRecord` | `adults.csv` | one event night |
| `RoomRecord` | `rooms.csv` | one event night |
| `ScoutRecord` (pre-reg) | `scouts_scheduled.csv` | one event night |
| `AdultRecord` (history) | `Master_AdultHistory.csv` | **cumulative, permanent** |
| `ConfigRecord` | `config.properties` | permanent, single row |

`-d` selects the event-night directory and defaults to today's date
(`YYYY-MM-DD`), so each night gets its own folder next to the previous ones.
`Master_AdultHistory.csv` is the exception: it accumulates across every event
and is **modified in place**, both by adult check-in and by the SignUpGenius
import.

`DataRecordFile.load`/`store` picks its format from the file extension:
`.properties` uses `java.util.Properties` (`key=value`, `#` comments), anything
else is CSV. The CSV path is kept so the original binary and its comparison
still work. Note that saving config from the Settings page rewrites the file and
does **not** preserve comments.

Each record class declares a `COLUMNS` array. A column must be listed there to
be served through `/config-autofill` and friends.

## Browser UI

Static files under `src/main/resources/shkc/core/WEBROOT`, packaged into the jar.
Built on [Tabulator](https://tabulator.info/) (MIT), which replaced the original
GPL dhtmlxSuite.

| File | Role |
| --- | --- |
| `index.html` | check-in station landing page; live registered lists |
| `youth_register.html`, `adult_register.html` | check-in forms |
| `scheduler.html` | the operator's main screen |
| `admin.html` | tabular admin over every record type |
| `configure.html` | Settings |
| `help.html` | operator documentation |
| `eb-data.js` | **the adapter** — speaks the server's wire formats |
| `scheduler_*_grid.js` | grid behavior per panel |
| `process_*.js` | one file per board lifecycle action |

`eb-data.js` is the seam worth understanding first. The server's wire formats
are inherited and odd — XML-ish `<rows><row><cell>` for grids, dhtmlx-combo
pseudo-JSON with unquoted keys for autofill lists, form-encoded POSTs with a
`!nativeeditor_status` field for writes. `eb-data.js` hides all of that behind
`ebFetchRows`, `ebSaveRow`, `ebAction`, `ebAutofillList`, and
`ebAutofillRecord`. Prefer changing the client over changing the server.

Three screens auto-refresh on the `RefreshTimeSecs` setting: the check-in index,
the admin tab in view, and the scheduler grids. Polls skip while a cell editor
is open or the browser tab is hidden.

## Board lifecycle

```
  Registered ──► InProgress ──► Completed
       │                        (Result: Approved | Adjourned | NotApproved)
       └──────► Postponed
```

Two deliberate departures from the original binary:

- **Seat and Start were merged.** Seating goes straight to `InProgress`; there is
  no separate `Seated` state and no Start button.
- **Verify was removed.** Paperwork is checked off-screen, so a `Registered`
  scout is seated directly. There is no `/verify-board` endpoint.

Both old statuses are still *accepted* wherever they appeared, so a record
carried over from an older run stays usable instead of stuck.

Room cards show a minutes-since-seated badge that turns yellow then red at
thresholds that are specific to the board type — `ProjectYellowMins`,
`ProjectRedMins`, `FinalYellowMins`, `FinalRedMins`.

## SignUpGenius import

`SignUpGeniusPlugin` (Jackson for JSON) runs at startup when `-sugkey` is given
and no `-prereg` file was supplied. It makes two calls to
`api.signupgenius.com`: one to find the active signup whose title contains both
"eagle" and "board" and whose date range covers today, and one for that signup's
filled-slots report.

Entries whose `item` text contains "adult" become `AdultRecord`s merged into
`Master_AdultHistory.csv` **by email** — matched records get their phone
updated, unmatched ones are added. Everything else becomes a pre-registered
`ScoutRecord` in `scouts_scheduled.csv`, which is what powers email autofill at
the check-in station.

Two behaviors that surprise people: the filter is by **calendar month**, not by
day, so two board nights in one month both import; and a duplicate email in the
adult history means that record is silently skipped entirely.

## Output of an event night

A dated folder (`YYYY-MM-DD/`) holding `scouts.csv`, `adults.csv`, `rooms.csv`,
and `scouts_scheduled.csv`, plus a board-results spreadsheet. These contain
personal information about minors and are never committed — see
[SECURITY.md](SECURITY.md).

## Reading order for a newcomer

1. `EagleBoardScheduler.main()` and the constructor — the wiring
2. `eb-data.js` — the client/server contract in one screen
3. `scheduler.html` plus `scheduler_event.js` — the operator's workflow
4. One `process_*.js` — how a single lifecycle action travels end to end
5. `.github/workflows/build.yml` — what "correct" is defined as here
