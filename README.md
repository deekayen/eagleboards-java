# Eagle Board Scheduler

Check-in and room-scheduling application for Eagle Scout Boards of Review,
used by the Etowah District (Atlanta Area Council, Scouting America). It runs
a small local web server: Scouts and board members check in from a browser at
the registration station, while an administrator manages people (`/admin`) and
room assignments (`/scheduler`) from the admin computer. Data is stored as CSV
files next to the application; each event night writes a dated results folder
and a board-results spreadsheet.

This repository reconstructs the application's source from an inherited binary
(see `PROVENANCE.md`) and modernizes it to build and run on current Java LTS
releases. The functional behavior and branding are intentionally identical to
the original `original/EagleBoardScheduler_20190618.jar` until stability is
proven; see the issue tracker for planned changes (multi-district support,
dependency upgrades). The inherited binary itself is not in the repository —
it embeds an API key — and is kept offline by the maintainer, authenticated
by the SHA-256 in `PROVENANCE.md`. Behavioral equivalence between it and this
source tree is proven mechanically by `scripts/verify-parity.sh`.

## Requirements

- Java 21 or newer (the build targets Java 21 bytecode; Temurin 25 works)
- Maven is not required to be installed — use the included wrapper `./mvnw`

## Build

```sh
./mvnw package
```

This produces a self-contained jar at `target/eagleboardscheduler-*.jar`.

## Run

```sh
scripts/run.sh
```

The script expects the SignUpGenius API key in the `SUG_KEY` environment
variable, or in an untracked `.env` file (copy `.env.example`). Equivalent
manual invocation:

```sh
java -jar target/eagleboardscheduler-*.jar -verbose -w \
  -a Master_AdultHistory.csv -c config.csv -port 8080 -sugkey "$SUG_KEY"
```

Two windows appear: a console and a small grey window showing the URL to open
on the check-in station. On the admin computer, append `/admin` (people
management) or `/scheduler` (room assignments) to that URL.

## Data and privacy — read before committing

The application's data files live in this same folder and contain personal
information about adults and minors. **They are never committed.** Guards:

- `.gitignore` excludes all CSV/spreadsheet formats (except `config.csv`,
  which holds only display colors and timings), all dated `YYYY-MM-DD/` run
  folders, `Master_AdultHistory.csv`, `*Board_Results*`, `LOGIN_INFO*`, the
  legacy `RunScheduler.*` scripts (they embed an API key), and `.env`.
- `scripts/hooks/pre-commit` (installed via `git config core.hooksPath
  scripts/hooks`) hard-fails any commit that stages one of those files or a
  literal API key. Do not bypass it with `--no-verify`.

After cloning on a new machine, run:

```sh
git config core.hooksPath scripts/hooks
```

## License

GPL-2.0 (see `LICENSE`) — required by the bundled dhtmlxSuite 4.1.2 Standard
Edition web UI library, which is GPL-licensed.
