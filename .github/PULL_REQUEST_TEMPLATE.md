## What and why

<!-- What changes, and what problem it solves. Link the issue if there is one. -->

## Checks

- [ ] `./mvnw clean package` succeeds (`clean` if any resource was deleted or renamed)
- [ ] CI green on Linux amd64, Windows amd64, Linux arm64, and macOS
- [ ] Tried it in a browser against synthetic data on a spare port
- [ ] Fixed a crash? Added a regression assertion to the CI smoke test
- [ ] Changed how a board is seated, run or torn down? Added a case to `scripts/test-board-event.sh`

## Data safety

- [ ] No CSV, spreadsheet, dated run folder, `Master_AdultHistory*`, `LOGIN_INFO*`, or `.env` is staged
- [ ] No API key, participant name, or contact detail appears in the diff, the commit messages, or this description
- [ ] The pre-commit hook ran (not bypassed with `--no-verify`)

## Notes for the reviewer

<!-- Anything surprising: behavior changes operators will notice, follow-up work,
     or a decision you would like a second opinion on. -->
