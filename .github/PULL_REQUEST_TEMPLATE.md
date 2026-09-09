## What and why

<!-- What changes, and what problem it solves. Link the issue if there is one. -->

## Parity gate

<!-- scripts/verify-parity.sh is the acceptance test for this project. It runs
     only on a machine that has the inherited jar; CI cannot run it. -->

- [ ] `scripts/verify-parity.sh` passes
- [ ] Not run — why:

If you deliberately diverged from the original binary's behavior, say which
exemption or normalization you added to the script, and why:

<!-- e.g. "Added REMOVED_FEATURE for VerifyBoardHandler; the Verify step is gone." -->

## Checks

- [ ] `./mvnw clean package` succeeds (`clean` if any resource was deleted or renamed)
- [ ] CI green on Linux amd64, Windows amd64, Linux arm64, and macOS
- [ ] Tried it in a browser against synthetic data on a spare port
- [ ] Fixed a crash? Added a regression assertion to the CI smoke test

## Data safety

- [ ] No CSV, spreadsheet, dated run folder, `Master_AdultHistory*`, `LOGIN_INFO*`, or `.env` is staged
- [ ] No API key, participant name, or contact detail appears in the diff, the commit messages, or this description
- [ ] The pre-commit hook ran (not bypassed with `--no-verify`)

## Notes for the reviewer

<!-- Anything surprising: behavior changes operators will notice, follow-up work,
     or a decision you would like a second opinion on. -->
