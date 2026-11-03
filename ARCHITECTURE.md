# Architecture

A tour of how the Eagle Board Scheduler fits together, for someone about to
change it. For *why* the code looks the way it does, read
[PROVENANCE.md](PROVENANCE.md); for workflow rules, [CONTRIBUTING.md](CONTRIBUTING.md).

## Components

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
                |  EventRoutes                     |  path -> handler table
                +-------+--------------------------+
                        |  each handler gets the shared EventData
                +-------v--------------------------+
                |  *Handler classes                |  one class per endpoint
                +-------+--------------------------+
                        |
        +---------------+----------------+-------------------+
        |               |                |                   |
   DataRecordFile   WEBROOT assets   SignUpGeniusPlugin   PopupDialog
   (CSV on disk)    (served from     (optional import)    (optional, -w)
                     the classpath)
```

## Entry point

`shkc.core.EagleBoardScheduler` is the jar's `Main-Class`.

`main()` parses the command line (see the README for the option table), asks
`NetworkAddresses` to list the URLs the tablets can use (and show them in the
Swing `PopupDialog` with `-w`), then constructs the app and starts the
server. The class is deliberately small. The constructor asks `EventFiles` where
everything lives, opens each `DataRecordFile` into one `EventData`, lets
`PreRegistrationLoader` import the pre-registrations, and has `EventRoutes`
register every HTTP handler.

Everything after startup is request-driven; there is no background thread other
than Jetty's.

## Web layer

`WebServer` wraps Jetty 12. A single servlet, `WebServer$LocalDefaultHandler`,
receives every request and dispatches it to a handler registered by path in
`EventRoutes`. Static files are resolved by `FileLocator`, which checks
the filesystem first and then the classpath, which is why the UI can be served
straight out of the jar.

Two things worth knowing before you touch this:

- **All request handling is wrapped in a `Throwable` safety net** in
  `WebServer.service`. A handler that throws logs a stack trace and returns a
  clean 500 for that one request instead of killing the server. Do not rely on
  it (null-guard your inputs), but know it is there.
- `-bind <ip-prefix>` pins the public connector to a single local address so the
  socket never appears on virtual or VPN adapters, and always adds a second
  connector on `127.0.0.1` so the admin machine keeps working.

## Data layer

There is no database. Records live in CSV files, read and written through
`DataRecordFile<T>` (the file) and `DataRecord` (a row).

| Record | File | Lifetime |
| --- | --- | --- |
| `ScoutRecord` | `scouts.csv` | one event |
| `AdultRecord` | `adults.csv` | one event |
| `RoomRecord` | `rooms.csv` | one event |
| `ScoutRecord` (pre-reg) | `scouts_scheduled.csv` | one event |
| `AdultRecord` (history) | `Master_AdultHistory.csv` | **cumulative, permanent** |
| `ConfigRecord` | `config.properties` | permanent, single row |

`-d` selects the event's directory and defaults to today's date
(`YYYY-MM-DD`), so each event gets its own folder next to the previous ones.
`Master_AdultHistory.csv` is the exception: it accumulates across every event
and is **modified in place**, both by adult check-in and by the SignUpGenius
import.

`DataRecordFile.load`/`store` picks its format from the file extension:
`.properties` uses `java.util.Properties` (`key=value`, `#` comments), anything
else is CSV. The CSV path is kept so the original binary and its comparison
still work. Saving config from the Settings page rewrites the file and
does **not** preserve comments.

Each record class declares a `COLUMNS` array. A column must be listed there to
be served through `/config-autofill` and friends.

## Browser UI

Static files under `src/main/resources/shkc/core/WEBROOT`, packaged into the jar.
The operator's Event page is plain DOM, laid out like the Windows version's
Event page (shared SPEC.md O-3). The Admin tables use
[Tabulator](https://tabulator.info/) (MIT), which replaced the original GPL
dhtmlxSuite. Every operator page shares `eb-app.css`, which follows the system's
light/dark appearance, font, and accent color. The check-in pages are shared by
every version: they come from `eagleboards-shared/checkin` (SPEC.md D-18),
pinned by `checkin-pages.lock`, and built to WCAG 2.2 AA.

| File | Role |
| --- | --- |
| `index.html`, `youth_register.html`, `adult_register.html`, `checkin.css`, `checkin.js` | the check-in pages; shared, copied from `eagleboards-shared/checkin`, never edited here |
| `scheduler.html` + `scheduler_event.js` | the Event page: youth queue, room cards, details pane |
| `admin.html` | Results and People: tabular admin over every record type |
| `configure.html` | Settings |
| `help.html` | operator documentation |
| `eb-data.js` | **the adapter**: speaks the server's wire formats |
| `scheduler_config.js` | timers, status labels, messages, dialogs, menus |
| `process_*.js` | one file per board lifecycle action |

`eb-data.js` is the seam worth understanding first. The server's wire formats
are inherited and odd: XML-ish `<rows><row><cell>` for grids, dhtmlx-combo
pseudo-JSON with unquoted keys for autofill lists, form-encoded POSTs with a
`!nativeeditor_status` field for writes. `eb-data.js` hides all of that behind
`ebFetchRows`, `ebSaveRow`, `ebAction`, `ebAutofillList`, and
`ebAutofillRecord`. Prefer changing the client over changing the server.

The Event and Admin pages never poll. `/events` (`ChangeFeed`) is a
server-sent event stream that sends a message after every POST a handler
answers, and the pages re-read what they show when one arrives; room timers
tick on the minute from the last read. The check-in pages don't refresh on
their own at all: the welcome page's lists load when it opens, which it does
after every sign-in. `RefreshTimeSecs` is no longer read by anything.

The check-in pages call `/api/checked-in`, `/api/scout-choices`,
`/api/youth-lookup`, and `/api/adult-lookup` (`CheckInApi`), the same API every
version serves: names and units for the lists, and for an email lookup only the
fields that form fills in. No birthdate is ever served (D-7): `/register-youth`
discards one, and the `-cells` and `-autofill` endpoints blank one already on
file. A youth's phone number goes the same way (D-8), and is not imported from
a pre-registration either; the youth file keeps the `Phone` column, and the
adult files still serve theirs.

`/checkin-address` lists the addresses the tablets can reach the server at
(`CheckInAddress`), and `/checkin-qr?url=` draws one of them as an SVG QR code
for the Event page's footer.

## Code map

Everything is in the one package, `shkc.core`, one top-level class per file.
A class's Javadoc says what it is for; this is the map.

**Starting up**

| Class | Job |
| --- | --- |
| `EagleBoardScheduler` | `main()`, the usage text, and the wiring in its constructor; nothing else |
| `Commandline` | reads `-d`, `-p`, `-port`, and the other options |
| `NetworkAddresses` | lists the URLs, shows the popup, applies `-bind` |
| `EventFiles` | works out the data folder, adult history, and config file from the command line |
| `PreRegistrationLoader` | imports a pre-registration CSV or SignUpGenius into the "scheduled" files |
| `EventRoutes` | **the table of every URL and the class that answers it** |

**The shared event state**

| Class | Job |
| --- | --- |
| `EventData` | the six record files, the data folder, the event lock, and the Undo log; every handler is given one |
| `UndoLog` / `UndoField` | the one level of Undo (see below) |
| `BoardCompositionChecker` / `BoardComposition` | the hard backstop for board size, chair, and one-board-at-a-time; answers with a refusal or the accepted members. Needs no server, so it is unit tested |
| `ScoutStatus`, `ScoutResult`, `BoardType`, `AdultRole`, `RoomMarker` | the words that appear in the data files, named once instead of repeated as strings |

**Handlers** (each is one endpoint)

| Class | Job |
| --- | --- |
| `BoardHandler` | base for the board steps: the plain-text `OK.` / reason answers |
| `SeatBoardHandler`, `InProgressBoardHandler`, `CompleteBoardHandler`, `PostponeBoardHandler`, `ResetBoardHandler` | the steps of the board lifecycle below |
| `ChangeBoardMembersHandler`, `RoomChangeHandler`, `RenameRoomHandler` | corrections while a board is seated |
| `RestoreBoardHandler` | Undo |
| `RecordCellsHandler` | base for the read side of an Admin table (`ScoutCellsHandler`, `AdultCellsHandler`, `RoomCellsHandler`, and the others are a few lines each) |
| `RecordUpdateHandler` | base for the edit side; subclasses add rules through `refusal`, `keepsOffUndo`, and `afterEdit` (`ScoutUpdateHandler`, `AdultUpdateHandler`, `RoomUpdateHandler` ...) |
| `RegisterScoutHandler`, `RegisterAdultHandler` | the tablets' sign-in |
| `AutoFillHandler`, `UpdateConfigHandler`, `ApprovedProposalsCellsHandler` | email lookup, the Settings page, earlier events' approved proposals |
| `CheckInApi`, `CheckInAddress` | the shared check-in pages' `/api/*` calls and the address and QR code |

**Data and imports**: `DataRecordFile`, `DataRecord` and its subclasses, `DataFileConverter`,
`NegaPreRegScoutRecordConverter`, `NegaPreRegAdultRecordConverter`, `PreRegFieldConverters`,
`SignUpGeniusPlugin`. **Web**: `WebServer`, `FileLocator`, `ChangeFeed`.
**Small helpers**: `Log`, `RequestFields`, `XmlText`, `NameUtil`.

### Adding an endpoint

1. Write a class named for the URL (`/seat-board` is `SeatBoardHandler`).
   Extend `BoardHandler` for a board step, `RecordCellsHandler` or
   `RecordUpdateHandler` for a table, or implement `WebServer.WebHandler`.
   Take an `EventData` in the constructor.
2. Hold `data.getLock()` for as long as you read or change records.
3. Add one line to `EventRoutes`.
4. If it changes how a board is seated, run, or torn down, add a case to
   `scripts/test-board-event.sh`. If it is a pure rule, put the rule in its own
   class and test it in `scripts/test-board-composition.java`.

### The event lock and Undo

Jetty answers requests on several threads, and a board step reads and changes
several records at once. So every handler that changes records, or reads
several together, does its whole job inside `synchronized (data.getLock())`:
there is one lock per event, owned by `EventData`, and `CheckInApi` is given the
same one. Two operators pressing Seat Board at the same moment are therefore
taken one after the other. (`AutoFillHandler` only looks one record up and, as
in the original, does not take the event lock.)

Undo is one level deep. A reversible handler asks `data.getUndoLog().begin()`
for a `Builder`, tells it the old value of every field it is about to change,
and calls `commit(description)` just before it answers. `RestoreBoardHandler`
puts the old values back, but only if every field still holds what the action
wrote; if something else changed one since, the whole undo is refused, not
guessed at. `UndoLog` itself takes no lock, which is why it is only used
while the event lock is held.

## Board lifecycle

```
  Registered ──► Seated ──► InProgress ──► Completed
       │       /seat-board  /inprogress-board  (Result: Approved | Adjourned | NotApproved)
       └──────► Postponed
```

`/seat-board` convenes the board: the members read the paperwork while the
youth waits outside. `/inprogress-board` (Start review) brings the youth in.
While Seated or InProgress, `/change-board-members` swaps who sits on the
board under the same composition rules; the timer keeps running. `/reset-board`
returns a Seated or InProgress youth to Registered, and `/restore-board` undoes
the last reversible action.

**Verify was removed.** Paperwork is checked off-screen, so a `Registered`
scout is seated directly. There is no `/verify-board` endpoint. `Verified` is
still *accepted* wherever it appeared, so a record carried over from an older
run stays usable instead of stuck.

Room cards show minutes since the last step. While Seated, the timer turns
overdue past `ConveneRedMins`; once in review it shows running long, then
overdue at thresholds specific to the board type: `ProjectYellowMins`,
`ProjectRedMins`, `FinalYellowMins`, `FinalRedMins`. Each state has its own
clock, and its colors are the shared status palette (SPEC.md D-13), in
`eb-app.css`; status colors are not settings (D-19).

## SignUpGenius import

`SignUpGeniusPlugin` (Jackson for JSON) runs at startup when `-sugkey` is given
and no `-prereg` file was supplied. It makes two calls to
`api.signupgenius.com`: one to find the active signup whose title contains both
"eagle" and "board" and whose date range covers today, and one for that signup's
filled-slots report.

Entries whose `item` text contains "adult" become `AdultRecord`s merged into
`Master_AdultHistory.csv` **by email**: matched records get their phone
updated, and unmatched ones are added. Everything else becomes a pre-registered
`ScoutRecord` in `scouts_scheduled.csv`, without a phone number (D-8), which is
what powers email autofill at the check-in station.

Two behaviors that surprise people: the filter is by **calendar month**, not by
day, so two events in one month both import; and a duplicate email in the
adult history means that record is silently skipped entirely.

## Output of an event

A dated folder (`YYYY-MM-DD/`) holding `scouts.csv`, `adults.csv`, `rooms.csv`,
and `scouts_scheduled.csv`, plus a board-results spreadsheet. These contain
personal information about minors and are never committed; see
[SECURITY.md](SECURITY.md).

## Reading order for a newcomer

1. `EagleBoardScheduler.main()` and the constructor (the wiring), then
   `EventRoutes`, the list of everything the server answers
2. `eb-data.js`: the client/server contract in one screen
3. `scheduler.html` plus `scheduler_event.js`: the operator's workflow
4. One `process_*.js`: how a single lifecycle action travels end to end
5. `.github/workflows/build.yml`: what "correct" is defined as here
