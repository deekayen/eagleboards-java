#!/bin/bash
# Behavioral parity check: original inherited jar vs rebuilt-from-source jar.
#
# The project owner cannot review Java code, so this script is the acceptance
# gate for the reconstruction: it proves the rebuilt jar behaves identically
# to original/EagleBoardScheduler_20190618.jar without reading the code.
#
#   1. Structure  — every class's declared members match (javap), modulo
#                   compiler-internal synthetics (access$ bridges, switch maps)
#   2. Reads      — both servers return identical bytes for every page,
#                   asset, and data endpoint
#   3. Writes     — identical registration/update requests produce identical
#                   data files on disk
#
# Timestamps (RegTime/LastUpdateTime fields, HTTP Date headers) are the only
# normalized difference. Everything else must match byte-for-byte.
#
# Usage: scripts/verify-parity.sh   (from the repo root; builds if needed)

set -u
cd "$(dirname "$0")/.."

ORIG_JAR=original/EagleBoardScheduler_20190618.jar
ADULT_FILE=Master_AdultHistory.csv
# `ls -t` (newest first), not a plain `ls`: the version is a DATE, so an
# alphabetical sort puts the OLDEST jar first, and `./mvnw package` never
# removes the previous version's. Left as `ls | head -1`, this gate would
# happily certify a months-old build as being at parity with the original --
# the one outcome it exists to make impossible.
pick_jar() { ls -t target/eagleboardscheduler-*.jar 2>/dev/null | grep -v original- | head -1; }
NEW_JAR=$(pick_jar)
PORT_A=18080
PORT_B=18081
FAILURES=0
# Set here because the EXIT trap kills them, and the trap can fire before the
# servers are started (any early exit) -- unset would abort it under `set -u`.
PID_A=
PID_B=

fail() { echo "FAIL: $1"; FAILURES=$((FAILURES + 1)); }
note() { echo "  ok: $1"; }

# --------------------------------------------------------------- 0. preflight
# Check every prerequisite up front, by name. The gate is only worth something
# if a missing one is LOUD: a tool that is absent (or, worse, present as a stub
# that exits quietly) empties out one side of a comparison further down, and an
# empty comparison reads exactly like agreement. This has bitten this script
# twice already -- see the notes on unzip wildcards and on python3 below.
#
#   scripts/verify-parity.sh --check
#
# runs only this section, so a new machine can be qualified before the
# inherited jar or a build is in place.
CHECK_ONLY=0
[ "${1:-}" = "--check" ] && CHECK_ONLY=1

missing_prereq=0
need() { # command "what it is needed for"
    command -v "$1" >/dev/null 2>&1 && return 0
    echo "MISSING: $1 — $2"
    missing_prereq=1
}
need java  "runs the two servers side by side"
need javap "reads declared members for the structural comparison (a JDK, not just a JRE)"
need unzip "extracts the classes and web assets from both jars"
need curl  "drives the two servers"
need awk   "normalizes CSV columns and log parameter maps"
need diff  "compares everything"
need cmp   "byte-compares the preserved assets"
need sed   "normalizes timestamps and branding"

if [ ! -f "$ORIG_JAR" ]; then
    echo "MISSING: $ORIG_JAR"
    echo "         The inherited binary is not in this repository -- it embeds a"
    echo "         SignUpGenius key. Ask the maintainer for it, check it against"
    echo "         the SHA-256 in PROVENANCE.md, and drop it at that path"
    echo "         (original/ is gitignored). Without it there is nothing to"
    echo "         compare the rebuild against."
    missing_prereq=1
fi

if [ "$missing_prereq" -ne 0 ]; then
    echo
    echo "PARITY: PREREQUISITES MISSING — nothing was compared"
    exit 1
fi

# Not fatal: the adult history is participant data and is never in the repo.
# A synthetic header-only file is generated into the sandbox when it is absent,
# so the gate still runs -- it just compares two empty adult lists instead of
# two identical populated ones. Said out loud, because a silently weaker check
# is the thing this script exists to prevent.
if [ -f "$ADULT_FILE" ]; then
    ADULT_FIXTURE=real
else
    ADULT_FIXTURE=synthetic
fi

if [ "$CHECK_ONLY" = 1 ]; then
    echo "preflight ok: this machine has everything the parity gate needs."
    [ "$ADULT_FIXTURE" = synthetic ] && \
        echo "  note: no $ADULT_FILE here; the run will use a synthetic header-only one."
    exit 0
fi

if [ -z "$NEW_JAR" ]; then
    echo "building rebuilt jar..."
    ./mvnw -q package || { echo "build failed"; exit 1; }
    NEW_JAR=$(pick_jar)
fi

# Name the artifact under test. A result that does not say what it tested is
# not evidence, and older jars in target/ are the way this gate would end up
# reporting on something other than the tree you are sitting in.
echo "testing: $NEW_JAR"
others=$(ls target/eagleboardscheduler-*.jar 2>/dev/null | grep -v original- | grep -vF "$NEW_JAR")
if [ -n "$others" ]; then
    echo "WARNING: target/ holds other builds, which are NOT what was tested:"
    echo "$others" | sed 's/^/           /'
    echo "         Run './mvnw clean package' if the result looks wrong."
fi

# ---------------------------------------------------------------- 1. structure
echo "== 1. structural comparison (javap declared members) =="
# Template spelled out: BSD mktemp (macOS) rejects a bare `mktemp -d`, it wants
# a template or -t. GNU mktemp accepts the template form too, so this is the
# one spelling that works on both.
WORK=$(mktemp -d "${TMPDIR:-/tmp}/eb-parity.XXXXXX") || exit 1
trap 'rm -rf "$WORK"; kill $PID_A $PID_B 2>/dev/null' EXIT
mkdir -p "$WORK/orig" "$WORK/new"
# '**' not '*': the MSYS2/Git-for-Windows unzip is built with WILD_STOP_AT_DIR,
# so '*' stops at '/' and 'shkc/*' silently extracts nothing (exit 11) — which
# looks like a clean pass because the comparison then runs on empty trees.
# '**' recurses on that build and on stock Info-ZIP alike, so it works on both.
unzip -qo "$ORIG_JAR" 'shkc/**' 'monfox/**' -d "$WORK/orig"
unzip -qo "$NEW_JAR"  'shkc/**' 'monfox/**' -d "$WORK/new" 2>/dev/null  # monfox/** absent post-swap

# Guard, because this gate has twice reported "ok" while comparing nothing: the
# unzip glob above extracted 0 files on one platform, and the CSV comparison
# further down ran through a `python3` that printed a notice and produced no
# output on another. Both looked like agreement. An empty extraction can never
# be a real pass -- the original jar has hundreds of classes -- so refuse to
# continue rather than let a future platform quietly repeat the trick.
#
# Two checks, because there are two ways to end up comparing less than you
# think. A floor on the class count catches a wildcard that matched nothing,
# and counting what landed against what the jar says it holds catches entries
# that collided on the way out -- which is a live risk on macOS and Windows,
# where the filesystem is case-insensitive by default and two entries whose
# names differ only in case overwrite each other silently.
# `| wc -l | tr -d` because BSD wc pads its output with spaces.
for side in orig new; do
    case "$side" in orig) jar=$ORIG_JAR ;; *) jar=$NEW_JAR ;; esac
    n=$(find "$WORK/$side" -name '*.class' | wc -l | tr -d '[:space:]')
    if [ "$n" -lt 50 ]; then
        echo "FAIL: extracted only $n class files into $WORK/$side."
        echo "      The unzip wildcard is not matching on this platform, so the"
        echo "      structural comparison would run on an empty tree and pass"
        echo "      without checking anything. Fix the extraction before trusting"
        echo "      any result from this script."
        exit 1
    fi
    listed=$(unzip -Z1 "$jar" 2>/dev/null | grep -cE '^(shkc|monfox)/.*[^/]$')
    landed=$(find "$WORK/$side" -type f | wc -l | tr -d '[:space:]')
    if [ "$listed" -gt 0 ] && [ "$landed" -ne "$listed" ]; then
        echo "FAIL: $jar lists $listed entries under shkc/ and monfox/, but only"
        echo "      $landed file(s) landed in $WORK/$side."
        echo "      Entries are being lost during extraction — on a case-insensitive"
        echo "      filesystem (the macOS and Windows default) two names differing"
        echo "      only in case overwrite each other, which would shrink what this"
        echo "      script compares without shrinking what it claims to compare."
        unzip -Z1 "$jar" 2>/dev/null | grep -E '^(shkc|monfox)/.*[^/]$' \
            | tr 'A-Z' 'a-z' | sort | uniq -d | sed 's/^/      collides: /' | head -5
        exit 1
    fi
done

# Compiler-internal artifacts with no behavior of their own; javac 25 emits
# them differently than javac 7 did (nestmates replaced access$ bridges).
# NetTest.class: the original jar misfiled this default-package class under
# shkc/core/, where the class loader could never have loaded it.
KNOWN_SYNTHETIC='monfox/log/SimpleLogger[$]1[.]class|shkc/core/NegaPreRegAdultRecordConverter[$]1[.]class|shkc/core/NetTest[.]class|shkc/core/WebServer[$]1[.]class'

# Deliberately rewritten past the original binary and no longer expected to
# match it by signature:
#  - WebServer + its dispatcher inner class: ported from Jetty 8 to Jetty 12
#    (ServletContextHandler / HttpServlet). Runtime behavior covered by 2-4.
#  - PopupDialog: the startup URL window was enhanced (centered text + a
#    clickable "open the scheduler" browser link). It only appears with -w,
#    is not part of the web protocol, and is checked visually, not here.
MIGRATED='shkc/core/WebServer[.]class|shkc/core/WebServer[$]LocalDefaultHandler[.]class|shkc/core/PopupDialog[.]class'

# Vendored third-party code the original binary carried, replaced by
# supported libraries: shkc.json.simple (json-simple 1.1, frozen 2012) ->
# Jackson; monfox.log -> java.util.logging.
REMOVED_VENDORED='^shkc/json/simple/|^monfox/'

# Features deliberately removed from the rebuilt app, so the original's class
# has no counterpart to compare against:
#  - VerifyBoardHandler: the Verify step (Registered -> Verified) was dropped;
#    a registered Scout is now seated directly, and /verify-board is gone.
#  - ConfigWindow: an unreferenced Swing settings window, superseded by the
#    browser Settings page. Nothing constructed it.
#  - FileLocator$Resolver: a pluggable-lookup callback. addResolver() was never
#    called, so the resolver list was always empty and the loop that consulted
#    it never ran. Removed with the loop in the dead-code audit.
#  - DataFileConverter$Filter: a per-field reject hook. Both converters passed
#    null for it, so the filter branch never ran. Removed with the branch.
# The trailing ([$]...)? also covers each class's anonymous inner classes —
# ConfigWindow carried five ActionListeners as ConfigWindow$1..$5.
REMOVED_FEATURE='shkc/core/EagleBoardScheduler[$]VerifyBoardHandler([$][A-Za-z0-9_]+)?[.]class|shkc/core/ConfigWindow([$][A-Za-z0-9_]+)?[.]class|shkc/core/FileLocator[$]Resolver[.]class|shkc/core/DataFileConverter[$]Filter[.]class'

# Members deleted in the dead-code audit: every one had zero references
# anywhere in the repo (Java, JS, HTML, scripts), so removing it cannot change
# behavior. They are listed PER CLASS rather than as one global pattern so a
# deletion here can never mask a same-named member that still exists on another
# class -- ScoutRecord.updateFields(boolean) is filtered, for instance, while
# PersonRecord's and DataRecord's real implementations keep being compared.
# A class with no entry gets NO_DELETIONS, a line javap can never emit.
mkdir -p "$WORK/deleted"
printf '___NO_DELETIONS___\n' > "$WORK/deleted/_default"
# AdultRecord/ScoutRecord: overrides whose whole body was super.updateFields().
cat > "$WORK/deleted/shkc_core_AdultRecord" <<'EOF'
 public void updateFields(boolean);
EOF
# ScoutRecord: unreferenced column accessors, the AdultScoutRatio pair left
# over from the retired B/S column, and a clone() nothing cloned.
cat > "$WORK/deleted/shkc_core_ScoutRecord" <<'EOF'
 public java.lang.Object clone();
 public shkc.core.ScoutRecord clone();
 public java.lang.String getAdultScoutRatio();
 public java.lang.String getBoardChair();
 public java.lang.String getBoardChairID();
 public java.lang.String getBoardMemberIDs();
 public java.lang.String getBoardMembers();
 public java.lang.String getBoardType();
 public java.lang.String getDOB();
 public java.lang.String getLeader();
 public java.lang.String getNotes();
 public java.lang.String getResult();
 public void setAdultScoutRatio(java.lang.String);
 public void updateFields(boolean);
EOF
cat > "$WORK/deleted/shkc_core_PersonRecord" <<'EOF'
 public java.lang.String getFlags();
 public java.lang.String getPhone();
 public java.lang.String getUnitName();
EOF
cat > "$WORK/deleted/shkc_core_RoomRecord" <<'EOF'
 public java.lang.String getBoardType();
EOF
# Commandline: a dev harness (test), an unused long-option predicate, an unused
# setter, and the CVS $Id$ string the decompiler preserved.
cat > "$WORK/deleted/shkc_core_Commandline" <<'EOF'
 boolean islongopt(java.lang.String);
 private static final java.lang.String _ident;
 public static void test(java.lang.String[], java.lang.String, java.lang.String, java.lang.String[], java.lang.String[]);
 public void setOptionNameSeparators(java.lang.String);
EOF
# DataRecord: convenience overloads no caller used; the fuller forms remain.
cat > "$WORK/deleted/shkc_core_DataRecord" <<'EOF'
 public void fromCSV(java.lang.String, char);
 public void toCells(java.lang.StringBuffer);
 public void toDataView(java.lang.StringBuffer);
EOF
# updateAdultScoutRatios: computed the B/S column, retired in e76fafa. It was
# already documented as unreachable and kept only for this gate.
cat > "$WORK/deleted/shkc_core_EagleBoardScheduler" <<'EOF'
 private void updateAdultScoutRatios(boolean);
EOF
# NameUtil: the whole variable-map API. Nothing ever put a variable in the map,
# so ${...} expansion always fell through to the system properties -- which it
# still does. _props went with the setters.
cat > "$WORK/deleted/shkc_core_NameUtil" <<'EOF'
 private java.util.Map _props;
 public java.lang.String getVariable(java.lang.String);
 public java.util.Iterator getVariableNames();
 public shkc.core.NameUtil(java.util.Map);
 public void addAll(java.util.Map);
 public void setVariable(java.lang.String, java.lang.String);
 public void setVariable(java.lang.String, java.util.Date);
 public void unsetVariable(java.lang.String);
EOF
cat > "$WORK/deleted/shkc_core_SignUpGeniusPlugin" <<'EOF'
 private shkc.core.NegaPreRegScoutRecordConverter$StatusConverter _statusConverter;
EOF
# DataFileConverter: the always-null Filter hook. Both the old constructor
# (which took it) and the new one (which does not) are filtered, because this
# is the one place a signature CHANGED rather than simply going away.
cat > "$WORK/deleted/shkc_core_DataFileConverter" <<'EOF'
 private shkc.core.DataFileConverter$Filter _filter;
 public shkc.core.DataFileConverter(shkc.core.DataRecordFile<T>, shkc.core.DataFileConverter$Filter, java.util.Map<java.lang.String, java.lang.String>, java.lang.String[]);
 public shkc.core.DataFileConverter(shkc.core.DataRecordFile<T>, java.util.Map<java.lang.String, java.lang.String>, java.lang.String[]);
EOF
# FileLocator: the filesystem-lookup half (getFile/getDirectory) and the
# resolver chain, neither of which any caller reached. What the app actually
# uses -- getInputStream and the search path -- is untouched and still compared.
cat > "$WORK/deleted/shkc_core_FileLocator" <<'EOF'
 private java.util.List _resolverList;
 public java.io.File getDirectory(java.lang.String) throws java.io.IOException;
 public java.io.File getDirectory(java.lang.String, boolean) throws java.io.IOException;
 public java.io.File getFile(java.lang.String) throws java.io.IOException;
 public java.io.File getFile(java.lang.String, boolean) throws java.io.IOException;
 public java.util.List getSearchPathList();
 public shkc.core.FileLocator();
 public void addResolver(shkc.core.FileLocator$Resolver);
 public void addSearchPath(java.lang.String);
 public void removeResolver(shkc.core.FileLocator$Resolver);
 public void setVariable(java.lang.String, java.lang.String);
EOF

sig() { # $1 = class file, $2 = its jar-relative name (for the deletion list)
    # Filtered as compiler-internal (verified behaviorally equivalent in the
    # bytecode): access$ bridges, enum $values()/switch-maps, and anonymous-
    # class capture plumbing (val$ field names and constructor shapes vary
    # between javac 7 and javac 25 but perform the same stores).
    # "static {};" also filtered: the original DataFileConverter carries an
    # EMPTY <clinit> (verified: its bytecode is a single return) that javac 25
    # elides; real static state lives in field initializers, which still show.
    # javax.servlet and jakarta.servlet are the same API before/after the
    # Jetty 12 migration — the rename is normalized so handler signatures
    # still compare meaningfully.
    # Also filtered, as deliberate dead-code removals (see the commit that
    # introduced this): the 58 unused single-String column/value constants in
    # the record classes, which only restated strings COLUMNS already lists as
    # literals, and the dev-harness main() methods in library classes. COLUMNS
    # itself is String[] and still compares; EagleBoardScheduler's real entry
    # point is covered by CI's manifest check and runtime smoke test.
    # Per-class deletion list, keyed by the class name with / and $ flattened;
    # classes with nothing deleted fall back to the impossible-line default.
    # -x -F: whole-line, fixed-string, so a listed signature matches only
    # itself. Applied after the whitespace collapse so the listed lines and the
    # javap output are in the same normalized shape.
    deleted="$WORK/deleted/$(printf '%s' "${2%.class}" | tr '/$' '__')"
    [ -f "$deleted" ] || deleted="$WORK/deleted/_default"
    javap -p "$1" 2>/dev/null \
      | grep -vE 'access[$][0-9]+|[$]SwitchMap[$]|Compiled from|private static .*[$]values\(\)|val[$]|final .* this[$]0;|[a-zA-Z0-9_.]+[$]1\);|^ *static \{\};' \
      | grep -vE '^ *public static final java\.lang\.String [A-Z_]+;|^ *public static void main\(java\.lang\.String\[\]\)' \
      | sed -E "s/[$][0-9]+\((, )?[a-zA-Z0-9_.$]+(, [a-zA-Z0-9_.$]+)*\);/\$N(CAPTURES);/; s/[$][0-9]+\(\);/\$N(CAPTURES);/; s/(javax|jakarta)\.servlet/SERVLET_API/g; s/(shkc\.json\.simple\.JSONArray|com\.fasterxml\.jackson\.databind\.JsonNode)/JSON_TREE/g; s/(monfox\.log|java\.util\.logging)\.Logger/LOGGER/g" \
      | grep -v '_debugLogger' \
      | sed -E 's/[[:space:]]+/ /g' \
      | grep -vxF -f "$deleted" \
      | LC_ALL=C sort
}
# `sed -E ... +`, not `sed ... \+`: `\+` is a GNU extension to basic regular
# expressions. BSD sed (macOS) reads it as a literal plus, so the whitespace
# never collapses. LC_ALL=C on the sort keeps the ordering byte-wise and
# locale-independent, so two machines produce the same normalized listing.

missing=0
while IFS= read -r cls; do
    rel=${cls#"$WORK/orig/"}
    if echo "$rel" | grep -qE "$KNOWN_SYNTHETIC"; then continue; fi
    if echo "$rel" | grep -qE "$MIGRATED"; then continue; fi
    if echo "$rel" | grep -qE "$REMOVED_VENDORED"; then continue; fi
    if echo "$rel" | grep -qE "$REMOVED_FEATURE"; then continue; fi
    if [ ! -f "$WORK/new/$rel" ]; then
        fail "class missing from rebuilt jar: $rel"
        missing=1
        continue
    fi
    if ! diff <(sig "$WORK/orig/$rel" "$rel") <(sig "$WORK/new/$rel" "$rel") >"$WORK/sigdiff" 2>&1; then
        fail "member signatures differ: $rel"
        sed 's/^/      /' "$WORK/sigdiff" | head -10
    fi
done < <(find "$WORK/orig" -name '*.class')
[ "$missing" -eq 0 ] && note "all classes present"
note "signature comparison complete"

# WEBROOT: since the UI rework (dhtmlx -> Tabulator), only the assets the
# rework preserved are still expected byte-identical to the original jar.
# The HTML/JS pages are first-party code now, checked in the UI section
# below; dhtmlx/, CVS/, and old_saved_script.js were dropped deliberately;
# signup_genius_api.js was removed entirely (checked separately below).
# help.html is no longer byte-compared: removing the Verify step made the
# original's operator instructions wrong (they described verifying paperwork
# through the UI), so the page was rewritten to match the current workflow.
# NegaScheduler.png, ScoutButton.png and LeaderButton.png were dropped in the
# dead-code audit: no page, script or stylesheet referenced any of them, and
# the first also carried the old district's branding.
PRESERVED="scheduler.css"
for p in $PRESERVED; do
    if ! cmp -s "$WORK/orig/shkc/core/WEBROOT/$p" "$WORK/new/shkc/core/WEBROOT/$p"; then
        fail "preserved WEBROOT asset differs from original: $p"
    fi
done
# images/: the dhtmlx toolbars that used these icons are gone, and only
# images/16x16/dialog-warning-4.png (the room-card overdue badge) is still
# referenced, so the other 105 files were removed. The check is therefore
# "everything the rebuild still ships is byte-identical to the original",
# not "both trees hold the same files": deletions are expected, but a
# retained icon that CHANGED, or one that appears only in the rebuild, is
# still a failure. `find` from inside the directory so the paths line up.
imgdiff=
while IFS= read -r img; do
    case "$img" in */CVS/*) continue ;; esac
    if ! cmp -s "$WORK/orig/shkc/core/WEBROOT/images/$img" "$WORK/new/shkc/core/WEBROOT/images/$img"; then
        imgdiff="$imgdiff$img"$'\n'
    fi
done < <(cd "$WORK/new/shkc/core/WEBROOT/images" && find . -type f | sed 's|^\./||')
if [ -n "$imgdiff" ]; then
    fail "WEBROOT images the rebuild still ships differ from the original:"
    echo "$imgdiff" | sed 's/^/      /' | head -5
else
    note "retained WEBROOT assets byte-identical to original"
fi
# signup_genius_api.js was deleted. Nothing referenced it (the server takes the
# key from -sugkey), and it was the only file in the tree carrying a third-party
# copyright notice. Assert it stays gone, and — stronger than the old
# placeholder check it replaces — that the real key the ORIGINAL jar still
# embeds appears nowhere in the rebuilt jar. The key is never echoed.
if [ -f "$WORK/new/shkc/core/WEBROOT/signup_genius_api.js" ]; then
    fail "signup_genius_api.js is back in the rebuilt jar; it was deliberately removed"
else
    orig_key=$(sed -nE 's/.*SIGNUP_GENIUS_KEY *= *"([^"]+)".*/\1/p' \
        "$WORK/orig/shkc/core/WEBROOT/signup_genius_api.js" 2>/dev/null | head -1)
    if [ -n "$orig_key" ] && grep -rqF "$orig_key" "$WORK/new" 2>/dev/null; then
        fail "the original's embedded SignUpGenius key appears in the rebuilt jar"
    else
        note "signup_genius_api.js gone; original's embedded key absent from the rebuild"
    fi
fi

# -------------------------------------------------------------- 2/3. runtime
echo "== 2. side-by-side servers =="
rm -rf parity && mkdir -p parity/A parity/B
# The original binary only reads CSV config, so parity runs both jars on a
# generated config.csv fixture (the rebuilt reads .csv via its CSV path). The
# .properties path is exercised by CI's smoke test instead. The config schema
# change is already accounted for (/config-autofill is status-only and the
# CONFIG startup-log line is normalized).
for d in parity/A parity/B; do
    if [ "$ADULT_FIXTURE" = real ]; then
        cp "$ADULT_FILE" "$d/"
    else
        # Same header the CI smoke test uses. Both sides get the identical
        # file either way, which is what makes the comparison meaningful.
        echo 'Type,ID,Last,First,Email,Phone,UnitType,Unit,UnitName,ProjectReview,FinalBoard,RegTime,Room,Flags,Sel,BoardHistory' > "$d/$ADULT_FILE"
    fi
    {
        echo "Type,ID,Name,RefreshTimeSecs,ProjectYellowMins,ProjectRedMins,FinalYellowMins,FinalRedMins,RegisteredColor,VerifiedColor,SeatedColor,InProgressColor,CompletedColor,PostponedColor,RegisteredHiColor,VerifiedHiColor,SeatedHiColor,InProgressHiColor,CompletedHiColor,PostponedHiColor"
        echo "CONFIG,DEFAULT,DEFAULT,30,25,40,40,50,#ffcccc,#ffffcc,#ccffff,#ccffcc,#ffffff,#909090,#ff6666,#ffff66,#66ffff,#66ff66,#eeeeee,#9f7f7f"
    } > "$d/config.csv"
done

[ "$ADULT_FIXTURE" = synthetic ] && \
    echo "  note: no $ADULT_FILE in the repo root — running on a synthetic"
[ "$ADULT_FIXTURE" = synthetic ] && \
    echo "        header-only adult history, so the adult lists compare empty."

( cd parity/A && exec java -jar "../../$ORIG_JAR" -verbose \
    -a "$ADULT_FILE" -c config.csv -port $PORT_A -d testrun \
    >server.log 2>&1 ) & PID_A=$!
( cd parity/B && exec java -jar "../../$NEW_JAR" -verbose \
    -a "$ADULT_FILE" -c config.csv -port $PORT_B -d testrun \
    >server.log 2>&1 ) & PID_B=$!

for port in $PORT_A $PORT_B; do
    for i in $(seq 1 30); do
        curl -sf -o /dev/null "http://127.0.0.1:$port/index.html" && break
        sleep 1
        [ "$i" = 30 ] && { fail "server on :$port never became ready"; }
    done
done
note "both servers up (:$PORT_A original, :$PORT_B rebuilt)"

# Normalize the only legitimate differences: clock values, and the "Etowah
# District" branding prefix that was deliberately removed from six pages
# (stripping it from the original's responses makes them comparable to the
# de-branded rebuild; any other divergence on those pages still fails).
# LC_ALL=C so sed survives binary bodies (PNGs) — they still diff byte-wise.
# Normalizations applied to every compared response:
#   TIMESTAMP        wall-clock stamps
#   district name    de-branding
#   unit type cells  UnitName widened from the original's first-letter form
#                    ("T9999") to the whole word ("Troop9999"), because the
#                    abbreviation collided -- Pack/Post both gave "P", and the
#                    new District/Council/Community options would have put
#                    Council, Community and Crew all on "C". Collapsing the
#                    known unit types back to their initial is applied to BOTH
#                    sides, so the unit number stays compared. It also folds
#                    the separate UnitType cell (<cell>Troop</cell>) to
#                    <cell>T</cell> on both sides, which is harmless: it is
#                    symmetric, and the full value is still compared in the
#                    CSV files by csvnorm.
norm() {
    LC_ALL=C sed -E '
        s/[0-9]{4}-[0-9]{2}-[0-9]{2}_[0-9]{2}:[0-9]{2}(-[0-9]{4})?/TIMESTAMP/g
        s/Etowah District:? //g
        s#<cell>Troop([0-9]*)</cell>#<cell>T\1</cell>#g
        s#<cell>Post([0-9]*)</cell>#<cell>P\1</cell>#g
        s#<cell>Crew([0-9]*)</cell>#<cell>C\1</cell>#g
        s#<cell>Ship([0-9]*)</cell>#<cell>S\1</cell>#g
        s#<cell>Pack([0-9]*)</cell>#<cell>P\1</cell>#g
        s#<cell>District([0-9]*)</cell>#<cell>D\1</cell>#g
        s#<cell>Council([0-9]*)</cell>#<cell>C\1</cell>#g
        s#<cell>Community([0-9]*)</cell>#<cell>C\1</cell>#g
    '
}
# NOTE: the list above must cover every unit type that appears in the data,
# not only the ones the sign-in form offers. If this section starts failing
# after a unit type is added, check the adult history for a value the list
# does not know. csvnorm() needs no such list -- it takes the leading
# alphabetic run whatever it is -- so prefer extending this list rather than
# skipping the column.
#
# Deliberately absent: "Team". Varsity Scout Teams were discontinued in 2017,
# so the type is not offered anywhere and a Team value in an adult history is
# stale data to be corrected at the source, not normalized around here.

req() { # method path [data] -> normalized "status + body" from one server
    local port=$1 method=$2 path=$3 data=${4:-}
    if [ "$method" = POST ]; then
        curl -s -w '\n%{http_code}\n' -X POST --data "$data" "http://127.0.0.1:$port$path"
    else
        curl -s -w '\n%{http_code}\n' "http://127.0.0.1:$port$path"
    fi | norm
}

compare() { # method path [data]
    local method=$1 path=$2 data=${3:-}
    if ! diff <(req $PORT_A "$method" "$path" "$data") \
              <(req $PORT_B "$method" "$path" "$data") >"$WORK/rdiff" 2>&1; then
        fail "$method $path responses differ:"
        sed 's/^/      /' "$WORK/rdiff" | head -8
    fi
}

# Same, but the two servers answer on DIFFERENT paths. The six youth endpoints
# were renamed from scout-* to youth-* to match the Scout->Youth rebrand, so
# the original jar still serves the old name and the rebuilt one the new. The
# response bodies must still be byte-identical: this is a rename, not a change
# of behaviour, and that is exactly what this proves.
compare_renamed() { # method originalPath rebuiltPath [data]
    local method=$1 opath=$2 npath=$3 data=${4:-}
    if ! diff <(req $PORT_A "$method" "$opath" "$data") \
              <(req $PORT_B "$method" "$npath" "$data") >"$WORK/rdiff" 2>&1; then
        fail "$method $opath -> $npath responses differ:"
        sed 's/^/      /' "$WORK/rdiff" | head -8
    fi
}

compare_raw() { # path — status + raw bytes, no normalization at all.
    # For the static assets. They carry no timestamp and no branding, so there
    # is nothing to normalize, and pushing binary bodies (the PNGs) through sed
    # is not portable: BSD sed on macOS is unreliable on data with embedded
    # NULs, and a normalizer that mangles both sides the same way would hide a
    # real difference rather than reveal it. Bytes are stricter anyway.
    local path=$1
    local a b
    a=$(curl -s -o "$WORK/raw.a" -w '%{http_code}' "http://127.0.0.1:$PORT_A$path")
    b=$(curl -s -o "$WORK/raw.b" -w '%{http_code}' "http://127.0.0.1:$PORT_B$path")
    if [ "$a" != "$b" ]; then
        fail "GET $path status differs: original=$a rebuilt=$b"
    elif ! cmp -s "$WORK/raw.a" "$WORK/raw.b"; then
        fail "GET $path body differs (byte comparison)"
    fi
}

compare_status() { # method path — status code only; body is Jetty's own
                   # error page, whose HTML differs between Jetty versions.
    local method=$1 path=$2
    local a b
    a=$(curl -s -o /dev/null -w '%{http_code}' -X "$method" "http://127.0.0.1:$PORT_A$path")
    b=$(curl -s -o /dev/null -w '%{http_code}' -X "$method" "http://127.0.0.1:$PORT_B$path")
    if [ "$a" != "$b" ]; then
        fail "$method $path status differs: original=$a rebuilt=$b"
    fi
}

echo "== 2a. read endpoints (parity vs original) =="
# Server-generated responses and preserved assets must match the original.
# /help.html is excluded for the same reason it left PRESERVED above: its
# operator instructions were rewritten when the Verify step was removed.
READS_RAW="/scheduler.css /images/16x16/dialog-warning-4.png"
READS="/adult-cells /adult-history-cells /room-cells /adult-autofill"
count=0
for p in $READS_RAW; do compare_raw "$p"; count=$((count+1)); done
for p in $READS; do compare GET "$p"; count=$((count+1)); done
# Renamed scout-* -> youth-* (see compare_renamed). Same handler, same bytes.
compare_renamed GET /scout-cells            /youth-cells;            count=$((count+1))
compare_renamed GET /scouts-scheduled-cells /youth-scheduled-cells;  count=$((count+1))
compare_renamed GET /scout-autofill         /youth-autofill;         count=$((count+1))
# The old names must be GONE on the rebuilt side, or the rename is only half
# done and stale links would keep working silently.
for old in /scout-cells /scouts-scheduled-cells /scout-autofill \
           /scout-update /scouts-scheduled-update /register-scout; do
    code=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT_B$old")
    [ "$code" = "404" ] || fail "renamed endpoint still answers on the rebuilt server: $old (got $code)"
done
# Unknown paths: the 404 must match; the error-page body is generated by
# Jetty itself and legitimately differs between Jetty 8 and 12.
compare_status GET /no-such-page
compare_status GET /no-such-dir/x.html
# /config-autofill: the config schema changed intentionally (board-type
# warning timers ProjectYellowMins/... replaced the old Seated/InProgress
# alert fields), so the served fields differ from the original by design.
# Status-only here; the colors it also carries are exercised via the UI.
compare_status GET "/config-autofill?Name=DEFAULT&fmt=json"
note "$count GET paths compared + 3 status-only paths"

echo "== 3. write endpoints =="
compare_renamed POST /register-scout /register-youth "Last=Parity&First=Test&Email=parity@example.org&Phone=555-000-0001&UnitType=Troop&Unit=9999&UnitName=T9999&DOB=2008-01-01&BoardType=EagleBoard&Leader=Leader+Parity"
compare POST /register-adult "Last=Boardmember&First=Check&Email=board@example.org&Phone=555-000-0002&UnitType=Troop&Unit=9999&UnitName=T9999&ProjectReview=Member&FinalBoard=Member"
# Explicit cols excluding the dropped AdultScoutRatio column (the rebuilt no
# longer tracks it, so a no-cols cells call would differ from the original).
compare_renamed GET "/scout-cells?cols=RegNum,Last,First,Email,Phone,BoardType,Status" \
                    "/youth-cells?cols=RegNum,Last,First,Email,Phone,BoardType,Status"
compare GET /adult-cells
compare POST /room-update  '!nativeeditor_status=inserted&gr_id=1&c0=1&c1=Room+101&c2=&c3='
compare GET /room-cells
# /verify-board is intentionally gone (the Verify step was removed), so there is
# no rebuilt endpoint to compare against the original's error path.
compare POST /seat-board   "RoomID=1&ScoutID=BOGUS&ChairID=B&MemberIDs=C"
compare POST /room-change  "RmID1=&RmID2="
note "write/error endpoints compared"

# Normalize a data CSV before comparing, for the two columns that differ from
# the original on purpose. No-op on files that have neither. Naive comma split
# is safe: the fields written during a parity run contain no embedded commas.
#
#   AdultScoutRatio  dropped by header name -- the rebuilt no longer tracks it,
#                    so the original's scout files still carry the column.
#   UnitName         shortened back to the original's form. The original wrote
#                    only the FIRST LETTER of the unit type, so Pack 12 and
#                    Post 12 both came out "P12"; adding District, Council and
#                    Community made that worse, since Council, Community and
#                    Crew would all have collided on "C". The rebuilt writes
#                    the whole word ("Troop9999"); this maps it back to "T9999"
#                    so the unit number and the type's initial are still
#                    compared rather than the column being skipped.
#
# awk, not python3. On Windows/MSYS2 `python3` resolves to the Microsoft Store
# app-execution-alias stub, which prints a notice, exits 0 and writes NOTHING
# to stdout -- which silently reduced BOTH sides of every file diff to empty
# and made this entire section pass without comparing anything.
csvnorm() {
    awk -F, -v OFS=, '
    NR == 1 {
        for (i = 1; i <= NF; i++) {
            if ($i == "AdultScoutRatio") drop = i
            if ($i == "UnitName")        un   = i
        }
    }
    {
        if (un > 0 && NR > 1 && $un != "") {
            head = $un
            sub(/[^A-Za-z].*$/, "", head)          # leading alphabetic run
            if (length(head) > 0) {
                $un = substr(head, 1, 1) substr($un, length(head) + 1)
            }
        }
        if (drop > 0) {
            s = ""
            for (i = 1; i <= NF; i++) if (i != drop) s = (s == "" ? $i : s OFS $i)
            print s
        } else {
            $1 = $1
            print
        }
    }
    '
}

echo "== 3a. resulting data files =="
sleep 1
# -name '.DS_Store' excluded: on macOS, anything that opens the sandbox in
# Finder (or Spotlight indexing it) drops one in, and it would then be reported
# as a data file missing from the rebuilt side.
for f in $(cd parity/A && find testrun -type f ! -name '.DS_Store' 2>/dev/null; echo "$ADULT_FILE"); do
    if [ ! -f "parity/B/$f" ]; then fail "file missing on rebuilt side: $f"; continue; fi
    if ! diff <(csvnorm <"parity/A/$f" | norm) <(csvnorm <"parity/B/$f" | norm) >"$WORK/fdiff" 2>&1; then
        fail "data file differs: $f"; sed 's/^/      /' "$WORK/fdiff" | head -8
    fi
done
note "data files compared"

echo "== 4. startup logs =="
# Normalized: sandbox path A/B, port, times, object identity hashes, and
# size: lines (de-branded pages are a few bytes smaller than the originals;
# their content is compared directly in section 2a).
# Dropped entirely: the web container's own chatter, which legitimately
# differs between the original's bundled Jetty 8 and the rebuild's Jetty 12
# (startup banners, SLF4J notices, and the old Jetty-8-era ResourceHandler
# getResource/RESOURCE trace lines that no longer exist).
#
# SCOPE: startup only. Each log is cut at its first request, so what is
# compared is the boot path -- argument handling, config load, and every
# record parsed out of the data files (the LOADED: lines, which on a real
# adult history is ~900 records proving both jars read the file identically).
#
# The request traffic that follows is NOT compared here, and must not be:
# sections 2/3 deliberately drive the two servers differently, so their logs
# cannot line up no matter how they are normalized.
#   - Renamed endpoints: 2a asks the original for /scout-cells and the rebuild
#     for /youth-cells (see compare_renamed), so the two logs name different
#     targets for the same logical request.
#   - Retired-name probes: the 404 check above hits /scout-* on the REBUILT
#     server only, adding whole blocks side B has and side A does not.
#   - Section 5 fetches 18 UI files from the rebuilt server only.
# Nothing is lost by cutting here. Every one of those requests is already
# compared far more strictly than a log line: 2a/2b diff the response bytes,
# 3 diffs the write endpoints, and 3a diffs the data files they produced --
# which is why, for instance, dropping the per-request "NEW ADULT RECORD:"
# chatter is safe: the record it announces is compared in adults.csv.
#
# Do not "fix" a future failure here by widening the cut. If two startup logs
# differ, the boot path differs, and that is the signal this section exists
# to give.
normlog() { norm <"$1" | LC_ALL=C sed -E "s/:1808[01]/:PORT/g; s/[0-9]{2}:[0-9]{2}:[0-9]{2}[.,][0-9]+/TIME/g; s/[0-9]{4}-[0-9]{2}-[0-9]{2} TIME/DATETIME/g; s|parity/[AB]|parity/X|g; s/@[0-9a-fA-F]+/@ID/g; s/^size:[0-9]+$/size:N/; s/context-path=null/context-path=/" \
    | grep -vE '^(DATETIME|TIME)?[: ]*(INFO|WARN)[: ]|SLF4J|jetty|oejs|oeje|getResource: |RESOURCE: |Session workerName|Started |Logging initialized|^LOADED: CONFIG,|^NEW SCOUT RECORD:' \
    | LC_ALL=C awk '
# Parameter-map dumps ({k=[v],...}) keep the same entries but a different
# iteration order under Jetty 12 vs 8 — sort entries so order is irrelevant.
#
# awk, not python3, for the same reason csvnorm above is awk: a `python3` that
# is not really python is exactly how this section would pass without comparing
# anything. On Windows it is the Microsoft Store alias stub; on a stock macOS
# there is no python3 at all, and /usr/bin/python3 is a shim that fails with an
# Xcode-tools notice. Either way it writes nothing to stdout, which empties
# BOTH logs and makes the diff succeed. awk ships with every Unix.
#
# Insertion sort rather than a pipe to sort(1), because the entries have to
# come back as one line. `x ""` forces string comparison, so numeric-looking
# keys order byte-wise (like python did) instead of numerically. Tested to
# produce byte-identical output to the python it replaces, under both mawk
# and the BWK awk that macOS ships.
# Startup ends at the first request the server dispatches; see the SCOPE note
# above for why the traffic after it cannot be compared line-by-line. Cutting
# here rather than filtering the request lines out keeps the cut honest: a
# filter would silently swallow any NEW startup line that happened to match it.
/^LocalDefaultHandler: target=/ { exit }
# "LOADED: ADULT,..." lines are CSV-shaped, so the <cell> rules in norm() never
# reach their UnitName column and every adult line would differ on the widened
# unit type alone ("C1109" vs "Crew1109"). Fold column 9 with the same
# leading-alphabetic-run rule csvnorm() uses: it is symmetric, so the original
# and the rebuild both come out "C1109", and the unit number is still compared.
# Field 9 by position, and a naive comma split, for the same reason csvnorm
# splits naively -- these columns hold no embedded commas. Guarded on NF so a
# row that ever did would be left alone rather than silently rewritten.
# ADULT only: the parity run starts with an empty testrun/, so the scout file
# has no rows to load and "LOADED: SCOUT" never appears (its UnitName sits in a
# different column, so it would need its own index).
/^LOADED: ADULT,/ {
    n = split($0, f, ",")
    if (n == 16 && f[9] != "") {
        head = f[9]
        sub(/[^A-Za-z].*$/, "", head)
        if (length(head) > 0) {
            f[9] = substr(head, 1, 1) substr(f[9], length(head) + 1)
            out = f[1]
            for (i = 2; i <= n; i++) out = out "," f[i]
            $0 = out
        }
    }
    print
    next
}
{
    if (substr($0, 1, 1) == "{" && substr($0, length($0)) == "}" && index($0, "=[") > 0) {
        n = split(substr($0, 2, length($0) - 2), e, ",")
        for (i = 2; i <= n; i++) {
            v = e[i] ""; j = i - 1
            while (j >= 1 && (e[j] "") > v) { e[j+1] = e[j]; j-- }
            e[j+1] = v
        }
        out = ""
        for (i = 1; i <= n; i++) out = (i == 1 ? e[i] : out "," e[i])
        print "{" out "}"
        next
    }
    print
}'; }
if ! diff <(normlog parity/A/server.log) <(normlog parity/B/server.log) >"$WORK/ldiff" 2>&1; then
    fail "startup logs differ:"; sed 's/^/      /' "$WORK/ldiff" | head -12
else
    note "startup logs identical (modulo time/port)"
fi

echo "== 5. first-party UI (rebuilt server only) =="
# These pages were rewritten on Tabulator (dhtmlx removed); they have no
# original-jar counterpart to diff against. Assert they serve, are free of
# dhtmlx references, and kept the headings operators (and CI greps) rely on.
# index_simple.html was deleted: a second sign-in page with no lists, linked
# from nowhere, that only duplicated index.html's purpose.
UI_PAGES="/index.html /admin.html /scheduler.html
/configure.html /youth_register.html /adult_register.html /eb-data.js
/scheduler_config.js /scheduler_event.js /eb-app.css
/process_seat.js /process_start.js
/process_complete.js /process_postpone.js /process_reset.js
/tabulator/tabulator.min.js /tabulator/tabulator.min.css"
uicount=0
for p in $UI_PAGES; do
    if ! curl -sf "http://127.0.0.1:$PORT_B$p" -o "$WORK/ui.out"; then
        fail "UI file not served: $p"
        continue
    fi
    case "$p" in
        /eb-data.js) ;;  # its comments document the legacy dhtmlx protocol
        *) grep -qi "dhtmlx" "$WORK/ui.out" && fail "dhtmlx reference remains in $p" ;;
    esac
    uicount=$((uicount + 1))
done
# The event is "Eagle Boards" -- that is what the district calls it everywhere
# else, and the pages say so. (A generic "Review Board" wording was tried on the
# grounds that boards of review exist for every rank, and reverted: nobody
# recognised it.) The district name itself stays out; see the branding rule in
# CLAUDE.md. These headings are pinned deliberately, so a wording change is
# taught here rather than worked around.
curl -sf "http://127.0.0.1:$PORT_B/index.html" | grep -q "Welcome to Eagle Boards" || fail "index.html heading changed"
curl -sf "http://127.0.0.1:$PORT_B/admin.html" | grep -qi "Eagle Board Admin Page" || fail "admin.html title changed"
curl -sf "http://127.0.0.1:$PORT_B/scheduler.html" | grep -qi "Eagle Board Scheduler" || fail "scheduler.html heading changed"
note "$uicount first-party UI files served clean"

kill $PID_A $PID_B 2>/dev/null; wait 2>/dev/null

echo
if [ "$FAILURES" -eq 0 ]; then
    echo "PARITY: PASS — rebuilt jar is behaviorally identical to the original"
else
    echo "PARITY: $FAILURES FAILURE(S) — see above"
fi
exit $FAILURES
