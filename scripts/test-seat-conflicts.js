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
var checkProjectSize = seat.checkProjectSize;
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

// A project proposal review is the GTA 9.0.2.4 approval of the service project
// proposal, not a board of review, so the three-member floor does not apply --
// this district runs them with two. The ceiling is shared: six either way.
console.log("== project review size (two to six members) ==");

check("no members at all is refused", checkProjectSize(0), "too-few");
check("one member is refused", checkProjectSize(1), "too-few");
check("two members is the district's working size", checkProjectSize(2), "ok");
check("three members is legal, confirm first", checkProjectSize(3), "over-preferred");
check("six members is the maximum, still legal", checkProjectSize(6), "over-preferred");
check("seven members is refused", checkProjectSize(7), "too-many");
check("a wildly oversized project review is refused", checkProjectSize(20), "too-many");

// The two rules differ only at the floor; a mix-up there would quietly seat
// two-member boards of review, which is the failure this pins down.
check("three is legal for a board but only 'preferred-plus' for a project",
   [checkBoardSize(3), checkProjectSize(3)], ["ok", "over-preferred"]);
check("two is refused for a board but fine for a project",
   [checkBoardSize(2), checkProjectSize(2)], ["too-few", "ok"]);

// ------------------------------------------------------------------------
// Auto-select (proposeBoard). The same cases are in the Windows version's
// SchedulerLogicTests and the Mac version's BoardRulesTests; keep all three
// in step.
// ------------------------------------------------------------------------
var proposeBoard = seat.proposeBoard;

function poolAdult(id, uname, final, project, room) {
   return { id: id, uname: uname, final: final, project: project, room: room || "" };
}

function queueScout(id, uname, btype) {
   return { id: id, uname: uname, btype: btype };
}

console.log("== auto-select keeps chairs for the boards that need them ==");

var p = proposeBoard(queueScout("S", "Troop1001", "Final"), [
   poolAdult("FC", "Troop9001", "Chair", "Member"),
   poolAdult("PC", "Troop9002", "Member", "Chair"),   // a Final member, but a Project chair
   poolAdult("M1", "Troop9003", "Member", "Member"),
   poolAdult("M2", "Troop9004", "Member", "Member")
], []);
check("member-only adults fill the member seats, not a project chair",
   [p.chairId, p.memberIds, p.problems], ["FC", ["M1", "M2"], []]);

p = proposeBoard(queueScout("S", "Troop1001", "Final"), [
   poolAdult("BOTH", "Troop9001", "Chair", "Chair"),
   poolAdult("FC", "Troop9002", "Chair", "Member"),
   poolAdult("M1", "Troop9003", "Member", "Member"),
   poolAdult("M2", "Troop9004", "Member", "Member")
], []);
check("a chair who can chair only this kind is used before one who can chair both",
   p.chairId, "FC");

p = proposeBoard(queueScout("S", "Troop1001", "Final"), [
   poolAdult("FC", "Troop9001", "Chair", "Member"),
   poolAdult("PC", "Troop9002", "Member", "Chair"),
   poolAdult("M1", "Troop9003", "Member", "Member")
], []);
check("when member-only adults run out, a chair-capable adult fills the seat",
   [p.chairId, p.memberIds, p.problems], ["FC", ["M1", "PC"], []]);

console.log("== auto-select weighs the troops of the scouts still waiting ==");

// B is listed before A, so sign-in order alone would give this board B and
// leave A -- who shares the next scout's troop -- as that scout's only member.
p = proposeBoard(queueScout("S", "Troop1001", "Project"), [
   poolAdult("P1", "Troop3001", "Member", "Chair"),
   poolAdult("P2", "Troop3002", "Member", "Chair"),
   poolAdult("B", "Troop4001", "Member", "Member"),
   poolAdult("A", "Troop2001", "Member", "Member")
], [queueScout("T", "Troop2001", "Project")]);
check("an adult who cannot serve the next scout's troop is used here instead",
   [p.chairId, p.memberIds], ["P1", ["A"]]);

// Seating a waiting scout now outranks keeping a chair for later: the only
// way to leave T a full board is to give S the project chair from T's troop.
p = proposeBoard(queueScout("S", "Troop1001", "Final"), [
   poolAdult("FC1", "Troop3001", "Chair", "Member"),
   poolAdult("FC2", "Troop3002", "Chair", "Member"),
   poolAdult("M", "Troop3003", "Member", "Member"),
   poolAdult("Y", "Troop2001", "Member", "Chair"),
   poolAdult("Z", "Troop2001", "Member", "Member"),
   poolAdult("W", "Troop3004", "Member", "Member")
], [queueScout("T", "Troop2001", "Final")]);
check("a scout still waiting who can be seated outranks a chair kept for later",
   [p.chairId, p.memberIds], ["FC1", ["Z", "Y"]]);

console.log("== auto-select never proposes someone who cannot sit ==");

p = proposeBoard(queueScout("S", "Troop1001", "Final"), [
   poolAdult("SAME", "Troop1001", "Chair", "Chair"),
   poolAdult("BUSY", "Troop9001", "Chair", "Chair", "101"),
   poolAdult("GONE", "Troop9002", "Chair", "Chair", "N/A"),
   poolAdult("NOPE", "Troop9003", "Unavailable", "Member"),
   poolAdult("FC", "Troop9004", "Chair", "Member"),
   poolAdult("M1", "Troop9005", "Member", "Member"),
   poolAdult("M2", "Troop9006", "Member", "Member")
], []);
check("not the scout's unit, not busy, not gone home, not Unavailable",
   [p.chairId, p.memberIds], ["FC", ["M1", "M2"]]);

p = proposeBoard(queueScout("S", "Troop1001", "Final"), [
   poolAdult("M1", "Troop9001", "Member", "Member"),
   poolAdult("M2", "Troop9002", "Member", "Member")
], []);
check("with no chair it still proposes the members, and says so",
   [p.chairId, p.memberIds, p.problems], [null, ["M1", "M2"], ["No Final Chairs Available."]]);

p = proposeBoard(queueScout("S", "Troop1001", "Final"), [
   poolAdult("FC", "Troop9001", "Chair", "Member"),
   poolAdult("M1", "Troop9002", "Member", "Member")
], []);
check("with too few members it proposes what there is, and says so",
   [p.chairId, p.memberIds, p.problems], ["FC", ["M1"], ["Only 1 Final Members Available"]]);

console.log("== a whole event: five chairs, five boards at once ==");

// The shape of scripts/test-board-event.sh: 9 Final and 5 Project scouts,
// 30 adults of whom only five chair anything -- one either kind, two Final
// only, two Project only (and those two are plain Members of a Final board).
// Proposing boards down the queue must reach the chair cap of five. Picking
// in sign-in order gave the first Final board both project chairs as its
// members, and the event stalled at three.
var pool = [
   poolAdult("FC1", "Troop2001", "Chair", "Chair"),
   poolAdult("FC2", "Troop2002", "Chair", "Member"),
   poolAdult("FC3", "Troop2003", "Chair", "Member"),
   poolAdult("PC1", "Troop2004", "Member", "Chair"),
   poolAdult("PC2", "Troop2005", "Member", "Chair")
];
for (var n = 6; n <= 25; n++) {
   pool.push(poolAdult("M" + n, "Troop" + (2000 + n), "Member", "Member"));
}
pool.push(poolAdult("U26", "Troop2026", "Member", "Unavailable"));
pool.push(poolAdult("U27", "Troop2027", "Member", "Unavailable"));
pool.push(poolAdult("U28", "Troop1001", "Unavailable", "Member"));
pool.push(poolAdult("U29", "Troop1002", "Unavailable", "Member"));
pool.push(poolAdult("U30", "Troop1003", "Unavailable", "Member"));

var queue = [];
for (var q = 1; q <= 14; q++) {
   queue.push(queueScout("S" + q, "Troop" + (1000 + q), q <= 9 ? "Final" : "Project"));
}

var boards = { Final: 0, Project: 0 };
var seatedIds = {};
for (var s = 0; s < queue.length; s++) {
   var stillWaiting = queue.filter(function (t) { return t !== queue[s] && !seatedIds[t.id]; });
   var proposal = proposeBoard(queue[s], pool, stillWaiting);
   if (proposal.problems.length === 0) {
      seatedIds[queue[s].id] = true;
      boards[queue[s].btype]++;
      [proposal.chairId].concat(proposal.memberIds).forEach(function (id) {
         pool.forEach(function (a) {
            if (a.id === id) {
               a.room = "R" + s;
            }
         });
      });
   }
}
check("five boards seat at once: three Final, two Project", boards, { Final: 3, Project: 2 });

console.log("== the adults who have waited longest to volunteer go first ==");

var freeSinceTimes = seat.freeSinceTimes;
var since = freeSinceTimes([
   { id: "ADULT:Able:Ann:1", regTime: "2026-09-24_19:00-0400" },
   { id: "ADULT:Baker:Bo:2", regTime: "2026-09-24_19:10-0400" },
   { id: "ADULT:Cole:Cy:3", regTime: "2026-09-24_19:05-0400" },
   { id: "ADULT:Whitmore~ Jr.:Lysander:4", regTime: "2026-09-24_19:00-0400" },
   { id: "ADULT:Lee:Al:1", regTime: "2026-09-24_19:00-0400" }
], [
   { status: "Completed", memberIds: "ADULT:Able:Ann:1,ADULT:Other:Oz:9", lastUpdate: "2026-09-24_19:40-0400" },
   // as it reads back after a restart: the CSV stored the commas as "~"
   { status: "Completed", memberIds: "ADULT:X:X:9~ADULT:Whitmore~ Jr.:Lysander:4~ADULT:Lee:Al:12",
     lastUpdate: "2026-09-24_19:50-0400" },
   { status: "Registered", memberIds: "", lastUpdate: "2026-09-24_20:00-0400" },   // a reset board
   { status: "Seated", memberIds: "ADULT:Cole:Cy:3", lastUpdate: "2026-09-24_20:05-0400" }
]);
check("someone who came off a completed board has waited since it finished",
   since["ADULT:Able:Ann:1"], "2026-09-24_19:40-0400");
check("someone who has not sat has waited since they signed in",
   since["ADULT:Baker:Bo:2"], "2026-09-24_19:10-0400");
check("a board still running does not count as their last one",
   since["ADULT:Cole:Cy:3"], "2026-09-24_19:05-0400");
check("a name with a comma is found in a list read back after a restart",
   since["ADULT:Whitmore~ Jr.:Lysander:4"], "2026-09-24_19:50-0400");
check("an ID that is the start of another's is not mistaken for it",
   since["ADULT:Lee:Al:1"], "2026-09-24_19:00-0400");

p = proposeBoard(queueScout("S", "Troop1001", "Final"), [
   Object.assign(poolAdult("FC", "Troop9001", "Chair", "Member"), { freeSince: "2026-09-24_19:00-0400" }),
   Object.assign(poolAdult("M1", "Troop9002", "Member", "Member"), { freeSince: "2026-09-24_19:40-0400" }),
   Object.assign(poolAdult("M2", "Troop9003", "Member", "Member"), { freeSince: "2026-09-24_19:10-0400" }),
   Object.assign(poolAdult("M3", "Troop9004", "Member", "Member"), { freeSince: "2026-09-24_19:20-0400" })
], []);
check("among equals, those who have waited longest are proposed, not the first to sign in",
   p.memberIds, ["M2", "M3"]);

p = proposeBoard(queueScout("S", "Troop1001", "Final"), [
   Object.assign(poolAdult("FC", "Troop9001", "Chair", "Member"), { freeSince: "2026-09-24_19:00-0400" }),
   Object.assign(poolAdult("PC", "Troop9002", "Member", "Chair"), { freeSince: "2026-09-24_18:30-0400" }),
   Object.assign(poolAdult("M1", "Troop9003", "Member", "Member"), { freeSince: "2026-09-24_19:30-0400" }),
   Object.assign(poolAdult("M2", "Troop9004", "Member", "Member"), { freeSince: "2026-09-24_19:35-0400" })
], []);
check("waiting longest does not outrank keeping a chair free",
   p.memberIds, ["M1", "M2"]);

console.log("== volunteers who came for any board go before a scout's own leaders ==");

function volunteer(a, fields) {
   return Object.assign(a, fields);
}

p = proposeBoard(queueScout("S", "Troop1001", "Final"), [
   poolAdult("FC", "Troop9001", "Chair", "Member"),
   volunteer(poolAdult("LEAD", "Troop9002", "Member", "Member"),
      { supporting: "SCOUT:Other:Oli:3001", freeSince: "2026-09-24_18:00-0400" }),
   volunteer(poolAdult("V1", "Troop9003", "Member", "Member"), { freeSince: "2026-09-24_19:30-0400" }),
   volunteer(poolAdult("V2", "Troop9004", "Member", "Member"), { freeSince: "2026-09-24_19:40-0400" })
], []);
check("an unattached volunteer is proposed before a scout's leader who has waited longer",
   p.memberIds, ["V1", "V2"]);

p = proposeBoard(queueScout("S", "Troop1001", "Final"), [
   poolAdult("FC", "Troop9001", "Chair", "Member"),
   volunteer(poolAdult("LEAD", "Troop9002", "Member", "Member"),
      { supporting: "SCOUT:Other:Oli:3001", freeSince: "2026-09-24_18:00-0400" }),
   volunteer(poolAdult("WB", "Troop9003", "Member", "Member"),
      { supporting: "SCOUT:Other:Oli:3001", woodBadge: "Y", freeSince: "2026-09-24_19:00-0400" }),
   volunteer(poolAdult("V", "Troop9004", "Member", "Member"), { freeSince: "2026-09-24_19:30-0400" })
], []);
check("a Wood Badge volunteer counts as here for any board, even with a scout",
   p.memberIds, ["WB", "V"]);

p = proposeBoard(queueScout("S", "Troop1001", "Final"), [
   poolAdult("FC", "Troop9001", "Chair", "Member"),
   poolAdult("PC", "Troop9002", "Member", "Chair"),   // unattached, but a Project chair
   volunteer(poolAdult("L1", "Troop9003", "Member", "Member"), { supporting: "SCOUT:A:A:1" }),
   volunteer(poolAdult("L2", "Troop9004", "Member", "Member"), { supporting: "SCOUT:B:B:2" })
], []);
check("coming for any board does not outrank keeping a chair free",
   p.memberIds, ["L1", "L2"]);

console.log("== fill the rest keeps the operator's picks (D-12) ==");
var fillBoard = seat.fillBoard;
var fillPool = [
   poolAdult("FC", "Troop9001", "Chair", "Member"),
   poolAdult("M1", "Troop9002", "Member", "Member"),
   poolAdult("M2", "Troop9003", "Member", "Member"),
   poolAdult("M3", "Troop9004", "Member", "Member"),
   poolAdult("SU", "Troop1001", "Member", "Member")      // the scout's own unit
];
var f = fillBoard(queueScout("S", "Troop1001", "Final"), fillPool, ["M3"], []);
check("a picked member with no chair gets a chair added", f.chairId, "FC");
check("then members up to three, never re-adding the pick", f.memberIds, ["M1"]);
check("and nothing is short", f.problems, []);

f = fillBoard(queueScout("S", "Troop1001", "Final"), fillPool, ["FC"], []);
check("a picked chair is kept as the chair: no chair added", f.chairId, null);
check("two members added beside the picked chair", f.memberIds, ["M1", "M2"]);

f = fillBoard(queueScout("S", "Troop1001", "Final"), fillPool, ["FC", "M1", "M2"], []);
check("a full board adds nobody", [f.chairId, f.memberIds, f.problems], [null, [], []]);

f = fillBoard(queueScout("S", "Troop1001", "Final"), fillPool, ["M1", "M2", "M3"], []);
check("three members and no chair: a chair is still added", f.chairId, "FC");
check("and no more members", f.memberIds, []);

f = fillBoard(queueScout("S", "Troop1001", "Final"), [
   poolAdult("M1", "Troop9002", "Member", "Member"),
   poolAdult("M2", "Troop9003", "Member", "Member")
], ["M1"], []);
// As in the Windows FillBoard: with no chair to add, the chair's seat is
// counted as a member seat too, so it asks for one more than it finds.
check("no chair free: says so, and fills the members it can",
   [f.chairId, f.memberIds, f.problems],
   [null, ["M2"], ["No Final Chairs Available.", "Only 1 Final Members Available"]]);

f = fillBoard(queueScout("S", "Troop1001", "Final"), [
   poolAdult("FC", "Troop9001", "Chair", "Member"),
   poolAdult("SU", "Troop1001", "Member", "Member"),
   poolAdult("BZ", "Troop9005", "Member", "Member", "101")
], ["FC"], []);
check("never adds the scout's own unit or someone already on a board",
   [f.memberIds, f.problems], [[], ["Only 0 Final Members Available"]]);

console.log("== linking an adult to a scout from the scheduler ==");

var withSupportLink = seat.withSupportLink;
check("links a scout to an adult who supports nobody yet",
   withSupportLink("", "SCOUT:A:A:1", true), "SCOUT:A:A:1");
check("adds a second scout after the first",
   withSupportLink("SCOUT:A:A:1", "SCOUT:B:B:2", true), "SCOUT:A:A:1|SCOUT:B:B:2");
check("never lists a scout twice",
   withSupportLink("SCOUT:A:A:1|SCOUT:B:B:2", "SCOUT:A:A:1", true), "SCOUT:B:B:2|SCOUT:A:A:1");
check("unlinks one scout and keeps the rest",
   withSupportLink("SCOUT:A:A:1|SCOUT:B:B:2", "SCOUT:A:A:1", false), "SCOUT:B:B:2");
check("unlinking the last scout leaves nothing",
   withSupportLink("SCOUT:A:A:1", "SCOUT:A:A:1", false), "");
check("an ID holding '~' from a comma name is kept whole",
   withSupportLink("SCOUT:Doe~ Jr.:Jan:1", "SCOUT:B:B:2", true), "SCOUT:Doe~ Jr.:Jan:1|SCOUT:B:B:2");

console.log("== finding a person's room (SPEC.md D-21) ==");

// The Windows version's test cases (SchedulerLogicTests.
// FindingAPersonSaysWhichRoomTheyAreInOrWhereTheyAreInstead), and the Mac's.
var findYouth = [
   { id: "S1", First: "Arthur", Last: "Eldred", Status: "InProgress", Room: "101" },
   { id: "S2", First: "Bill", Last: "Amend", Status: "Registered", Room: "" },
   { id: "S3", First: "Peter", Last: "Agre", Status: "Completed", Room: "N/A" },
   { id: "S4", First: "Rob", Last: "Corddry", Status: "Postponed", Room: "" }
];
var findAdults = [
   { id: "A1", First: "Neil", Last: "Armstrong", Room: "101" },
   { id: "A2", First: "Jim", Last: "Lovell", Room: "" },
   { id: "A3", First: "Charles", Last: "Duke", Room: "N/A" }
];
function say(query) {
   return seat.findPeople(query, findYouth, findAdults).map(function (p) {
      return p.name + " " + p.where + (p.room ? " [" + p.room + "]" : "");
   }).join("; ");
}
check("youth first, then adults, each by last name", say("ar"),
   "Arthur Eldred is in room 101 [101]; Neil Armstrong is in room 101 [101]; Charles Duke has gone home");
check("any part of the name", say("neil arm"), "Neil Armstrong is in room 101 [101]");
check("a youth waiting", say("bill"), "Bill Amend is waiting");
check("a youth finished, whatever the case", say("AGRE"), "Peter Agre has finished");
check("a youth postponed", say("rob c"), "Rob Corddry was postponed");
check("an adult on no board", say("lovell"), "Jim Lovell isn't on a board");
check("an adult gone home", say("Duke"), "Charles Duke has gone home");
check("an empty find, or no one, finds no one", [say("  "), say("Spielberg")], ["", ""]);

function find(query) {
   var found = seat.findPeople(query, findYouth, findAdults);
   var rooms = seat.roomsFound(query, found, ["101", "102", "200A"]);
   return [rooms, seat.personFindNote(found, rooms)];
}
check("an empty find shows every room", find(""), [null, ""]);
check("the rooms holding someone found", find("armstrong"), [["101"], ""]);
check("and a room by its name", find("20"), [["200A"], ""]);
check("with where the rest are", find("ar"), [["101"], "Charles Duke has gone home."]);
check("in no room", find("bill"), [[], "Bill Amend is waiting."]);
check("or no one at all", find("Spielberg")[1], "No one by that name has signed in.");
var crowd = [1, 2, 3, 4, 5, 6].map(function (n) {
   return { id: "W" + n, First: "Pat", Last: "Waiting" + n, Status: "Registered", Room: "" };
});
var crowdFound = seat.findPeople("pat", crowd, []);
check("four at most, then how many more", seat.personFindNote(crowdFound, seat.roomsFound("pat", crowdFound, [])),
   "Pat Waiting1 is waiting. Pat Waiting2 is waiting. Pat Waiting3 is waiting. Pat Waiting4 is waiting. And 2 more.");

console.log("");
if (failures > 0) {
   console.log("SEAT CONFLICT TESTS: FAIL — " + failures + " of " + checks + " checks failed");
   process.exit(1);
}
console.log("SEAT CONFLICT TESTS: PASS — " + checks + " checks");
