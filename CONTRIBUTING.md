# Contributing

Thanks for helping with the Eagle Board Scheduler. This page covers everything
needed to get productive: setup, build, test, house style, and how changes are
reviewed. Read [ARCHITECTURE.md](ARCHITECTURE.md) first if you want the map of
how the app fits together, and [PROVENANCE.md](PROVENANCE.md) for where the code
came from — it explains why parts of it look the way they do.

## The one unusual thing about this project

The source was **reconstructed by decompiling an inherited binary**. No original
source was ever received. That single fact drives most of the conventions
below: the odd variable names and the reluctance to reformat.

The maintainer does not read Java, so **CI is what proves a change is safe.**
`.github/workflows/build.yml` builds on four platforms, runs both test scripts,
exercises the endpoints and the whole board lifecycle, and guards specific bugs
that have bitten before. Keep it green, and add a case when you change
behavior — a rule with no test is a rule that comes back.

## Prerequisites

| Need | Version | Notes |
| --- | --- | --- |
| JDK | 21 or newer | CI builds on Temurin **25**; bytecode targets 21 |
| Maven | none | use the bundled wrapper `./mvnw` (`mvnw.cmd` on Windows) |
| Git | any recent | |

The test scripts also need `bash`, `curl` and `awk` — all of which ship with
macOS, Linux, and Git for Windows. They need no Python, deliberately: there is
no single interpreter name that works on all three (Debian and Raspberry Pi OS
have `python3` and no bare `python`; a Windows box may have the reverse, with
`python3` resolving to a Microsoft Store stub that exits without reading its
input). A silently empty filter turns a test into a rubber stamp, so these
stick to `awk`.

### Supported platforms

CI builds and runs a smoke test on every push and pull request across:

- **Linux amd64** (`ubuntu-latest`)
- **Windows amd64** (`windows-latest`)
- **Linux arm64** (`ubuntu-24.04-arm`) — the Raspberry Pi deployment target
- **macOS** (`macos-latest`) — a development platform, not a deployment target

macOS was once left out because those runners bill at 10× the minute rate on a
private repository and were judged to prove nothing the other legs did not. It
is a leg now because the repo grew shell test scripts and macOS is the only
BSD-userland platform in the matrix — where `sed`, `awk`, `stat` and `seq` all
differ from GNU, and where a portability bug would otherwise show up as a
false PASS rather than a failure. If the billing outweighs that, this is the
line to delete.

The app runs anywhere with a JDK 21+. In practice it is deployed on a Windows
admin laptop or a Raspberry Pi at the event venue. The optional Swing popup
(`-w`) is skipped automatically when there is no display, so a headless Pi
works.

## First-time setup

```sh
git clone https://github.com/deekayen/eagleboards.git
cd eagleboards
git config core.hooksPath scripts/hooks   # REQUIRED - see "Never commit data"
```

That hook install is per clone and is not optional. It is the thing standing
between a routine commit and publishing a minor's personal information.

## Build

```sh
./mvnw package          # -> target/eagleboardscheduler-*.jar (self-contained)
```

**Use `./mvnw clean package` after deleting or renaming a resource.** Plain
`package` does not remove stale files from `target/classes`, so a deleted
WEBROOT file keeps getting packaged into the jar and keeps being served. This
has bitten us for real.

### "I rebuilt, but I'm still looking at the old app"

```sh
scripts/diagnose.sh          # checks every cause below and names the culprit
```

Run that first. There are several independent causes, all of them silent — the
app looks entirely normal, just out of date — and guessing between them by hand
wastes an afternoon. The reliable age test needs no version number: the browser
UI was rebuilt from dhtmlx onto Tabulator, so `dhtmlx` anywhere in the served
scheduler page means an old build, as does a **Verify** button (that step was
removed).

The causes, in the order the script checks them:

1. **Stale resources**, as above. Renaming `scout_register.html` to
   `youth_register.html` leaves the old file in `target/classes`, and a plain
   `package` ships both. Check with:

   ```sh
   unzip -l target/eagleboardscheduler-*.jar | grep -E 'scout_register|index_simple|signup_genius_api|process_verify'
   ```

   Anything listed means the jar is stale — those files were all deleted or
   renamed. After a clean build it prints nothing.

2. **A `WEBROOT/` folder on disk shadowing the jar.** This one survives any
   amount of rebuilding, so check it first if a clean build changed nothing.
   `WebServer.sendResponseFile` tries `new File("WEBROOT", name)` *before* the
   classpath, and that path is relative to the working directory — the repo
   root, since `run.sh` cds there. So a stray `WEBROOT/` serves the whole UI
   and the jar is never consulted. The original app was deployed as a jar
   beside such a folder, so an inherited one is easy to end up with.

   The verbose log tells you which source answered:

   ```
   looking for: dir=WEBROOT, fname=/index.html
   sendResponseFile:/index.html                       <- served from DISK
   ```
   ```
   looking for: dir=WEBROOT, fname=/index.html
   [1] not in filesystem, checking classpath ...      <- served from the JAR
   sendResponseFile:/index.html
   ```

   `run.sh` and `scripts/run.bat` both warn when they see one; nothing else
   does, so a stray `WEBROOT/` can leave you staring at pages from 2019 while
   every rebuild succeeds. The filesystem-first order is inherited behaviour and
   is deliberately left alone; it is how an operator patches a page at an event
   without a toolchain.

3. **An older jar being picked.** `target/` accumulates one jar per version,
   because `package` never removes the previous one. The version is a *date*,
   so `ls target/eagleboardscheduler-*.jar | head -1` selects the **oldest**
   build — alphabetically first. `scripts/run.sh` names the jar it picked and
   `scripts/run.bat` sorts by timestamp; if you write a new script
   that reaches into `target/`, sort by time, never by name. `ls -la
   target/*.jar` shows what is actually there.

4. **A forgotten server still holding the port.** `run.sh` hardcodes port 8080,
   and the app **exits** when it cannot bind:

   ```
   ERROR: Failed to bind to 0.0.0.0/0.0.0.0:8080
   ```

   So an instance left running from an earlier session keeps serving while
   every restart you launch dies on startup. Nothing in the browser changes,
   which makes it the most convincing of these — it survives a clean build, a
   fresh jar, and a correct checkout, because none of them are what is
   answering. Find and end it:

   ```sh
   lsof -nP -iTCP:8080 -sTCP:LISTEN     # macOS / Linux
   kill <pid>
   ```

5. **The browser's own cache.** These are plain static pages. If everything
   above is clean and the tab still looks old, hard-reload (Cmd-Shift-R) or
   open a private window.

`./mvnw clean package` resolves causes 1 and 3, and is the only way to be sure
`target/` holds exactly what the source tree says. It cannot help with 2, 4 or
5 — which is why `diagnose.sh` checks all of them.

## Run it locally

Never point a development build at live event data. Use a scratch directory,
a spare port, and a **synthetic** header-only adult file:

```sh
printf 'Type,ID,Last,First,Email,Phone,UnitType,Unit,UnitName,ProjectReview,FinalBoard,RegTime,Room,Flags,Sel,BoardHistory\n' > /tmp/eb/Master_AdultHistory.csv
cp config.properties /tmp/eb/
java -jar target/eagleboardscheduler-*.jar \
  -verbose -a /tmp/eb/Master_AdultHistory.csv -c config.properties \
  -port 18080 -d /tmp/eb/data
```

Then open `http://127.0.0.1:18080/` (check-in), `/admin`, or `/scheduler`.
Omit `-sugkey` unless you are specifically testing the SignUpGenius import —
with it, the app pulls **real registrant names and emails** from the live API.

See the [README](README.md#command-line-options) for the full option list.

## Verifying a change

CI is the check that matters — `.github/workflows/build.yml`, on every push and
pull request, across all four platforms. Locally, the two test scripts are the
fast feedback loop:

```sh
node scripts/test-seat-conflicts.js     # the composition rules, as pure functions
bash scripts/test-board-evening.sh      # a whole evening against a real server
```

Neither needs network access or any installed package, and both refuse to touch
real data: the evening test seeds a throwaway directory with synthetic names on
a spare port. **Never copy live event data in to make a test look busier.**

`scripts/verify-parity.sh` also exists. It boots the inherited 2019 binary
beside the rebuilt one and reports every difference in class signatures, served
bytes, endpoint responses, data files and startup logs. It was the acceptance
gate while the rebuild was being proved correct against the original; that job
is finished, and it is now **an optional diagnostic** — reach for it if you
suspect a change disturbed behaviour inherited from the original and you want to
see exactly what moved. It is not required for a pull request, it does not run
in CI, and it needs the inherited jar, which is deliberately kept out of the
repository and off GitHub.

### Running the shell scripts off Linux

They run on macOS and Git-for-Windows as well as Linux, and are written to the
portable spelling of every tool they use. The theme of the bugs found here is
that **a portability problem in a test script does not look like a failure — it
looks like a pass**, because a tool that quietly produces nothing empties out
*both* sides of a comparison. Keep that in mind before "simplifying" any of it:

- **No Python.** The normalizers are `awk`. A stock Windows box resolves
  `python3` to a Microsoft Store alias stub, and a stock macOS has no `python3`
  at all (`/usr/bin/python3` is a shim that fails with an Xcode-tools notice);
  both write nothing to stdout and exit, which once blanked both sides of a
  comparison and passed a section without comparing it. There is also no single
  interpreter name that works everywhere — Debian and Raspberry Pi OS have
  `python3` and no bare `python`. Do not reintroduce a Python dependency.
- **Bash 3.2 on macOS.** The runners still ship it, so no `mapfile`, no
  `declare -A`, no `${var,,}`. `test-board-evening.sh` uses positional
  parameters instead of arrays for exactly this reason.
- **BSD vs GNU tools** (macOS): `mktemp -d` needs an explicit template, `\+` is
  not a repetition operator in BSD `sed` (use `sed -E` and `+`), `wc -l` pads
  its output with spaces, and there is no `sha256sum` (`shasum -a 256` instead).
  Compare binary bodies with `cmp` rather than piping through `sed`, which is
  not dependable on data containing NULs.
- **The MSYS2 / Git-for-Windows `unzip`** is built with `WILD_STOP_AT_DIR`, so
  `*` does not cross `/`; use `shkc/**`, not `shkc/*`, or the extraction quietly
  produces zero files.
- **Case-insensitive filesystems** are the default on macOS and Windows, so two
  paths differing only in case collide.
- macOS may ask whether `java` should accept incoming network connections the
  first time a server starts. The tests use loopback, so allowing or denying it
  does not change the result.

## House style

- **Every name describes its purpose.** The decompiler emitted `var1`, `var10`
  and friends throughout; they have all been renamed and none remain. Locals,
  parameters and fields you write get self-documenting names (`bindPrefix`,
  `boundAddress`, `memberIds`) — never a `var##`, and never one copied from the
  surrounding decompiled style, because that style is an artifact, not a
  convention. CI enforces this: the "No decompiled variable names" step greps
  `src/main/java` and fails the build if any come back.
- **Editing a method body does not change a class's signature; adding or
  removing a field or method does.** Add null-guards freely. Removing a member
  is the edit that bites: an accessor that reads as dead today is one upstream
  change away from being called again, and it surfaces as a compile error long
  after the deletion looked safe. Check for callers first, and delete a member
  in the same change as its last caller. If you want to see exactly which
  declared members have moved against the original binary, `verify-parity.sh`
  still reports that as a diagnostic — a targeted filter in its `sig()` keeps
  the rest of that class compared, where exempting the class skips every
  signature in it.
- **Server endpoints are the contract.** The UI rework froze every endpoint and
  wire format. Prefer client-only changes; if you must touch the server, add a
  case to `scripts/test-board-evening.sh` covering it.
- **Handlers must not crash the server.** All request handling sits inside a
  `Throwable` net in `WebServer.service` that logs and returns a clean 500 for
  that one request. Still, null-guard your inputs: `getParameter` can return
  null, and `new StringTokenizer(null, ...)` throws.
- **Comments explain *why*, not *what*.** The what is usually obvious; the why
  is often "because the original binary did it this way".
- **Branding stays district-neutral.** Do not reintroduce a specific district or
  council name.

### Colour has meaning

`eb-ui.css` implements the Scouts BSA palette (BSA Brand Guidelines p.54: "mainly
tan, gray, and olive hues… Scouting Red as an accent or action color"). Button
colour states what the button *does*, so an operator can find the right one
without reading every label:

| Colour | Meaning | Examples |
| --- | --- | --- |
| **Olive** `#243E2C` | the action proceeds, nothing is lost | Refresh, Seat, Complete, + Room, Change Room |
| **White** | navigate or view, no data changes | Settings, Admin, Login, Help, View, CSV |
| **Red** `#CE1126` | destructive, reversing, cancelling | − Room, Reset, Postpone, Clear (adults), Delete |

Three intents, which is where Carbon, Material and USWDS all land (primary /
secondary / danger). Blue was briefly a fourth, "a neutral change", holding one
button — Change Room. It was retired when **Scouting Blue `#003F87` became the
heading-band colour** above every grid: a hue that means both a surface and an
action stops telling you what a button does. Change Room is olive now, and the
room row reads correctly — + Room and Change Room leave the schedule intact,
− Room is the one that takes something away.

Pick by **meaning, not by label**. The two buttons named "Clear" are deliberately
different colours: the Adult panel's unchecks people and is red, while the room
filter only empties a search box and is white. Matching them would promise a
consequence the second one does not have.

Two hard constraints when adding a colour:

- **Measure it.** Every pairing above passes WCAG 1.4.3 (AA): olive-on-white and
  white-on-olive 11.66:1, white-on-red 5.63:1, white-on-blue 10.19:1. Three brand
  colours cannot carry text on white at all — Pale Gray 3.61, Dark Tan 2.66, Pale
  Blue 2.15 — they are background-only.
- **Olive, red and blue are mutually non-adjacent.** All three are dark and sit
  within a whisker of each other in luminance: red/olive 2.07:1, blue/red
  1.81:1, blue/olive 1.14:1. Each goes on white, tan or light tan — never
  directly against another.

If you need a *fourth* signal, note that the palette has no clean one left, and
say why in the PR. Cub Scouting Gold `#FDC116` belongs to Cub Scouting (p.55),
Scouting Dark Blue `#003366` is a derived shade of the band blue and reads as the
same signal, Scouting Tan is 1.28:1 against the Light Tan toolbar and disappears
into it, Scouting Pale Gray fails contrast, and Warm Gray `#515354` is already
the disabled colour — using it for an available action puts "you can't do this"
and "do this" in one hue.

## Never commit data or secrets

The app's working files contain personal information about adults **and
minors**. They are never committed. Guards, in order:

1. `.gitignore` excludes all CSV/spreadsheet formats, dated `YYYY-MM-DD/` run
   folders, `Master_AdultHistory*`, `*Board_Results*`, `LOGIN_INFO*`, the legacy
   `RunScheduler.*` scripts (they embed an API key), `original/`, and `.env`.
2. `scripts/hooks/pre-commit` hard-fails any commit that stages one of those or
   a literal API key.

**Do not bypass the hook with `--no-verify`.** If it fires, it is right and you
are about to publish something you cannot unpublish. See
[SECURITY.md](SECURITY.md).

## Commits and pull requests

- Branch off `main`.
- Write commit messages in the imperative mood with a short subject line, then a
  body explaining **why**. Look at `git log` for the register: they explain the
  problem, the fix, and the consequences, not just the diff.
- Say in the PR if you deliberately diverged from the original binary's
  behavior, and why.
- CI must be green on all four platforms.
- When you fix a crash, add a regression assertion to the CI smoke test — there
  are existing examples guarding past bugs.

## Cutting a release

Versions are **CalVer** — the release date. That keeps them distinct from the
inherited binary's date, which is provenance rather than a version.

```sh
# 1. bump <version> in pom.xml to today, e.g. 2026.08.27
# 2. commit, tag to match, push both
git commit -am "Release 2026.08.27"
git tag v2026.08.27
git push --follow-tags
```

Pushing the tag runs `.github/workflows/release.yml`, which builds the shaded
jar, refuses to publish if the artifact carries a secret or participant data,
smoke tests that exact jar, and attaches it to a GitHub Release.

The tag must match the pom version or the workflow fails — that check exists so
a release named `v2026.08.27` can never ship a jar named `...-2026.07.30.jar`.

**The SignUpGenius key is never published.** It is not in the jar (it is passed
at runtime via `-sugkey`), and the release build fails if it finds the key or
any data file inside the artifact. Do not attach CSVs, spreadsheets, or logs to
a release.

We publish **Release assets, not GitHub Packages**. This is an end-user
application rather than a library, and the Maven registry would make every
downloader configure an authenticated `settings.xml` — poor for a volunteer
setting up a Raspberry Pi.

## Where to ask

Open a GitHub issue. Follow-up work and design decisions are tracked there.
