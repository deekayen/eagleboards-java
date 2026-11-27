# Security & privacy

This project handles personal information about **minors**. Please read this
before your first commit.

## Sensitive data

| Data | Where it lives | Why it matters |
| --- | --- | --- |
| Scout names, emails, unit; a DOB or phone number from older files (no longer collected: SPEC.md D-7, D-8) | `scouts.csv`, `scouts_scheduled.csv`, dated `YYYY-MM-DD/` folders, board-results spreadsheets | Personal information about minors |
| Adult names, emails, phones | `adults.csv`, `Master_AdultHistory.csv` | Personal information; the history file is cumulative across years |
| The event log | `eagleboards.log`, and its rolled copies `eagleboards.log.1` to `.5`, in the event's data folder | Names people by record ID (`Type:Last:First:Unit`), and holds the addresses and browser names of the tablets that connected. Never email addresses, phone numbers, birthdates, free-text notes, or the API key by design, but see "The event log" below for what enforces that. A `-debug` run holds more |
| SignUpGenius API key | `.env`, the inherited jar, legacy `RunScheduler.*` | Grants API access to the district's signups |
| `LOGIN_INFO*` | operator machines | Credentials |

## Never commit these

`.gitignore` excludes all CSV and spreadsheet formats, dated `YYYY-MM-DD/`
folders, `Master_AdultHistory*`, `*Board_Results*`, `LOGIN_INFO*`, the legacy
`RunScheduler.*` scripts, `original/`, `.env`, and every `*.log` file (the event
log lives in whatever folder `-d` names, which need not be a dated one).

`scripts/hooks/pre-commit` hard-fails any commit that stages one of those or a
literal API key. Install it once per clone:

```sh
git config core.hooksPath scripts/hooks
```

**Do not bypass it with `--no-verify`.** If the hook fires it is right, and the
thing it stopped cannot be unpublished once pushed.

The only committed configuration is `config.properties`: warning timings, no
personal information.

## Handling the API key

- Keep it in `.env` (gitignored). Copy `.env.example` and fill it in.
- Never paste it into an issue, a pull request, a screenshot, or a log excerpt.
- CI reads it from the `SUG_KEY` repository secret. GitHub masks it in logs.

SignUpGenius takes the key as a URL query parameter (`user_key=`), so the URL
that fetches the registrations holds it. What the program does about that:

- **Masked.** `Log.redact` replaces the value of a `key=`, `apikey=`,
  `user_key=`, `token=`, `password=`, or `secret=` query parameter with `***`.
  The `-verbose` console line that shows the request URL goes through it, and so
  does the text of a connection error or an unreadable response from
  SignUpGenius, on the console and in the event log. When the import fails,
  `PreRegistrationLoader` also replaces any appearance of the key itself in the
  message and in the exception chain it prints and logs. The `arguments:` line
  at the top of every run in the event log shows the value after `-sugkey` as
  `***`. Every INFO, WARN, and ERROR line passes through the same query-parameter
  mask as a backstop before it is written to the file. `SignUpGeniusClient`
  writes no request URL of its own to the event log; a URL can reach the log only
  inside an exception message, and that message is masked as above.
- **Shown in part.** The startup message and the log's `pre-registrations:`
  line give the last four characters of the key (`...abcd`), so a log says
  which key was used. A key too short for that shows as `(hidden)`.
- **Not masked.** The mask matches the query-parameter shape, so a key that
  appeared in some other text would pass through it. The code never prints one
  that way today, but nothing would stop a future message from doing so. The
  key is also on the command line (`-sugkey`), so anyone who can list the
  machine's processes can read it, and a `-debug` log does not mask anything but
  secrets (see below). `FileLocator`, which makes the HTTP request, writes the
  unmasked URL to `java.util.logging` at FINE and FINER; nothing in the program
  configures that logger, so those messages are discarded unless a JVM is started
  with its own `logging.properties` that turns them on.

The launcher scripts add `-verbose` only when `EB_VERBOSE` is set. The key is
masked there, but do not screenshot or paste that console: `-verbose` still
prints whole records (names, emails, and phone numbers) as the files load and
as people sign in or are imported, and the notes a board enters.

## The event log

Every run writes `eagleboards.log` in the event's data folder, whatever flags it
was started with (RUNNING.md, "The event log", says how to read it). It is
participant data and is handled like the CSV files beside it.

- **What it holds.** It names people through record IDs (`Type:Last:First:Unit`),
  because "why was this adult refused" cannot be answered without one. It also
  holds the address and browser name of every device that connected.
- **What it must never hold:** email addresses, phone numbers, birthdates,
  free-text notes, and the SignUpGenius key. Request parameters reach the log
  only through `Log.safeParams`, which writes the value of a short list of
  identifier, status, and setting parameters and only the length of any other.
- **How that is enforced.** Mostly by convention and review: a new log line must
  use `safeParams`, `safeValue`, and `redact`, not concatenate a parameter.
  The one automatic check is `Log.scrub`, which replaces anything shaped like an
  email address (accented letters included) or a North American phone number (ten
  digits in any grouping, or with none), masks the key shapes above, masks the
  SignUpGenius key itself wherever it appears (it is registered at startup),
  in every INFO, WARN, and ERROR line and in the stack trace under it. It catches
  an exception message that quotes a CSV line. It does not recognize a name, a
  birthdate, or a note, so it is a backstop and not a guarantee.
- **`-debug` is different.** With `-debug`, what `-verbose` prints (every
  request's full parameter map, each SignUpGenius registrant's name) is also
  written to the file as DEBUG lines. Those lines are masked for secrets only,
  so they can hold email addresses, phone numbers, and notes. Use `-debug` to
  reproduce one problem, not for a whole event, and treat that file as sensitive
  as `adults.csv`.
- **Sharing it.** Do not attach a log to an issue, a pull request, or a chat
  without reading it first. Never commit one: the hook blocks `*.log` and the
  rolled `*.log.1` to `.5`, and `.gitignore` excludes them.

## Working with real data

- **Develop against synthetic data.** Use a scratch directory, a spare port, and
  a header-only `Master_AdultHistory.csv`. Never load the real history file into
  anything you are going to screenshot.
- Omit `-sugkey` unless you are specifically testing the import; with it, the
  app pulls real registrant names and emails from the live API.
- If a test does pull real data, delete the files afterward.
- The inherited jar is kept offline by the maintainer because it embeds an API
  key. It is authenticated by the SHA-256 in [PROVENANCE.md](PROVENANCE.md).

## Network exposure

The app serves plain HTTP with **no authentication**. Anyone who can reach the
port can read and modify the event's data. That is acceptable only because it is
meant to run briefly on a trusted local network.

- Run it on the venue LAN, never on a public interface or a port-forward.
- Use `-bind <ip-prefix>` to pin the listener to the one interface you actually
  serve from, so it does not appear on virtual, VPN, or secondary adapters.
- Shut it down when the event ends.

## Reporting a vulnerability

Open a GitHub issue for anything non-sensitive. For something that would expose
participant data or the API key, contact the maintainer directly instead of
filing a public issue, and do not include the affected data in your report.
