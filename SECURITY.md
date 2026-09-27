# Security & privacy

This project handles personal information about **minors**. Please read this
before your first commit.

## What is sensitive here

| Data | Where it lives | Why it matters |
| --- | --- | --- |
| Scout names, emails, unit; a DOB or phone number from older files (no longer collected: SPEC.md D-7, D-8) | `scouts.csv`, `scouts_scheduled.csv`, dated `YYYY-MM-DD/` folders, board-results spreadsheets | Personal information about minors |
| Adult names, emails, phones | `adults.csv`, `Master_AdultHistory.csv` | Personal information; the history file is cumulative across years |
| SignUpGenius API key | `.env`, the inherited jar, legacy `RunScheduler.*` | Grants API access to the district's signups |
| `LOGIN_INFO*` | operator machines | Credentials |

## Never commit these

`.gitignore` excludes all CSV and spreadsheet formats, dated `YYYY-MM-DD/`
folders, `Master_AdultHistory*`, `*Board_Results*`, `LOGIN_INFO*`, the legacy
`RunScheduler.*` scripts, `original/`, and `.env`.

`scripts/hooks/pre-commit` hard-fails any commit that stages one of those or a
literal API key. Install it once per clone:

```sh
git config core.hooksPath scripts/hooks
```

**Do not bypass it with `--no-verify`.** If the hook fires it is right, and the
thing it stopped cannot be unpublished once pushed.

The only committed configuration is `config.properties` — warning timings, no
personal information.

## Handling the API key

- Keep it in `.env` (gitignored). Copy `.env.example` and fill it in.
- Never paste it into an issue, a pull request, a screenshot, or a log excerpt.
- CI reads it from the `SUG_KEY` repository secret. GitHub masks it in logs.

**Known issue:** SignUpGenius takes the key as a URL query parameter, and the
app logs the full request URL when run with `-verbose` — which the launcher
scripts do. Your console window therefore contains the key in plain text. Do not
screenshot or paste that console. Scrub any log before sharing it. Masking that
one log line is tracked as follow-up work.

## Working with real data

- **Develop against synthetic data.** Use a scratch directory, a spare port, and
  a header-only `Master_AdultHistory.csv`. Never load the real history file into
  anything you are going to screenshot.
- Omit `-sugkey` unless you are specifically testing the import — with it, the
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
participant data or the API key, contact the maintainer directly rather than
filing a public issue, and do not include the affected data in your report.
