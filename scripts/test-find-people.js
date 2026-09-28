// ------------------------------------------------------------------------
// test-find-people.js — finding a person's room (SPEC.md D-21).
//
// findPeople, roomsFound and personFindNote are pure (no DOM), so this runs
// headless with no browser and no test framework:
//
//   node scripts/test-find-people.js
//
// The same cases are the Windows version's SchedulerLogicTests.
// FindingAPersonSaysWhichRoomTheyAreInOrWhereTheyAreInstead and the Mac's
// PersonFindTests. The board rules, auto-select and fill the rest are not
// here: they are the shared cases, run by scripts/test-cases.js.
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

console.log("== finding a person's room (SPEC.md D-21) ==");

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
   console.log("FIND A PERSON TESTS: FAIL — " + failures + " of " + checks + " checks failed");
   process.exit(1);
}
console.log("FIND A PERSON TESTS: PASS — " + checks + " checks");
