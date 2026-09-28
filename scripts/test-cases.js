// ------------------------------------------------------------------------
// test-cases.js — the shared rule and auto-select cases (SPEC.md D-5).
//
// The board rules, auto-select and fill the rest are written down once, as
// data, in eagleboards-shared/cases, and every version runs every case
// through its own code. scripts/cases/ is a byte-for-byte copy of that
// folder, pinned by test-cases.lock and checked in CI. A new case goes there,
// never only here: see cases/README.md for the format.
//
// This runs each case through process_seat.js, maps the answer into the
// cases' shapes and compares. It never loosens a comparison to make a case
// pass: a case that fails here means Java, or the case, is wrong, and a real
// difference is settled in eagleboards-shared.
//
//   node scripts/test-cases.js [cases-folder]
//
// The folder defaults to scripts/cases. Runs on all CI platforms with no
// packages (see .github/workflows/build.yml).
// ------------------------------------------------------------------------

var fs = require("fs");
var path = require("path");
var seat = require(path.join(
   __dirname, "..", "src", "main", "resources", "shkc", "core", "WEBROOT", "process_seat.js"));

var FORMAT = 1;
var dir = process.argv[2] || path.join(__dirname, "cases");

var failures = 0;
var checks = 0;

function fail(what, why) {
   console.log("FAIL: " + what + "\n        " + why);
   failures++;
}

// ---- the cases' vocabulary into process_seat.js's ----

function youthOf(y) {
   return { id: y.id || "", uname: y.unit || "", btype: y.boardType || "" };
}

function adultOf(a) {
   return {
      id: a.id, uname: a.unit || "", final: a.final || "", project: a.project || "",
      room: a.room || "", freeSince: a.freeSince || "", supporting: a.supporting || "",
      woodBadge: a.woodBadge || ""
   };
}

// The shortage messages are worded differently in each version, so the cases
// compare them as structures. A message this does not know is a failure, not
// something to skip past.
function problemOf(text) {
   var m = /^No (Final|Project) Chairs Available\.$/.exec(text);
   if (m) {
      return { kind: "no-chair" };
   }
   m = /^Only (\d+) (Final|Project) Members Available$/.exec(text);
   if (m) {
      return { kind: "too-few-members", available: Number(m[1]) };
   }
   throw new Error("unknown problem message: " + JSON.stringify(text));
}

function proposalOf(p) {
   return { chair: p.chairId, members: p.memberIds, problems: p.problems.map(problemOf) };
}

var PROBLEM_KINDS = { "no-chair": true, "too-few-members": true };

function checkProblemKinds(expect) {
   (expect.problems || []).forEach(function (p) {
      if (!PROBLEM_KINDS[p.kind]) {
         throw new Error("unknown problem kind in the case: " + JSON.stringify(p.kind));
      }
   });
}

// Only the keys the case names are compared; any other key is a mistake in
// the case, so it fails rather than being ignored.
function compareKeys(got, want, allowed) {
   var wrong = [];
   Object.keys(want).forEach(function (key) {
      if (allowed && allowed.indexOf(key) < 0) {
         throw new Error("unknown key in expect: " + JSON.stringify(key));
      }
      var g = JSON.stringify(got[key] === undefined ? null : got[key]);
      var w = JSON.stringify(want[key]);
      if (g !== w) {
         wrong.push(key + "\n          got:  " + g + "\n          want: " + w);
      }
   });
   return wrong;
}

function compareWhole(got, want) {
   var g = JSON.stringify(got);
   var w = JSON.stringify(want);
   return g === w ? [] : ["\n          got:  " + g + "\n          want: " + w];
}

var PROPOSAL_KEYS = ["chair", "members", "problems"];

var ops = {
   "unit-conflicts": function (c) {
      var got = seat.findUnitConflicts(c.youth.unit || "", c.adults.map(adultOf))
         .map(function (a) { return a.id; });
      return compareWhole(got, c.expect);
   },

   "outside-member": function (c) {
      return compareWhole(seat.hasNonUnitMember(c.youth.unit || "", c.adults.map(adultOf)), c.expect);
   },

   "board-size": function (c) {
      var verdict;
      if (c.boardType === "Final") {
         verdict = seat.checkBoardSize(c.count);
      } else if (c.boardType === "Project") {
         verdict = seat.checkProjectSize(c.count);
      } else {
         throw new Error("unknown boardType: " + JSON.stringify(c.boardType));
      }
      return compareWhole(verdict, c.expect);
   },

   "suggest": function (c) {
      checkProblemKinds(c.expect);
      var p = seat.proposeBoard(youthOf(c.youth), c.adults.map(adultOf), c.waiting.map(youthOf));
      return compareKeys(proposalOf(p), c.expect, PROPOSAL_KEYS);
   },

   "fill": function (c) {
      checkProblemKinds(c.expect);
      var p = seat.fillBoard(youthOf(c.youth), c.adults.map(adultOf), c.picked, c.waiting.map(youthOf));
      return compareKeys(proposalOf(p), c.expect, PROPOSAL_KEYS);
   },

   "free-since": function (c) {
      var since = seat.freeSinceTimes(
         c.adults.map(function (a) { return { id: a.id, regTime: a.regTime || "" }; }),
         c.boards.map(function (b) {
            return { status: b.status || "", memberIds: b.members || "", lastUpdate: b.lastUpdate || "" };
         }));
      return compareKeys(since, c.expect, null);
   },

   // The procedure cases/README.md sets out: down the queue in order, each
   // youth's proposal weighing every other youth not yet seated; a proposal
   // with no problems seats a board and puts its adults in a room.
   "seat-down-the-queue": function (c) {
      var adults = c.adults.map(adultOf);
      var queue = c.queue.map(youthOf);
      var seated = {};
      var boards = { Final: 0, Project: 0 };
      queue.forEach(function (youth, s) {
         var waiting = queue.filter(function (t) { return t !== youth && !seated[t.id]; });
         var p = seat.proposeBoard(youth, adults, waiting);
         if (p.problems.length === 0) {
            seated[youth.id] = true;
            boards[youth.btype]++;
            var ids = [p.chairId].concat(p.memberIds);
            adults.forEach(function (a) {
               if (ids.indexOf(a.id) >= 0) {
                  a.room = "R" + s;
               }
            });
         }
      });
      return compareWhole(boards, c.expect);
   },

   "support-link": function (c) {
      return compareWhole(seat.withSupportLink(c.supporting, c.youth, c.linked), c.expect);
   }
};

var files;
try {
   files = fs.readdirSync(dir).filter(function (f) { return /\.json$/.test(f); }).sort();
} catch (e) {
   console.log("SHARED CASES: FAIL — cannot read " + dir + ": " + e.message);
   process.exit(1);
}
if (files.length === 0) {
   console.log("SHARED CASES: FAIL — no case files in " + dir);
   process.exit(1);
}

files.forEach(function (file) {
   var suite;
   try {
      suite = JSON.parse(fs.readFileSync(path.join(dir, file), "utf8"));
   } catch (e) {
      checks++;
      fail(file, "not readable JSON: " + e.message);
      return;
   }
   if (typeof suite.format !== "number" || suite.format > FORMAT) {
      checks++;
      fail(file, "format " + JSON.stringify(suite.format) + " is not one this runner reads (" + FORMAT + ")");
      return;
   }
   var run = ops[suite.op];
   if (!run || file !== suite.op + ".json") {
      checks++;
      fail(file, "unknown op " + JSON.stringify(suite.op) + ", or a file not named after its op");
      return;
   }
   console.log("== " + suite.op + " ==");
   suite.cases.forEach(function (c) {
      checks++;
      var what = suite.op + ": " + c.name;
      var wrong;
      try {
         wrong = run(c);
      } catch (e) {
         fail(what, e.message);
         return;
      }
      if (wrong.length === 0) {
         console.log("  ok: " + c.name);
      } else {
         fail(what, wrong.join("\n        "));
      }
   });
});

console.log("");
if (failures > 0) {
   console.log("SHARED CASES: FAIL — " + failures + " of " + checks + " cases failed");
   process.exit(1);
}
console.log("SHARED CASES: PASS — " + checks + " cases");
