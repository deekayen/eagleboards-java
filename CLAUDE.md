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
- **Parity gate: `scripts/verify-parity.sh` — run it after every change and keep
  it green.** It boots the original inherited jar and the rebuilt jar side by
  side and proves they behave identically for everything that wasn't
  intentionally changed (class signatures, served bytes, endpoint responses,
  data files written, startup logs). The owner cannot review Java, so this
  mechanical check — not code review — is the acceptance gate.

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
- **Board lifecycle:** Registered → Verified → (Seat) → InProgress → Completed /
  Postponed. "Seat" and "Start" were merged — seating goes straight to
  InProgress; there is no separate Seated state or Start button anymore.
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
  district/council name; that's tracked as configurable-branding work.

## Workflow

- Branch is `main`, tracks `origin/main`. Commits are made and pushed from the
  working directory; there's usually nothing to `git pull` unless a Dependabot
  PR was merged on GitHub.
- Follow-up work and decisions are tracked as GitHub issues on
  `deekayen/eagleboards`. The repo is **private** until a rights review with the
  original author and a final license decision — see the pre-public checklist
  issue. The rights question is that the app was reconstructed by decompiling a
  binary whose author reserved all rights; no Monfox LLC copyright notice
  remains in this tree (the vendored `monfox/log` library was replaced by
  `java.util.logging`, and the one file that carried the notice was unused and
  has been deleted).
