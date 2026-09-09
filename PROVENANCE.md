# Provenance

This project reconstructs source code from an inherited binary. The original
application was written by a third party for the Etowah District (Atlanta Area
Council, Scouting America); the source code was never received — only the
compiled JAR below.

## Original artifact

**The original jar is intentionally NOT committed** — its bundled
`signup_genius_api.js` embeds a live SignUpGenius API key, so the binary is
kept only on the maintainer's machine (`original/`, gitignored). The checksum
below authenticates any offline copy. `signup_genius_api.js` is **not carried
over into this tree at all**: nothing referenced it at runtime (the server-side
integration takes the key from the `-sugkey` option instead), and it was the one
file bearing a third-party copyright notice, so it was deleted rather than
shipped with a placeholder key. `scripts/verify-parity.sh` asserts both that the
file stays absent and that the original's embedded key appears nowhere in the
rebuilt jar.

| | |
|---|---|
| File | `original/EagleBoardScheduler_20190618.jar` (kept out of the tree) |
| SHA-256 | `5d88ea0107e48c89b8994f2a4a711ad3423c1f3dda41b04a689bf7f820adbebd` |
| Size | 3,318,944 bytes |
| Manifest | `Main-Class: shkc.core.EagleBoardScheduler`, `Created-By: 1.7.0_09 (Oracle Corporation)` |
| Bytecode | major version 51 (Java 7) |
| App class dates | 2017-10-24 (core), jar assembled 2019-06-18 |

The binary stays **off this machine's git entirely** — gitignored, blocked by
`scripts/hooks/pre-commit`, and never uploaded anywhere. It carries the embedded
key described above and the unresolved rights question, so it is kept only as a
local file by whoever is running `scripts/verify-parity.sh`. CI does not have it
and does not run that script.

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

`LICENSE` holds the Apache-2.0 text; see also `NOTICE`.

The repository was previously GPL-2.0, which the bundled dhtmlxSuite 4.1.2
Standard Edition required. That library has been removed — the interface was
rewritten on MIT-licensed Tabulator — so no bundled component compels copyleft
any more. What remains is Jetty (EPL-2.0), the Jakarta Servlet API (EPL-2.0 or
GPL-2.0 with Classpath Exception), Jackson (Apache-2.0), SLF4J (MIT) and
Tabulator (MIT). GPL-2.0 was also a poor fit once dhtmlx was gone, because
Apache-2.0 — which Jackson uses, and which is shaded into the distributed
jar — is generally treated as incompatible with GPL-2.0.

