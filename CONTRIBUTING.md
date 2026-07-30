# Contributing

Thanks for helping with the Review Board Scheduler. This page covers everything
needed to get productive: setup, build, test, house style, and how changes are
reviewed. Read [ARCHITECTURE.md](ARCHITECTURE.md) first if you want the map of
how the app fits together, and [PROVENANCE.md](PROVENANCE.md) for where the code
came from — it explains why parts of it look the way they do.

## The one unusual thing about this project

The source was **reconstructed by decompiling an inherited binary**. No original
source was ever received. That single fact drives most of the conventions below:
the odd variable names, the reluctance to reformat, and above all the parity
gate, which is the project's acceptance test.

The maintainer does not read Java. **`scripts/verify-parity.sh` — not code
review — is what proves a change is safe.** It boots the original jar and the
rebuilt jar side by side and shows they behave identically for everything that
was not deliberately changed.

## Prerequisites

| Need | Version | Notes |
| --- | --- | --- |
| JDK | 21 or newer | CI builds on Temurin **25**; bytecode targets 21 |
| Maven | none | use the bundled wrapper `./mvnw` (`mvnw.cmd` on Windows) |
| Git | any recent | |

To run the parity gate you also need `bash`, `unzip`, `curl`, and **`python3`**.

### Supported platforms

CI builds and runs a smoke test on every push and pull request across:

- **Linux amd64** (`ubuntu-latest`)
- **Windows amd64** (`windows-latest`)
- **Linux arm64** (`ubuntu-24.04-arm`) — the Raspberry Pi deployment target

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

## The parity gate

```sh
scripts/verify-parity.sh
```

**Keep it passing.** It needs `original/EagleBoardScheduler_20190618.jar`, which
is not in the repository — it embeds an API key. Ask the maintainer for it; it
is authenticated by the SHA-256 in [PROVENANCE.md](PROVENANCE.md). Drop it at
that path (`original/` is gitignored) and the script will find it.

What it checks:

1. **Structure** — every class's declared members still match (`javap`)
2. **Reads** — both servers return identical bytes for pages, assets, and data endpoints
3. **Writes** — identical requests produce identical files on disk
4. **Startup logs** — identical, modulo time and port
5. **First-party UI** — the rewritten pages serve, carry no dhtmlx references, and keep their headings

### When you change behavior on purpose

Do not just let the gate fail. **Teach it** that the divergence is deliberate,
with a comment saying why. There are worked examples in the script: the Jetty
8→12 migration (`MIGRATED`), the Jackson and JUL swaps (`REMOVED_VENDORED`), the
removed Verify step (`REMOVED_FEATURE`), the config schema change, and
`help.html` leaving the byte-compared lists once its instructions were rewritten.

### Running it on Windows

Two gotchas, both of which fail *silently* or misleadingly:

- The MSYS2 / Git-for-Windows `unzip` is built with `WILD_STOP_AT_DIR`, so `*`
  does not cross `/`. The script uses `shkc/**` for this reason. If you "fix"
  that back to `shkc/*` the extraction quietly produces **zero files** and the
  gate reports a cheerful pass having compared two empty trees.
- `python3` and `python` on a stock Windows box are Microsoft Store alias stubs,
  not Python. Install real Python and make sure `python3` resolves.

## House style

- **Leave the decompiled variable names alone.** `var1`, `var10` and friends are
  everywhere. Sweeping renames widen the parity diff and add risk for no
  functional gain. Keep edits minimal and local.
- **Name anything you write descriptively.** New locals, parameters, and fields
  get self-documenting names (`bindPrefix`, `boundAddress`, `connector`) — not
  `var27`. Renaming a *local* is parity-safe: the gate compares declared
  members, and local names are not part of a javap signature.
- **Editing a method body does not change a class's signature; adding or
  removing a field or method does.** This is why you can add null-guards freely,
  but removing a member means teaching the parity gate about it. Prefer a
  targeted filter in the gate's `sig()` function, which keeps the rest of that
  class compared, over exempting the whole class — an exemption skips every
  signature in it.
- **Server endpoints are the contract.** The UI rework froze every endpoint and
  wire format. Prefer client-only changes; if you must touch the server, check
  whether the gate exercises that path.
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
| **Olive** `#243E2C` | primary or additive | Refresh, Seat, Complete, + Room |
| **White** | navigate or view | Settings, Admin, Login, Help, View, CSV |
| **Red** `#CE1126` | destructive, reversing, cancelling | − Room, Reset, Postpone, Clear (adults), Delete |
| **Blue** `#003F87` | a neutral change | Change Room |

Pick by **meaning, not by label**. The two buttons named "Clear" are deliberately
different colours: the Adult panel's unchecks people and is red, while the room
filter only empties a search box and is white. Matching them would promise a
consequence the second one does not have.

Two hard constraints when adding a colour:

- **Measure it.** Every pairing above passes WCAG 1.4.3 (AA): olive-on-white and
  white-on-olive 11.66:1, white-on-red 5.63:1, white-on-blue 10.19:1. Three brand
  colours cannot carry text on white at all — Pale Gray 3.61, Dark Tan 2.66, Pale
  Blue 2.15 — they are background-only.
- **Never put Scouting Red on Scouts BSA Olive.** They measure 2.07:1 against
  each other; both look strong on white but their luminances are nearly
  identical. Red belongs on white or tan.

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
- Say in the PR whether `verify-parity.sh` passed, and if you taught it a new
  exemption, say which and why.
- CI must be green on all three platforms.
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

The repository is **private** pending a rights review with the original author
and a final license decision. Do not redistribute it. See [LICENSE](LICENSE) and
the pre-public checklist issue.

The rights question is about the reconstruction itself: the app was rebuilt by
decompiling a binary whose author reserved all rights. No third-party copyright
notice remains in this tree — the vendored `monfox/log` library was replaced by
`java.util.logging`, and the single unused file that carried a Monfox LLC notice
has been deleted.
