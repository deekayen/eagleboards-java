// ------------------------------------------------------------------------
// test-seat-conflicts.js — regression tests for the same-unit board rule.
//
// A board of review must not include adults from the scout's own unit. The
// scheduler warns about the overlap and lets the reviewer override it; these
// tests pin down WHICH members get flagged, because the failure that matters
// is a silent one — a same-unit adult that never gets reported still ends up
// sitting on the board.
//
// findUnitConflicts is deliberately pure (no grids, no dialogs, no DOM), so
// this runs headless with no browser and no test framework:
//
//   node scripts/test-seat-conflicts.js
//
// Runs on all three CI platforms (see .github/workflows/build.yml).
// ------------------------------------------------------------------------

var path = require("path");
var seat = require(path.join(
   __dirname, "..", "src", "main", "resources", "shkc", "core", "WEBROOT", "process_seat.js"));
var findUnitConflicts = seat.findUnitConflicts;
var checkBoardSize = seat.checkBoardSize;
var hasNonUnitMember = seat.hasNonUnitMember;

var failures = 0;
var checks = 0;

function check(what, got, want) {
   checks++;
   var g = JSON.stringify(got);
   var w = JSON.stringify(want);
   if (g === w) {
      console.log("  ok: " + what);
   } else {
      console.log("FAIL: " + what + "\n        got:  " + g + "\n        want: " + w);
      failures++;
   }
}

// Names the conflicting members, so an assertion failure says who was missed
// rather than just how many.
function names(conflicts) {
   return conflicts.map(function (c) { return c.first + " " + c.last; });
}

function adult(first, last, uname) {
   return { id: "ADULT:" + last + ":" + first, first: first, last: last, uname: uname };
}

console.log("== same-unit board member detection ==");

// The council rule itself: an adult from the scout's unit is flagged.
check("adult in the scout's unit is flagged",
   names(findUnitConflicts("Troop1234", [adult("Robin", "Sameunit", "Troop1234")])),
   ["Robin Sameunit"]);

// The everyday case — a properly composed board raises nothing at all.
check("board with no unit overlap is clean",
   names(findUnitConflicts("Troop1234", [
      adult("Jordan", "Otherunit", "Troop5678"),
      adult("Casey", "Otherunit", "Troop9999"),
      adult("Sam", "Otherunit", "Pack0042")
   ])),
   []);

// The bug this suite exists for: the old code returned on the FIRST match, so
// a board stacked with same-unit adults reported one name and hid the rest.
check("EVERY same-unit member is reported, not just the first",
   names(findUnitConflicts("Troop1234", [
      adult("Robin", "Sameunit", "Troop1234"),
      adult("Alex", "Sameunit", "Troop1234"),
      adult("Jordan", "Otherunit", "Troop5678")
   ])),
   ["Robin Sameunit", "Alex Sameunit"]);

// Mixed board: only the overlapping adults come back, order preserved.
check("only the overlapping members are returned",
   names(findUnitConflicts("Troop1234", [
      adult("Jordan", "Otherunit", "Troop5678"),
      adult("Robin", "Sameunit", "Troop1234"),
      adult("Casey", "Otherunit", "Troop9999")
   ])),
   ["Robin Sameunit"]);

// A whole board from the scout's unit — the worst case, fully reported.
check("an entire same-unit board is fully reported",
   names(findUnitConflicts("Troop1234", [
      adult("Robin", "Sameunit", "Troop1234"),
      adult("Alex", "Sameunit", "Troop1234"),
      adult("Jordan", "Sameunit", "Troop1234")
   ])),
   ["Robin Sameunit", "Alex Sameunit", "Jordan Sameunit"]);

console.log("== unit-name edge cases ==");

// Units are compared whole. Troop 12 must not match Troop 123, and a Pack
// and a Troop that share a number are different units.
check("a unit that is a prefix of another does not match",
   names(findUnitConflicts("Troop12", [adult("Robin", "Sameunit", "Troop123")])),
   []);

check("same number, different unit type, is not a match",
   names(findUnitConflicts("Troop1234", [adult("Robin", "Otherunit", "Pack1234")])),
   []);

// Blank units mean "unknown", not "the same". Comparing them directly would
// make two blanks look like one unit and warn on every board in a data set
// that is missing UnitName.
check("blank scout unit does not match a blank member unit",
   names(findUnitConflicts("", [adult("Robin", "Nounit", "")])),
   []);

check("known scout unit does not match a blank member unit",
   names(findUnitConflicts("Troop1234", [adult("Robin", "Nounit", "")])),
   []);

check("blank scout unit does not match a known member unit",
   names(findUnitConflicts("", [adult("Robin", "Otherunit", "Troop5678")])),
   []);

// Comparison is exact: unit names arrive already normalized by the server
// (UnitType + Unit, e.g. "Troop1234"), so case differences are real data
// differences and must not be silently equated.
check("differing case is not treated as the same unit",
   names(findUnitConflicts("Troop1234", [adult("Robin", "Sameunit", "TROOP1234")])),
   []);

console.log("== degenerate input ==");

check("no members selected yields no conflicts",
   names(findUnitConflicts("Troop1234", [])),
   []);

// The council rule (no same-unit adults) may be bypassed down to the national
// floor, but not through it: GTA 8.0.3.0 #2 requires at least one member not
// affiliated with the unit. These pin down when the bypass is offered at all.
console.log("== national floor: at least one member from outside the unit ==");

check("a board entirely from the scout's unit has no outside member",
   hasNonUnitMember("Troop1234", [
      adult("Robin", "Sameunit", "Troop1234"),
      adult("Alex", "Sameunit", "Troop1234"),
      adult("Jordan", "Sameunit", "Troop1234")
   ]),
   false);

check("one outside member is enough to meet the national floor",
   hasNonUnitMember("Troop1234", [
      adult("Robin", "Sameunit", "Troop1234"),
      adult("Alex", "Sameunit", "Troop1234"),
      adult("Jordan", "Otherunit", "Troop5678")
   ]),
   true);

check("a fully cross-unit board meets the floor",
   hasNonUnitMember("Troop1234", [
      adult("Jordan", "Otherunit", "Troop5678"),
      adult("Casey", "Otherunit", "Troop9999")
   ]),
   true);

check("a blank member unit counts as outside (unknown, not affiliated)",
   hasNonUnitMember("Troop1234", [
      adult("Robin", "Sameunit", "Troop1234"),
      adult("Pat", "Nounit", "")
   ]),
   true);

check("an unknown scout unit never blocks the bypass",
   hasNonUnitMember("", [adult("Robin", "Sameunit", "Troop1234")]),
   true);

check("same number, different unit type, counts as outside",
   hasNonUnitMember("Troop1234", [adult("Robin", "Otherunit", "Pack1234")]),
   true);

// Guide to Advancement 8.0.0.3 / 8.0.3.0 #3: "no fewer than three and no more
// than six members". The boundaries are the whole point — three and six are
// both legal, two and seven are not.
console.log("== board size (GTA 8.0.0.3: three to six members) ==");

check("no members at all is refused", checkBoardSize(0), "too-few");
check("one member is refused", checkBoardSize(1), "too-few");
check("two members is refused (below the national minimum)", checkBoardSize(2), "too-few");
check("three members is the district's working size", checkBoardSize(3), "ok");
check("four members is legal, confirm first", checkBoardSize(4), "over-preferred");
check("five members is legal, confirm first", checkBoardSize(5), "over-preferred");
check("six members is the national maximum, still legal", checkBoardSize(6), "over-preferred");
check("seven members is refused (above the national maximum)", checkBoardSize(7), "too-many");
check("a wildly oversized board is refused", checkBoardSize(20), "too-many");

console.log("");
if (failures > 0) {
   console.log("SEAT CONFLICT TESTS: FAIL — " + failures + " of " + checks + " checks failed");
   process.exit(1);
}
console.log("SEAT CONFLICT TESTS: PASS — " + checks + " checks");
