# What's changed since the original

This is a cumulative summary of how the app differs from the **inherited 2019
binary** (`EagleBoardScheduler_20190618.jar`) that this project was rebuilt
from — see `PROVENANCE.md` for how that reconstruction was done.

It is deliberately **not** a release-to-release changelog. Everything below is
phrased as "the original did X, it now does Y", so anyone who knew the old
program can read this once and understand the whole delta. Entries are grouped
by what they affect, not by when they landed.

Releases are versioned by date (e.g. `2026.07.31`). For commit-level detail,
`git log` is the record.

---

## At a glance

- The board of review workflow lost the **Verify** gate, and the two stages
  that remain are now timed apart: seating convenes the board, and a second
  click brings the youth in.
- **Seating a board is now checked against the Guide to Advancement** for size
  and for members from the youth's own unit; the original accepted whatever the
  operator picked.
- The entire browser interface was rebuilt, because the toolkit it was built on
  was **GPL-licensed and unmaintained**.
- Every screen now **refreshes itself**; the old one only updated when someone
  pressed a button.
- "Scout" is now **"Youth"** throughout the interface. The event is still
  **"Eagle Boards"**, as it always was.
- Every bundled library was **replaced or upgraded** — the original shipped a
  2013 web server and a 2012 JSON parser.
- A **live API key** that was baked into the original jar is gone.

---

## The board of review workflow

**The lifecycle is shorter.** It was:

```
Registered → Verified → Seated → InProgress → Completed / Postponed
```

It is now:

```
Registered → Seated → InProgress → Completed / Postponed
```

- **Verify is gone.** It was a paperwork gate: the operator ticked boxes and the
  server recorded the youth as Verified. Paperwork is checked off-screen, so a
  registered youth is now seated directly. The Verify button, its dialog, and
  the `/verify-board` endpoint were all removed.
- **Seat and Start still mean different things.** *Seated* is the convening
  phase: the members have the room and the paperwork — application, references,
  project workbook — and the youth is still outside, per Guide to Advancement
  8.0.3.0 #8. **Start Review** is what brings the youth in and moves the board
  to *InProgress*. An interim version of this program merged the two, on the
  view that the second click was redundant. Keeping them separate is what lets
  the convening phase and the interview be timed apart, which is the point of
  the room-card clocks below.
- **A board cannot be completed while it is still convening.** `/complete-board`
  refuses a *Seated* youth, so no result can be recorded for a review that never
  started.
- **Old records still work.** Anything left in a `Verified` state by an earlier
  run is still seatable rather than stranded. Nothing sets that state any more.

**Seating now checks who is on the board.** The original accepted any set of
members the operator picked. Seating is now checked against the Guide to
Advancement before it goes through:

- **Board size** must be three to six members, per GTA 8.0.0.3. Fewer than three
  is refused. Four to six is legal and asks for confirmation first. Seven is
  refused outright.
- **Adults from the youth's own unit** raise a warning that names every one of
  them, because this council forbids them entirely. The warning can be
  overridden, which falls back to the national rule, GTA 8.0.3.0 #2: at least
  one member must come from outside the unit. A board made up *entirely* of the
  youth's own unit is therefore refused with no override available.
- **A project proposal review** is not a board of review (GTA 9.0.2.4), so the
  three-member floor does not apply to it — this district runs them with two,
  under the same ceiling of six.
- **The chair must be qualified to chair.** A board is chaired by someone whose
  role for that board type is Chair, and the chair sits on the board. The
  original let any selected member be named chair; so did this rebuild until the
  chair dropdown was narrowed to qualified chairs only. When they are all busy
  the answer is to promote someone on the Admin page, not to hand a Member the
  gavel because nobody else was left in the list.
- **An adult sits on one board at a time.** Anyone already in a room, or stood
  down for the night with Disable, is refused, hidden from the adult grid's
  default view, skipped by auto-select, and has their checkbox greyed out with
  the reason on hover. They return to the pool when the review completes.
- Age is attested by the **"I am 21+"** button on the sign-in page, and the
  parent/relative rule is covered by the unit match, so neither needs a field on
  the adult record.

The size and unit rules are pure functions with no server round trip, covered by
`scripts/test-seat-conflicts.js`. Size, chair qualification and one-board-at-a-
time are **also enforced by the server**, because the browser is the normal way
in and not the only one: a board seated past the UI is one nobody finds out
about until they read the result of a review that should not have happened.
`scripts/test-board-event.sh` runs a whole event against those rules. Both
run in CI on all three platforms.

**Other workflow changes**

- Board results record **"Adjourned"** where they used to say "Suspended".
  Archived spreadsheets keep the old word; nothing is rewritten.
- The Complete dialog no longer asks for **Project Cost, BSA Hours, or Other
  Hours**, and the "Collect Statistics" startup reminder is gone. (The server
  still accepts them if sent, so the fields could come back without a code
  change.)
- The **Completion Notes** box starts empty instead of pre-filled with
  placeholder text.
- **Room warning timers are per board type, and each phase is timed on its
  own.** The clock runs on the record's last update, so it restarts by itself at
  every transition. Convening is capped by `ConveneRedMins`, 30 minutes, which
  goes straight to overdue with no running-long stage because it is a limit
  rather than a target. From **Start Review** the interview is paced by its own
  pair: Project runs long at 25 minutes and is overdue at 40; Final runs long
  at 30 and is overdue at 45. All five are editable in Settings. The original
  had a single pair of alert/reminder values shared by every board.
- **The timers read with color blindness.** Each state has its own clock: a
  stopwatch on time, a timer clock on an orange tint running long, an alarm
  clock on a solid pink-red fill overdue. The original told statuses apart by
  row color alone, and its pink (registered) and green (in progress) rows lose
  most of their difference to the most common color blindness.

---

## The interface

**Rebuilt on a different toolkit.** The original UI was built on **dhtmlxSuite
4.1.2**, which is GPL-licensed and no longer maintained. Every page was rewritten
on **Tabulator 6.5.2** (MIT). This was a front-end-only rework — the server, its
endpoints, and its wire formats were held frozen so the behaviour could be proven
unchanged.

**It now looks like a Scouting app.** The inherited pale-blue-and-grey was
replaced with the Scouts BSA sub-brand palette (tan, gray, olive, with Scouting
Red for actions). Colour carries meaning: olive for primary actions, white for
navigation, red for destructive, blue for neutral changes. Every text/background
pairing was measured against WCAG 2.2 contrast rather than picked by eye.

**The sign-in forms were redesigned.** Both registration pages were two columns
with right-aligned labels; they are now a single column with labels above the
fields, and field width hints at the expected input length instead of every box
being the same size. Controls also got bigger: the checkbox the operator hits for
every board was 13×13 pixels, below the 24×24 accessibility minimum, and
disabled buttons were too faint to read.

**Screens fit the screen.** Both the scheduler and the admin page used to run off
the bottom and had to be scrolled during an event. Each is now a
viewport-height layout where the grids scroll internally and the page itself
does not. On the scheduler, Youth and Adult Board Members sit side by side in a
top row, as they did in the original.

**Screens refresh themselves.** New registrations used to appear only when
someone pressed Refresh. The check-in page, the admin page, and the scheduler
now all poll on the existing `RefreshTimeSecs` setting, so one setting drives
every screen. The check-in page repaints rather than appending, and only
auto-scrolls if the viewer was already at the bottom. Background failures are
silent on the check-in screen, which faces the youth signing in.

**Smaller interface fixes**

- **Chosen board members survive clicking around.** Selecting a youth used to
  wipe every adult checkbox, throwing away a board the operator had assembled by
  hand. Only the Adult panel's Clear button empties them now.
- **Seating a board no longer leaves its members checked.** The server only
  ever set `Room` on a seated adult, not `Sel` — so their checkbox stayed on
  indefinitely, which made auto-select skip picking anyone new for the next
  youth (it treats any existing check as "the operator already chose") and let
  a fast click to the next youth briefly re-offer adults who were just seated,
  before the delayed refresh caught up. The client now clears the checkbox and
  marks the room locally the moment seating succeeds.
- **The periodic poll no longer interrupts hand-picking board members.** It
  used to fully replace the Adult grid's data every `RefreshTimeSecs`, which
  reset the scroll position and could occasionally overwrite a checkbox click
  whose save hadn't reached the server yet. The poll now preserves scroll
  position and defers to any checkbox change still in flight.
- **Room cards show adults by full first name, not an initial.** `Leaders`
  used to read like "J. Smith, K. Doe"; matching a name someone gives you
  verbally to that list meant already knowing their last name. It now reads
  "Jordan Smith, Kelly Doe".
- The **B/S column** (adult:scout ratio) was removed from the youth grid and
  from the report export — it was computed and stored but not used.
- The six **XML export buttons** were removed; CSV export is unchanged. The XML
  format was the old toolkit's internal grid format and nothing consumed it.
- The startup **URL popup** (`-w`) centres its text and adds a clickable link
  straight to that host's scheduler page.

---

## Names and wording

- **"Scout" → "Youth"** across the interface: the admin tabs, the Rooms grid
  heading, the sign-in page (now at `/youth_register`), and the downloaded file
  name (`Youth.csv`, previously `Scouts.csv`).
- **Six endpoints were renamed** to match — this is the one place the frozen
  server contract was deliberately broken:

  | Original | Now |
  |---|---|
  | `/scout-cells` | `/youth-cells` |
  | `/scouts-scheduled-cells` | `/youth-scheduled-cells` |
  | `/scout-update` | `/youth-update` |
  | `/scouts-scheduled-update` | `/youth-scheduled-update` |
  | `/scout-autofill` | `/youth-autofill` |
  | `/register-scout` | `/register-youth` |

  The old names now return 404 rather than quietly continuing to work, so a
  stale bookmark fails loudly instead of half-working.

- **The event is still called "Eagle Boards".** For a while the pages said
  "Review Board" instead, on the reasoning that boards of review exist for every
  rank; that was reverted, because nobody involved calls the evening anything
  but Eagle Boards and the generic wording only read as odd. The sign-in page
  says "Welcome to Eagle Boards", the admin page is the "Eagle Board Admin
  Page", and the scheduler is the "Eagle Board Scheduler". The **board types**
  read **"Final Board"** and **"Proposal Review"**, though the values stored on
  disk stay `Final` and `Project` — they key room assignments and the
  board-type timers, so the labels are a display mapping only.
- **District branding was removed.** Pages are district-neutral so any district
  can run the app. Export filenames are generic too (`Report.csv`, `Youth.csv`,
  `Adults.csv`, `Rooms.csv`, `AdultHistory.csv`) rather than district-prefixed.
  Making the name configurable instead of absent was considered and declined —
  the app deliberately never displays whose district it is.
- **Unit types were updated for programs that no longer exist.** *Team*
  (Varsity Scouting, discontinued) is no longer offered; *Post* (Exploring) was
  added. Youth sign-in offers Troop, Post, Crew, and Ship; adult sign-in adds
  Pack, District, Council, and Community. Existing historical records with
  retired unit types still display.
- **Unit labels are spelled out.** The original abbreviated a unit to the first
  letter of its type plus its number, so Troop 1776 became `T1776`. That
  collided — Pack and Post both gave `P`, and the new Council/Community/Crew
  options would all have given `C` — so the whole word is written now
  (`Troop1776`).

---

## Settings and configuration

- **Config moved from CSV to `config.properties`** — a plain `key=value` file
  that supports `#` comments for hand-editing, which is the normal Java choice
  for flat configuration. The CSV reader is retained so the original binary and
  the parity gate still work. Note that saving from the Settings page rewrites
  the file and does not preserve comments.
- The old shared alert/reminder fields were replaced by the four board-type
  timers described above.
- **Status colors are no longer settings.** The original's twelve row colors
  (`RegisteredColor` through `PostponedHiColor`) are gone from the file; every
  version of Eagle Boards now draws status from one shared palette, checked for
  contrast and color blindness in light and dark. An older file that still has
  them loads, and saving drops them.
- **Fixed:** saving settings appended a duplicate record each time instead of
  updating the existing one.

---

## Running the app

- **New `-bind <ip-prefix>` option.** A machine with Hyper-V or WSL adapters
  would pop up one window per interface and listen on all of them. `-bind`
  restricts both the advertised URLs and the listening socket to the interface
  whose IPv4 address starts with the given prefix (e.g. `192.168.`). A prefix
  rather than a fixed address, so DHCP moving the host within the subnet still
  works; `127.0.0.1` stays reachable either way. Without `-bind`, behaviour is
  exactly what it was.
- The default config file is `config.properties` (was `config.csv`).
- `scripts/run.sh` and `run.bat` wrap startup and drop the `-w` popup
  automatically when there is no display, e.g. a headless Pi.
- `scripts/diagnose.sh` reports which of five known causes is behind a browser
  showing stale pages, and lists every running instance.
- Tagged builds are published to GitHub Releases.

---

## Under the hood

### Libraries

| Component | Original (2019 jar) | Now |
|---|---|---|
| Web server | Jetty 8.1.11 (2013, end-of-life) | Jetty 12.1.11 |
| Servlet API | `javax.servlet` 3.0 | `jakarta.servlet` (Jetty EE11) |
| Browser UI toolkit | dhtmlxSuite 4.1.2 (GPL, unmaintained) | Tabulator 6.5.2 (MIT) |
| JSON parsing | json-simple 1.1, vendored and repackaged as `shkc.json.simple` | Jackson Databind 2.22.1 |
| Logging | `monfox.log`, a vendored ~2010 library | `java.util.logging` (JDK built-in) |
| Bytecode | Java 7 (class major version 51) | targets Java 21, built on JDK 25 |
| Build | none — only the compiled jar was received | Maven, single self-contained jar |

Dependency updates are now proposed automatically every week, and the build is
tested on 64-bit Linux, Windows, and ARM Linux.

### Reliability

- **A failing request can no longer take down the web service.** All request
  handling is wrapped so an unexpected error is logged and returns a clean 500
  for that one request, instead of leaving every subsequent page load broken.
- **Fixed a crash when seating a board with no members selected** — a
  null-handling bug present in the original binary. It now rejects the request
  with a clear message.
- **The SignUpGenius import finds a sign-up on its first day.** The original
  compared today's date with the sign-up's start date and time, so on the
  day a sign-up began, today counted as before it and the import found
  nothing: January's board night, for a sign-up that runs the year. Only the
  dates are compared now.

### Readable code

**Every variable now has a name that says what it holds.** The sources were
recovered by decompiling the inherited jar, so almost every local was called
`var1`, `var7`, `var23`. All 2,890 of them were renamed. The build now fails if
any come back.

### Dead code removed

`ConfigWindow` and `NetTest` (unused classes), `old_saved_script.js`,
`index_simple.html` (a second sign-in page with no lists), and the scripts
behind the removed Verify step. Roughly 928 files of the old UI
toolkit — about 3 MB — went with it.

A later audit walked the whole tree looking for anything nothing referenced,
and removed it:

- **105 of the 106 bundled icons** (~560 KB). They were toolbar art for the
  dhtmlx interface that is gone; only the overdue-badge warning icon on the
  room cards is still used. `NegaScheduler.png`, `ScoutButton.png` and
  `LeaderButton.png` went too — no page ever loaded them, and the first
  carried the old district's branding.
- **The `AdultScoutRatio` leftovers.** The "B/S" column was retired earlier,
  but the code that computed it survived, along with its accessors.
- **Two callback hooks that could never fire**: a pluggable resource resolver
  (nothing ever registered one) and a per-field import filter (both importers
  passed `null`), together with the loops that consulted them.
- **`NameUtil`'s variable map.** `${...}` expansion in a path could substitute
  caller-supplied variables, except nothing ever set one, so it always fell
  through to the system properties — which is exactly what it still does.
- **The unused half of `FileLocator`** — the file and directory lookups. What
  the app actually calls, resource loading along a search path, is untouched.
- Unreferenced record accessors, a command-line test harness, empty
  initialisation stubs in the browser code, and a handful of write-only local
  variables.

Nothing here changed behavior: every item had zero references anywhere in the
repository. The parity gate was taught about each deletion individually rather
than being pointed away from whole classes, so everything that remains is
still compared against the original.

---

## Security and privacy

- **A live SignUpGenius API key was embedded in the original jar**, in
  `signup_genius_api.js`. That file is not carried over at all: nothing used it
  at runtime, since the server takes the key from the `-sugkey` option. The
  build checks both that the file stays absent and that the original's key
  appears nowhere in the rebuilt jar.
- **The original jar is never committed**, because it contains that key. It is
  kept offline and authenticated by checksum.
- **Participant data cannot be committed by accident.** All CSV/XLS files, dated
  folders, and adult-history files are ignored by git and blocked by a
  pre-commit hook. The only configuration committed is `config.properties`,
  which holds timings and no personal data.

### Licensing

**GPL-2.0 → Apache-2.0.** The original choice was forced by the bundled GPL
dhtmlx toolkit; removing that toolkit in favour of MIT-licensed Tabulator
removed the constraint. GPL-2.0 had also become a poor fit, since Apache-2.0
(Jackson's license, shaded into the distributed jar) is generally treated as
incompatible with it. Apache-2.0 additionally covers inbound contributions,
grants patent rights explicitly, and disclaims trademarks. A `NOTICE` file was
added to carry attribution.

---

## What deliberately did *not* change

This matters as much as the list above. Except for the six renamed youth
endpoints, the server's **endpoints and wire formats are frozen**, and so are
the **CSV data files** on disk. Field names that appear in the wire format or in
`rooms.csv` kept their original spelling even where the visible column heading
changed. The XML export format is still supported server-side even though the
buttons are gone, so it can be restored if a downstream tool ever needs it.

## How this is verified

The owner of this project does not review Java, so correctness is established
mechanically rather than by code review. `scripts/verify-parity.sh` boots the
original inherited jar and the rebuilt jar side by side and proves they behave
identically — class structure, served bytes, endpoint responses, the data files
they write, and their startup logs.

Every intentional difference listed on this page is registered in that script
with a comment explaining why, so the gate stays green and any *unintended*
change shows up immediately. See `CLAUDE.md` for the rules contributors follow.
