# CLAUDE.md — working notes for this repo

Guidance for anyone (human or AI) making changes here. Read this before editing.

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
- Browser UI is static files under `src/main/resources/shkc/core/WEBROOT`,
  built on **Tabulator** (MIT) after the original GPL dhtmlx was removed.
  `eb-data.js` is the adapter that speaks the server's endpoints.
- Optional Swing popup (`PopupDialog`, only with `-w`) shows the check-in URL.
- Optional **SignUpGenius** import (`SignUpGeniusPlugin`, Jackson JSON).

## Build, run, verify

- Build: `./mvnw package` → `target/eagleboardscheduler-*.jar` (JDK 25, targets 21).
- Run: `scripts/run.sh` (reads `SUG_KEY` from env or `.env`; drops the `-w`
  popup automatically when there's no display, e.g. a headless Pi).
- Board composition rules: `node scripts/test-seat-conflicts.js` (headless, no
  framework, no network). Runs in CI on all three platforms.
- Whole board evening: `bash scripts/test-board-evening.sh` — boots the jar on a
  spare port against a throwaway data dir and runs a full evening at the
  district's real shape (14 scouts, 12 rooms, 30 adults, and only **5** adults
  qualified to chair anything, so boards queue behind the chairs). It covers
  what the pure-function tests cannot: adults committed to one room and released
  at Complete, postpone/reset handing the room and members back, and the
  composition rules holding for requests that never went through our UI. Needs
  `bash curl awk` and a JDK — **no Python**, for the reason in the parity note
  below. Runs in CI on all three platforms. Add a case here when you change how
  a board is seated, run, or torn down.
- **Parity gate: `scripts/verify-parity.sh` — run it after every change and keep
  it green.** It boots the original inherited jar and the rebuilt jar side by
  side and proves they behave identically for everything that wasn't
  intentionally changed (class signatures, served bytes, endpoint responses,
  data files written, startup logs). The owner cannot review Java, so this
  mechanical check — not code review — is the acceptance gate.
  `scripts/verify-parity.sh --check` runs just the preflight (tools + inputs),
  which is how you qualify a new machine. It runs on Linux, macOS and Git-for-
  Windows; it needs `bash unzip curl awk diff cmp` and a JDK, and **no Python**
  — see CONTRIBUTING "Running it off Linux" before touching any shell idiom in
  it, because portability bugs in this script surface as a false *pass*.
  **CI runs it too**, in the `parity` job, on Linux and macOS. The inherited jar
  can't be committed, so that job downloads it from the private
  `parity-reference` prerelease and checks its SHA-256 first — see PROVENANCE.md,
  including what has to happen to that release before the repo is ever public.

**Prefer pushing over re-running the suite locally.** `build.yml` already runs
the build, the structural check, both test scripts, the runtime smoke test, the
lifecycle check and `run.bat` across four platforms, and `parity` runs the gate
— all in about a minute. Re-running those by hand proves nothing extra, and a
local pass only describes the working tree at the moment it ran, not what
landed. Run things locally to *debug* a failure CI has already found, or to
iterate quickly — then let the push be the verification.

## The golden rules

1. **Keep the parity gate passing.** When you intentionally diverge from the
   original binary's behavior, don't just let parity fail — teach the script
   that the divergence is deliberate (an exemption or a normalization) with a
   comment saying why. Existing examples in `verify-parity.sh`: Jetty 8→12
   (`MIGRATED`), Jackson/JUL swaps (`REMOVED_VENDORED`), de-branding, the
   config-schema change, `PopupDialog` enhancements.
2. **Editing a method body doesn't change a class's javap signature; adding or
   removing a field/method does.** The parity structural check compares
   declared members. Add null-guards freely; removing a member means teaching
   the gate about it. Prefer a targeted filter in `sig()` (which keeps the rest
   of that class compared) over adding the class to an exemption list, since an
   exemption skips every signature in it. Worked example: the 58 unused
   column/value constants removed from the record classes.
3. **Never commit PII or secrets.** Participant data (all CSV/XLS, dated
   `YYYY-MM-DD/` folders, `Master_AdultHistory*`, `LOGIN_INFO*`) and the
   inherited jar (embeds an API key) are gitignored and blocked by
   `scripts/hooks/pre-commit`. Install the hook once per clone:
   `git config core.hooksPath scripts/hooks`. No CSV is committed; the only
   committed config is `config.properties` (colors/timings, no PII).
4. **Test in a sandbox, never against the live instance or real data.** Copy
   `config.properties` and use a *synthetic* header-only `Master_AdultHistory.csv`
   into a scratch dir; run on a spare port with `-d testdata`. Never load the
   real `Master_AdultHistory.csv` into anything you screenshot.
5. **CI must stay green on all three platforms** (`.github/workflows/build.yml`:
   amd64 Linux, amd64 Windows, arm64 Linux). It builds, runs a runtime smoke
   test, checks the live SignUpGenius API (Linux/push, `SUG_KEY` secret), and
   guards specific past bugs. Add a regression assertion when you fix a crash.

## Conventions / gotchas

- **Decompiled variable names** (`var1`, `var10`…) are everywhere. Keep edits
  minimal and in the same style; don't do sweeping renames (they widen the
  parity diff and add risk for no functional gain).
- **Server endpoints are the contract.** The UI rework kept every endpoint and
  wire format frozen; prefer client-only changes. If you must change the
  server, check whether `verify-parity.sh` exercises that path.
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
    else. The chair dialog therefore lists only qualified chairs.
  - **One board at a time.** An adult with a `Room` is committed to it and
    cannot be added to a second; `Room` = `N/A` is the Disable button's marker
    for someone who has gone home. Both are refused server-side, hidden by the
    grid's default filter, skipped by auto-select, and their checkbox is
    rendered `disabled` with the reason in its tooltip.
  - **Same unit.** Adults from the scout's own unit raise an overridable warning
    naming every one of them: this council forbids them entirely, and the
    override falls back to the national rule (GTA 8.0.3.0 #2), which still
    requires at least one member from outside the unit — so a board made
    *entirely* of the scout's unit is refused with no override. Client-side
    only, deliberately: it is a judgement call, not an absolute.

  Age is attested by the "I am 21+" button on the sign-in page, and the
  parent/relative rule is handled by unit matching, so neither needs a field on
  `AdultRecord`. Keep the pure rules pure and tested (`test-seat-conflicts.js`)
  and the server's behavior tested too (`test-board-evening.sh`).
- **Config lives in `config.properties`** (JDK `java.util.Properties`,
  `key=value`, `#` comments) as one CONFIG record, loaded into `ConfigRecord`
  (columns must be listed in `ConfigRecord.COLUMNS` to be served via
  `/config-autofill`) and edited via the Settings page (`configure.html`).
  The format is chosen by file extension inside `DataRecordFile.load`/`store`
  (`.properties` → key=value, else CSV — the CSV path is kept so the original
  binary and the parity gate still work). Room-card warning timers are
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
  `deekayen/eagleboards`.
