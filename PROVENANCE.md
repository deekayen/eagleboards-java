# Provenance

This project reconstructs source code from an inherited binary. The original
application was written by a third party for the Etowah District (Atlanta Area
Council, Scouting America); the source code was never received — only the
compiled JAR below.

## Original artifact

**The original jar is intentionally NOT committed** — its bundled
`signup_genius_api.js` embeds a live SignUpGenius API key, so the binary is
kept only on the maintainer's machine (`original/`, gitignored). The checksum
below authenticates any offline copy. The copy of `signup_genius_api.js`
under `src/main/resources` has that key replaced with the placeholder
`REPLACE_WITH_SIGNUP_GENIUS_KEY`; nothing references the file at runtime (the
server-side integration takes the key from the `-sugkey` option instead).

| | |
|---|---|
| File | `original/EagleBoardScheduler_20190618.jar` (kept offline) |
| SHA-256 | `5d88ea0107e48c89b8994f2a4a711ad3423c1f3dda41b04a689bf7f820adbebd` |
| Size | 3,318,944 bytes |
| Manifest | `Main-Class: shkc.core.EagleBoardScheduler`, `Created-By: 1.7.0_09 (Oracle Corporation)` |
| Bytecode | major version 51 (Java 7) |
| App class dates | 2017-10-24 (core), jar assembled 2019-06-18 |

## What the JAR bundles

- `shkc/core/**` — the application itself (~21 top-level classes) plus the
  `shkc/core/WEBROOT/**` static web UI (HTML/JS/CSS/images)
- `shkc/json/simple/**` — a repackaged copy of json-simple 1.1
- `monfox/log/**` — a small logging library
- `org/eclipse/jetty/**`, `javax/servlet/**` — Jetty 8 (class timestamps match
  the 8.1.11.v20130520 release) and Servlet API 3.0
- `shkc/core/WEBROOT/dhtmlx/**` — dhtmlxSuite 4.1.2 Standard Edition (GPL)

## Reconstruction method

Java sources under `src/main/java` were produced by decompiling the JAR's
`shkc/**` and `monfox/**` classes (Vineflower, cross-checked with CFR), then
minimally hand-fixed until they compile; the git tag `decompiled-raw` marks the
unmodified decompiler output, so every hand edit is visible as a diff from that
tag. Static resources under `src/main/resources` were extracted from the JAR
byte-for-byte. Jetty and the Servlet API are consumed as ordinary Maven
dependencies rather than being decompiled: the rebuild was first proven
behaviorally identical against the same Jetty 8.1.11 the binary bundled,
then migrated to current, supported Jetty 12 (jakarta.servlet) —
`WebServer.java` is the only class that changed, and
`scripts/verify-parity.sh` still passes against the original binary.

## Licensing note

The bundled dhtmlxSuite 4.1.2 Standard Edition is GPL-licensed, so this
repository is licensed GPL-2.0 (see `LICENSE`) to remain distributable.
