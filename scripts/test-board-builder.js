// ------------------------------------------------------------------------
// test-board-builder.js: the event page's board builder, after the
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

console.log("");
console.log("== an operator's board stays with its youth (SPEC.md D-12, amended 2026-11-08) ==");

// A board being built, as boardBuilder in scheduler_event.js holds it.
function building(scoutId, picked, chairId, proposed) {
   return {
      scoutId: scoutId, picked: picked, chairId: chairId, roomId: "R1", roomByOperator: false,
      problems: ["a warning"], editing: false, needsProposal: false,
      proposed: proposed === undefined ? { ids: ["chair", "m1", "m2"], chairId: "chair" } : proposed
   };
}
var changed = building("A", ["chair", "m1", "m3"], "chair");
var asProposedA = building("A", ["chair", "m1", "m2"], "chair");
var waitingChanged = { waiting: true, untouched: false };

var kept = {};
check("a board the operator changed is kept", seat.keepBoard(kept, changed, waitingChanged), true);
check("it is kept for its youth", Object.keys(kept), ["A"]);
changed.picked.push("m4");
changed.proposed.ids.push("m5");
check("the kept board is a copy: later changes to the builder do not reach it",
   [kept.A.picked, kept.A.proposed.ids], [["chair", "m1", "m3"], ["chair", "m1", "m2"]]);

var none = {};
check("an untouched proposal is not kept: it follows the event",
   seat.keepBoard(none, asProposedA, { waiting: true, untouched: true }), false);
check("nothing was proposed, so nothing was changed",
   seat.keepBoard(none, building("A", ["m1"], null, null), waitingChanged), false);
check("changing the members of a seated board is not kept",
   seat.keepBoard(none, Object.assign(building("A", ["chair", "m1", "m3"], "chair"), { editing: true }), waitingChanged), false);
check("a board still waiting for its proposal is not kept",
   seat.keepBoard(none, Object.assign(building("A", [], null), { needsProposal: true }), waitingChanged), false);
check("a youth no longer waiting has nothing to keep",
   seat.keepBoard(none, building("A", ["chair", "m1", "m3"], "chair"), { waiting: false, untouched: false }), false);
check("no youth, no board", seat.keepBoard(none, building(null, ["m1"], null), waitingChanged), false);
check("none of those were kept", none, {});

var back = seat.takeBoard(kept, "A");
check("coming back takes the board as the operator left it",
   [back.picked, back.chairId, back.roomId, back.problems], [["chair", "m1", "m3"], "chair", "R1", ["a warning"]]);
check("a board taken back is no longer kept", kept, {});
check("a youth with no kept board gets none", seat.takeBoard(kept, "A"), null);

// The sequence from the bug: change A's board, open B, change B's, go back.
var kept2 = {};
var current = building("A", ["chair", "m1", "m3"], "chair");
seat.keepBoard(kept2, current, waitingChanged);                          // leaving A, changed
check("opening B: B has no kept board, so B is proposed afresh", seat.takeBoard(kept2, "B"), null);
current = building("B", ["chair", "m1", "m2"], "chair");                   // B's proposal, untouched
check("leaving B untouched keeps nothing of B's",
   seat.keepBoard(kept2, current, { waiting: true, untouched: true }), false);
var backToA = seat.takeBoard(kept2, "A");
check("A comes back as the operator left it, with none of B's board in it",
   [backToA.picked, backToA.chairId], [["chair", "m1", "m3"], "chair"]);
current = building("A", backToA.picked, backToA.chairId);
current.picked.push("m5");                                               // A changed again
seat.keepBoard(kept2, current, waitingChanged);                          // leaving A for B
current = building("B", ["chair", "m2", "m4"], "m2");                      // B changed too
seat.keepBoard(kept2, current, waitingChanged);                          // leaving B for A
check("both changed boards are kept, each for its own youth", Object.keys(kept2).sort(), ["A", "B"]);
check("A's is A's", seat.takeBoard(kept2, "A").picked, ["chair", "m1", "m3", "m5"]);
check("B's is B's", seat.takeBoard(kept2, "B").picked, ["chair", "m2", "m4"]);

var stale = { A: seat.takeBoard({ A: changed }, "A"), B: building("B", ["chair", "m1"], "chair"), C: building("C", ["chair", "m1"], "chair") };
seat.pruneBoards(stale, function (id) { return id !== "B" && id !== "C"; });
check("a youth seated, finished, or deleted elsewhere keeps no board", Object.keys(stale), ["A"]);

var revived = seat.revivedBoard(
   { picked: ["chair", "m1", "m3"], chairId: "chair", roomId: "R1", roomByOperator: true, problems: [], proposed: null },
   function (id) { return id !== "chair" && id !== "m3"; });
check("an adult deleted meanwhile is no longer picked, and nor is a chair who went with them",
   [revived.picked, revived.chairId, revived.roomByOperator], [["m1"], null, true]);
check("a chair who is still here stays",
   seat.revivedBoard({ picked: ["chair", "m1"], chairId: "chair", roomId: null, roomByOperator: false, problems: [], proposed: null },
      function () { return true; }).chairId, "chair");

if (failures > 0) {
   console.log("BOARD BUILDER: FAIL (" + failures + " of " + checks + " checks)");
   process.exit(1);
}
console.log("BOARD BUILDER: PASS (" + checks + " checks)");
