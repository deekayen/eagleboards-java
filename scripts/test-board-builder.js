// ------------------------------------------------------------------------
// test-board-builder.js — the event page's board builder, after the
// 2026-09-30 event: a proposed board following the event until the
// operator changes it (SPEC.md D-12, amended), and whom Start review names
// to introduce the youth (D-23).
//
// proposalUntouched and introductionFor are pure (no DOM), so this runs
// headless with no browser and no test framework:
//
//   node scripts/test-board-builder.js
//
// The same cases are the Windows version's SchedulerLogicTests
// (AProposedBoardIsUntouchedUntilTheOperatorChangesIt,
// StartReviewOnABoardOfReviewNamesWhoIntroducesTheYouthNeverAParent,
// AProjectReviewHasNoIntroductionToRemindAbout).
//
// Runs on all CI platforms (see .github/workflows/build.yml).
// ------------------------------------------------------------------------

var path = require("path");
var seat = require(path.join(
   __dirname, "..", "src", "main", "resources", "shkc", "core", "WEBROOT", "process_seat.js"));

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

console.log("== a proposed board follows the event until changed (SPEC.md D-12) ==");

var proposal = { ids: ["chair", "m1", "m2"], chairId: "chair" };
var free = { chair: true, m1: true, m2: true, newcomer: true };
function canPick(id) {
   return free[id] === true;
}

check("as proposed, in any order", seat.proposalUntouched(proposal, ["m2", "chair", "m1"], "chair", canPick), true);
check("nothing proposed yet", seat.proposalUntouched(null, [], null, canPick), false);
check("the operator removed someone", seat.proposalUntouched(proposal, ["chair", "m1"], "chair", canPick), false);
check("the operator added someone", seat.proposalUntouched(proposal, ["chair", "m1", "m2", "newcomer"], "chair", canPick), false);
check("the operator changed the chair", seat.proposalUntouched(proposal, ["chair", "m1", "m2"], "m1", canPick), false);

// Someone proposed who has since gone onto another board, or home, left by
// themselves: still untouched, so it's proposed again without them.
free.m2 = false;
check("a member left for another board", seat.proposalUntouched(proposal, ["chair", "m1"], "chair", canPick), true);
free.chair = false;
check("the chair went home too", seat.proposalUntouched(proposal, ["m1"], null, canPick), true);

var empty = { ids: [], chairId: null };
check("a board proposed empty, with nobody here", seat.proposalUntouched(empty, [], null, canPick), true);

console.log("== whom Start review names to introduce the youth (SPEC.md D-23) ==");

var youth = { id: "S1", uname: "Troop1001", btype: "Final", leader: "Sam Smith" };
var leader = { id: "L", first: "Sam", last: "Smith", uname: "Troop1001", room: "", supporting: "" };
var parent = { id: "P", first: "Pat", last: "Aldridge", uname: "Troop1001", room: "", supporting: "" };
var introducer = { id: "SM", first: "Jo", last: "Jones", uname: "Troop1001", room: "104", supporting: "SCOUT:X|S1" };
var ids = function (adults) {
   return adults.map(function (a) { return a.id; });
};

var linked = seat.introductionFor(youth, [leader, parent, introducer]);
check("linked: only whoever introduces them", [ids(linked.introducers), ids(linked.leaders)], [["SM"], []]);

var unlinked = seat.introductionFor(youth, [leader, parent]);
check("no one linked: their leader, never the parent", [ids(unlinked.introducers), ids(unlinked.leaders)], [[], ["L"]]);

var parentOnly = seat.introductionFor(youth, [parent]);
check("only a parent here: no one named", [ids(parentOnly.introducers), ids(parentOnly.leaders)], [[], []]);

var elsewhere = { id: "L2", first: "Sam", last: "Smith", uname: "Troop9", room: "", supporting: "" };
check("a leader from another unit, found by first and last name",
   ids(seat.introductionFor(youth, [elsewhere]).leaders), ["L2"]);

var blank = { id: "B", first: "", last: "", uname: "Troop9", room: "", supporting: "" };
check("an adult with no name is nobody's leader", ids(seat.introductionFor(youth, [blank]).leaders), []);

check("a project review has no introduction",
   seat.introductionFor({ id: "S1", uname: "Troop1001", btype: "Project", leader: "Sam Smith" }, [introducer]), null);

console.log("");
console.log("== a proposed board only takes a room of its own type (SPEC.md D-24) ==");
var finalRoom = { id: "R1", BoardType: "Final" };
var projectRoom = { id: "R2", BoardType: "Project" };

check("a project review is proposed the project room, not the first free room",
   seat.proposeRoom([finalRoom, projectRoom], "Project"), "R2");
check("a board of review is proposed the final room",
   seat.proposeRoom([projectRoom, finalRoom], "Final"), "R1");
check("only a final room free: a project review is proposed no room",
   seat.proposeRoom([finalRoom], "Project"), null);
check("no room free: none proposed", seat.proposeRoom([], "Final"), null);

var asProposed = { id: "R1", BoardType: "Final", free: true };
check("a proposed room stays while it is free and the youth's type",
   seat.keepsRoom(asProposed, "Final", false), true);
check("a proposed room switched to the other type is withdrawn",
   seat.keepsRoom(asProposed, "Project", false), false);
check("a room the operator chose stays, even the other type",
   seat.keepsRoom(asProposed, "Project", true), true);
check("a room that is taken goes, whoever chose it",
   seat.keepsRoom({ id: "R1", BoardType: "Final", free: false }, "Final", true), false);
check("no room chosen: nothing to keep", seat.keepsRoom(null, "Final", false), false);

if (failures > 0) {
   console.log("BOARD BUILDER: FAIL -- " + failures + " of " + checks + " checks");
   process.exit(1);
}
console.log("BOARD BUILDER: PASS -- " + checks + " checks");
