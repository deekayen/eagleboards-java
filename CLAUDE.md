# CLAUDE.md — working notes for this repo

Guidance for anyone (human or AI) making changes here. Read this before editing.

**Read `SPEC.md` in `deekayen/eagleboards-shared` before changing the
operator screen, the check-in pages, the board rules or the data files.**
It is the source of truth for anything more than one version of Eagle
Boards does; this repo does not decide shared behavior on its own.

## What this is

An Eagle Scout Board of Review check-in and room scheduler for a Scouting
district. It was **reconstructed by decompiling an inherited binary** (no
original source was received) and then modernized. See `PROVENANCE.md`.

- Java app, single self-contained jar. `Main-Class: shkc.core.EagleBoardScheduler`.
- Embedded **Jetty 12** web server. One servlet (`WebServer$LocalDefaultHandler`)
  dispatches every request to app handlers registered in `EagleBoardScheduler`.
- Data is **CSV files** in a working directory (`-d`), read/written through the
  `DataRecordFile` / `DataRecord` machinery. Records: scouts, adults, rooms,
  adult history, and a single-row CONFIG.
- Browser UI is static files under `src/main/resources/shkc/core/WEBROOT`.
  `eb-data.js` is the adapter that speaks the server's endpoints. The
  operator's **Event page** (`scheduler.html` + `scheduler_event.js`, styled
  by `eb-app.css`) is plain DOM, laid out like the Windows version's Event
  page (SPEC.md O-3): youth queue, room cards and a details pane that builds
  the board, all visible at once. The Admin tables still use **Tabulator**
  (MIT), which replaced the original GPL dhtmlx; they really are
  spreadsheets. Admin, Settings and Help share `eb-app.css` and the same top
  bar.
- **The check-in pages are shared** (SPEC.md D-18): `index.html`,
  `youth_register.html`, `adult_register.html`, `checkin.css` and
  `checkin.js` in `WEBROOT/` are copies of `eagleboards-shared/checkin`,
  pinned by `checkin-pages.lock`. **Never edit them here**; CI fails if they
  differ from the pinned commit. Change them in the shared repo (WCAG 2.2 AA:
  run its `check-contrast.js` and an axe scan), then copy all five and update
  the lock. They call `CheckInApi` (`/api/*`), the API all three versions
  serve.
- **No birthdate** (SPEC.md D-7, O-5): nothing asks for, keeps, shows or
  exports one. `/register-youth` discards a `DOB` from an old cached page, and
  the `-cells` and `-autofill` endpoints blank one already on file rather than
  drop the column, so files still move between versions.
- **No youth phone number** (SPEC.md D-8): the same, for a youth's `Phone`,
  which the pre-registration imports no longer bring in either. Only the youth
  files (`isYouthFile`) withhold it; the adult files share those handlers and
  still serve an adult's number.
- **Nothing polls** (SPEC.md D-15). `/events` (`ChangeFeed`) streams a message
  after every POST a handler answers; the Event and Admin pages re-read then.
  Keep every change to the data a POST, and never a read: a GET that changed
  data would go unannounced. The check-in pages don't poll either: their lists
  load when the welcome page opens. `RefreshTimeSecs` stays in the config file
  for older builds but nothing reads it.
- Optional Swing popup (`PopupDialog`, only with `-w`) shows the check-in URL.
- Optional **SignUpGenius** import (`SignUpGeniusPlugin`, Jackson JSON).

## How a board night actually runs

Read this before reasoning about statuses. The code's names do not explain
the evening on their own, and it is easy to build a wrong model from them.

1. **RSVP.** Scouts reserve a slot on SignUpGenius. At startup the app
   imports those reservations (`SignUpGeniusPlugin`, into
   `scouts_scheduled.csv`) so that sign-in can pre-fill the scout's details.
2. **Sign-in.** A scout who RSVP'd gets a `P#` registration number; one who
   did not is a **walk-in** and gets `W#`. Walk-ins rank below every RSVP in
   the queue (`sort_regnum` in `scheduler_config.js`). Status `Registered`.
3. **Pick a scout, assign adults, Seat Board** → `Seated`. The board goes in
   ahead of the scout to preview the application, references and project
   workbook.
4. **Start Review** → `InProgress`. The scout is brought in.
5. **Complete** → `Completed`, with the **board's decision** as the Result:
   - `Approved`
   - `Adjourned`: the board met the scout and **postpones** its decision
     (not approved tonight, may come back).
   - `NotApproved`: denied.

**Postponed is not a board result.** It is the decision for a scout who
**never sees their board**: they arrived unprepared and are sent away. That
is the Postpone button, which works only on a `Registered` scout. Once a
board is seated it is refused, because from then on the board's decision is
the Result. A postponed scout is not waiting to come back that night, and a
postponed scout has no Result. The Admin page therefore offers `Postponed`
as a Status only, never as a Result, and `/complete-board` refuses it.

**A table never changes a scout's status** (SPEC.md P-6). The Admin page
shows Status read-only; it changes only through the Event page's steps,
which take and free a room and its members. `ScoutUpdateHandler.refusal`
also refuses Seated, InProgress and a sitting board's status through
`/youth-update`, whoever posts it (event test section 25): a scout set
Seated from a table had no room and no members. `/youth-update` still takes
Registered, Completed or Postponed on the record, which section 18's
correction moves; nothing on the Admin page offers them.

**An adult's facts are shared, and the history is read-only** (P-6). Name,
unit, contact and roles (`ADULT_REG_FIELDS`) edited on the Adults tab reach
the same adult in the adult history (`shareAdultFacts`), off the Undo stack;
the Adult history CSV tab is read-only and `/adult-history-update` refuses
edits (section 26). **Add adult…** and **Sign in for today** post to
`/register-adult`, the tablet's own sign-in, with the history record's ID
when filled in from it (section 27).

**Find a person** (SPEC.md D-21) sits under the Rooms heading, not over the
Youth list: `findPeople`, `roomsFound` and `personFindNote` in
`process_seat.js`, with the Windows and Mac test cases in
`test-seat-conflicts.js`. **Approved proposals** (D-22):
`/approved-proposals-cells` reads every dated folder beside the event's
(`_dataRoot`'s parent) dated before it, each time it is asked, never
creating a file there, and answers as `/youth-cells` does with only its
columns; the admin page's tab shows it read-only (event test section 28).

**Correcting a result.** Wrong result clicked: edit the Result on the Admin
page's **Boards** tab. A result recorded against the wrong scout (mistaken
identity): Undo on the Event page, straight away, since the Admin page no
longer changes a status. The record-level move -- the wrong scout back to
`Registered` with Result, Chair and Members cleared, the reviewed scout
given the result -- still works through `/youth-update`, and Seat Board
accepts a `Registered` scout whose Room is still `N/A` from the mistaken
Complete. Section 18 of `test-board-event.sh` covers both corrections and
checks that the Admin page offers no status to choose.

## Build, run, verify

- Build: `./mvnw package` → `target/eagleboardscheduler-*.jar` (JDK 25, targets 21).
- Run: `scripts/run.sh` (reads `SUG_KEY` from env or `.env`; drops the `-w`
  popup automatically when there's no display, e.g. a headless Pi).
- Board composition rules: `node scripts/test-seat-conflicts.js` (headless, no
  framework, no network). Runs in CI on all four platforms.
- Whole board event: `bash scripts/test-board-event.sh` — boots the jar on a
  spare port against a throwaway data dir and runs a full event at the
  district's real shape (14 scouts, 12 rooms, 30 adults, and only **5** adults
  qualified to chair anything, so boards queue behind the chairs). It covers
  what the pure-function tests cannot: adults committed to one room and released
  at Complete, postpone/reset handing the room and members back, and the
  composition rules holding for requests that never went through our UI. Needs
  `bash curl awk` and a JDK — **no Python**; see CONTRIBUTING "Running the shell
  scripts off Linux" for why. Runs in CI on all four platforms. Add a case
  here when you change how a board is seated, run or torn down.
  **Copy every new scenario to the other two versions:** the Windows port
  (`deekayen/eagleboards-windows`) runs this same script, and the Mac
  version (`deekayen/eagleboards-macos`) mirrors it in
  `BoardEventTests.swift`.

**Prefer pushing over re-running the suite locally.** `build.yml` already runs
the build, the structural check, both test scripts, the runtime smoke test, the
lifecycle check and `run.bat` across four platforms in about a minute.
Re-running those by hand proves nothing extra, and a local pass only describes
the working tree at the moment it ran, not what landed. Run things locally to
*debug* a failure CI has already found, or to iterate quickly — then let the
push be the verification.

## The golden rules

1. **CI is the acceptance gate. Keep it green on all four platforms**
   (`.github/workflows/build.yml`: amd64 Linux, amd64 Windows, arm64 Linux,
   macOS). It builds, runs both test scripts, exercises the endpoints and the
   board lifecycle, checks the Windows launcher, checks the live SignUpGenius
   API (Linux/push, `SUG_KEY` secret), and guards specific past bugs. **Add a
   regression assertion when you fix a crash**, and a case in
   `test-board-event.sh` when you change how a board is seated, run or torn
   down — a rule with no test is a rule that comes back.
2. **Never commit PII or secrets.** Participant data (all CSV/XLS, dated
   `YYYY-MM-DD/` folders, `Master_AdultHistory*`, `LOGIN_INFO*`) and the
   inherited jar are gitignored and blocked by `scripts/hooks/pre-commit`.
   Install the hook once per clone: `git config core.hooksPath scripts/hooks`.
   No CSV is committed; the only committed config is `config.properties`
   (timings, no PII). The rebuilt jar contains no API key — it takes one
   from `-sugkey`, sourced from `SUG_KEY` or an untracked `.env`. The inherited
   2019 binary is the exception: it hardcoded a key in a bundled
   `signup_genius_api.js`, which is why that file was dropped rather than
   carried over. Keep that jar off GitHub.
3. **Test in a sandbox, never against the live instance or real data.** Copy
   `config.properties` and use a *synthetic* header-only `Master_AdultHistory.csv`
   into a scratch dir; run on a spare port with `-d testdata`. Never load the
   real `Master_AdultHistory.csv` into anything you screenshot.

## Conventions / gotchas

- **Variable names say what the variable is for.** The decompiler emitted
  `var1`, `var10`… throughout; those were all renamed, and no `var##` remains
  anywhere in `src/main/java`. Do not reintroduce them, not even to match
  surrounding style — matching a decompiler is not a style. CI greps for them
  and fails the build (the "No decompiled variable names" step in `build.yml`).
- **Server endpoints are the contract.** The UI rework kept every endpoint and
  wire format frozen; prefer client-only changes. If you must change the
  server, add a case to `scripts/test-board-event.sh` covering it.
- **Handlers must not crash the server.** All request handling is wrapped in a
  `Throwable` safety net in `WebServer.service` that logs a stack trace and
  returns a clean 500 for that one request. Still, null-guard handler inputs
  (`getParameter` can be null; `new StringTokenizer(null,…)` throws).
- **Board lifecycle:** Registered → Seated → InProgress → Completed /
  Postponed (`Verified` survives on legacy records only; nothing sets it).
  Seating and starting are **two steps again**, after a period when they were
  merged into one:
  - **Seat Board** (`/seat-board`) → `Seated`. The members get the room and the
    paperwork — application, references, project workbook — and the scout is
    still outside. GTA 8.0.3.0 #8.
  - **Start Review** (`/inprogress-board`) → `InProgress`. The scout is brought
    in. Only now may the board be completed.

  They were merged on the view that the second step was redundant; separating
  them again is what lets the two phases be *timed apart*, which is the whole
  point. Room-card timers run on `MinsSinceLastUpdate`, so the clock restarts
  by itself at each transition — `ConveneRedMins` caps the convening phase
  (red only, no yellow: it is a limit, not a target), and
  `FinalYellowMins`/`FinalRedMins` then time the interview from Start Review.
  **Watch for code that assumed `Seated` was unreachable.** `/complete-board`
  accepted a `Seated` scout, which was harmless while nothing was ever left in
  that state and became "record a result for a review that never happened" the
  moment it was restored; there is now a CI guard for exactly that.
- **Changing a seated board's members** (`/change-board-members`) runs the
  same composition check as seating (`checkComposition`), except that adults
  already in the room may stay. It does not touch `LastUpdateTime`, so the
  room timer keeps running: it is the same board.
- **Renaming a room** from the Event page goes through `/rename-room`, which
  moves the youth and adults in it to the new name (the room keeps its ID).
  Editing the Room column on the Admin page does not, and strands a board in
  progress (event test section 13). A name of `N/A` or with a comma is
  refused, as in the Windows and Mac versions.
- **Board composition rules** live in `process_seat.js` for the operator's sake
  (it explains and, where allowed, offers an override) **and again in
  `SeatBoardHandler` as a hard backstop**. The UI is the normal way in, not the
  only one, and a board seated past it is one nobody finds out about until they
  read the result of a review that should not have happened. Both must agree:
  - **Size.** A board of review is three to six (GTA 8.0.0.3) — fewer refused,
    four to six asks to confirm, seven refused outright. A *project* proposal
    review is not a board of review (GTA 9.0.2.4) and this district runs it with
    two, under the same ceiling of six. `checkBoardSize` / `checkProjectSize`.
  - **Chair is binding.** A board must be chaired by someone whose role for that
    board type is `Chair`, and the chair must be sitting on the board. When the
    qualified chairs are all busy the answer is to promote someone on the Admin
    page — never to let a Member hold the gavel because the dropdown had nobody
    else. The details pane therefore offers the Chair mark only beside
    qualified chairs, and the fallback chair dialog lists only them.
  - **One board at a time.** An adult with a `Room` is committed to it and
    cannot be added to a second; `Room` = `N/A` is the Disable button's marker
    for someone who has gone home. Both are refused server-side, skipped by
    auto-select, and left out of Add members unless Show everyone is ticked,
    where their Add button is disabled and the room or "Gone home" is named.
  - **Same unit.** Adults from the scout's own unit raise an overridable warning
    naming every one of them: this council forbids them entirely, and the
    override falls back to the national rule (GTA 8.0.3.0 #2), which still
    requires at least one member from outside the unit — so a board made
    *entirely* of the scout's unit is refused with no override. Client-side
    only, deliberately: it is a judgement call, not an absolute.

- **Auto-select** (`proposeBoard` in `process_seat.js`) proposes the board
  when a waiting scout is selected, and **Fill the rest** (`fillBoard`, the
  Windows `SchedulerLogic.FillBoard`) completes it around the adults the
  operator picked, using the same ranking (`rankFreeAdults`). It weighs the whole waiting line, not
  just this scout: of every legal board, it takes the one that leaves the
  most other waiting scouts able to get a full board right now (chairs and
  troops both count), then the one using up the fewest chair qualifications
  (member-only adults in member seats, single-type chairs before
  either-type), then the one keeping the most flexible adults, then
  volunteers who came for any board (not linked to a scout, or Wood Badge),
  then the adults who have waited longest to volunteer since they were last
  free (`freeSinceTimes`: sign-in, or when their last board completed). It
  is only a proposal, not a rule, but the same algorithm and the same test
  cases live in the Windows and Mac versions; change all three together.
- **What an adult says at sign-in.** Each board type is Member, Chair or
  "No thanks" (stored as the role `Unavailable`, which Seat Board refuses).
  Two per-night columns end the adult record: `WoodBadge` (`Y` or blank)
  and `Supporting` (IDs of the scouts they came with, `|`-separated, since
  commas become `~` on disk). Neither is copied into the adult history, so
  next month's form never pre-fills them. Start Review names the supporting
  adults and the room they are in, so someone can fetch them to introduce
  the scout; Locate lists them first. An operator links or unlinks them
  after both have signed in with **Link an adult** in the details pane (or
  an adult's right-click menu), which writes the same column.

  Age is attested by the "I am 21+" button on the sign-in page, and the
  parent/relative rule is handled by unit matching, so neither needs a field on
  `AdultRecord`. Keep the pure rules pure and tested (`test-seat-conflicts.js`)
  and the server's behavior tested too (`test-board-event.sh`).
- **Config lives in `config.properties`** (JDK `java.util.Properties`,
  `key=value`, `#` comments) as one CONFIG record, loaded into `ConfigRecord`
  (columns must be listed in `ConfigRecord.COLUMNS` to be served via
  `/config-autofill`) and edited via the Settings page (`configure.html`).
  The format is chosen by file extension inside `DataRecordFile.load`/`store`
  (`.properties` → key=value, else CSV — the CSV path is kept so the original
  binary still loads). Room-card warning timers are
  board-type specific: `ProjectYellowMins` / `ProjectRedMins` /
  `FinalYellowMins` / `FinalRedMins`, minutes since seating. Note: saving via
  the Settings page rewrites the file and does not preserve `#` comments.
- **Branding is district-neutral** — never reintroduce "Etowah" or a specific
  district/council name. This is the settled end state, not a waypoint:
  configurable branding was considered and declined (issue #1, closed not
  planned), so the app never displays whose district it is.

## Workflow

- Branch is `main`, tracks `origin/main`. Commits are made and pushed from the
  working directory; there's usually nothing to `git pull` unless a Dependabot
  PR was merged on GitHub.
- Follow-up work and decisions are tracked as GitHub issues on
  `deekayen/eagleboards-java`.
- **No AI attribution** in commits, issues, PR text, or anywhere in history,
  as in every Eagle Boards repository.
