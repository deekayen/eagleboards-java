// ------------------------------------------------------------------------
// process_seat.js — Registered -> InProgress transition (/seat-board).
//
// Validates the selection (scout status, checked adults, unit conflicts,
// availability, member counts per board type, selected room), then shows
// the chair-confirmation dialog and posts the seat request.
// ------------------------------------------------------------------------

// Scouting America, Guide to Advancement 8.0.0.3 and 8.0.3.0 #3: a board of
// review has "no fewer than three and no more than six members". Three is the
// district's working size, so four to six is allowed but worth confirming;
// seven is not permitted at any level and is refused outright.
var BOARD_MIN_MEMBERS = 3;
var BOARD_MAX_MEMBERS = 6;

// A project proposal review is not a board of review -- it is the GTA 9.0.2.4
// approval of the service project proposal -- so the three-member floor does
// not apply and this district runs them with two. The ceiling is shared with
// the board of review: six is as many people as belong in the room either way.
var PROJECT_MIN_MEMBERS = 2;

// Verdict for a proposed board size, kept separate from the dialogs so the
// national limits can be unit-tested headless:
//
//   "too-few"        below the national minimum — refused
//   "too-many"       above the national maximum — refused
//   "over-preferred" legal, but more than the district's working size of
//                    three, so the reviewer is asked to confirm
//   "ok"             seat it
function checkBoardSize(count) {
   if (count < BOARD_MIN_MEMBERS) {
      return "too-few";
   }
   if (count > BOARD_MAX_MEMBERS) {
      return "too-many";
   }
   if (count > BOARD_MIN_MEMBERS) {
      return "over-preferred";
   }
   return "ok";
}

// Same verdicts for a project proposal review: two is the working size, six
// the ceiling. Kept beside checkBoardSize so both are unit-tested headless.
function checkProjectSize(count) {
   if (count < PROJECT_MIN_MEMBERS) {
      return "too-few";
   }
   if (count > BOARD_MAX_MEMBERS) {
      return "too-many";
   }
   if (count > PROJECT_MIN_MEMBERS) {
      return "over-preferred";
   }
   return "ok";
}

// Board members must not come from the scout's own unit. This used to be a
// hard stop that named only the FIRST offender, so a board with several
// same-unit adults had to be fixed one alert at a time, and a district that
// genuinely had no one else available could not seat at all. It is now an
// overridable warning that lists every overlap at once — the council rule is
// enforced by making the conflict impossible to miss, not by making the
// software refuse.
//
// Kept pure (no grids, no dialogs, no DOM) so it can be unit-tested headless:
// see scripts/test-seat-conflicts.js.
//
//   scout_uname  the scout's UnitName, e.g. "Troop1234"
//   members      [{ id, last, first, uname }, ...] — the checked adults
//   returns      the subset of members sharing the scout's unit
function findUnitConflicts(scout_uname, members) {
   var conflicts = [];

   // A blank unit on either side is unknown, not a match. Comparing them
   // directly would make two blanks look like the same unit and block a
   // board over missing data.
   if (!scout_uname) {
      return conflicts;
   }

   for (var i = 0; i < members.length; i++) {
      if (members[i].uname && members[i].uname === scout_uname) {
         conflicts.push(members[i]);
      }
   }

   return conflicts;
}

// The council's rule (no adults from the scout's unit) is stricter than the
// national one, so a reviewer may bypass it — but only down to the national
// floor, GTA 8.0.3.0 #2: a unit-level board must still have at least one
// member who is not affiliated with the unit. A board made up entirely of the
// scout's own unit fails BOTH rules, so there is nothing left to fall back to
// and the bypass is not offered.
//
// A blank unit counts as "outside": it is unknown, not proof of affiliation,
// and refusing to seat over missing data would be worse than the risk.
function hasNonUnitMember(scout_uname, members) {
   if (!scout_uname) {
      return true;
   }

   for (var i = 0; i < members.length; i++) {
      if (!members[i].uname || members[i].uname !== scout_uname) {
         return true;
      }
   }

   return false;
}

// ------------------------------------------------------------------------
// Auto-select: the board proposed when a waiting scout is selected.
//
// It used to take the first qualified chair and then the first adults whose
// role for that board type was "Member", in sign-in order. A Final board's
// "Member" is often someone who chairs project reviews, so the first Final
// board of the night could take both project chairs as its members and leave
// every project review with nobody to chair it. It also ignored the troops of
// the scouts still waiting, so it could spend the one adult a later scout
// could use on a board anyone could have filled.
//
// Now every legal board for the scout is considered -- one qualified chair
// plus the working number of members, none from the scout's unit -- and the
// one chosen is, in order of priority:
//
//   1. the one that leaves the most of the OTHER waiting scouts, taken in
//      queue order, still able to get a full board right now from the adults
//      left over (their chairs, and their units, both count);
//   2. then the one that uses up the fewest chair qualifications, so
//      member-only adults fill member seats and a chair who can chair only
//      this kind of board is used before one who can chair both -- the
//      chairs are what cap the evening, and walk-ins have not arrived yet;
//   3. then the one whose adults could serve the fewest other waiting scouts,
//      keeping the flexible adults for later;
//   4. then sign-in order.
//
// When no full board exists it proposes what it can, in the same preference
// order, and says what is missing -- as before.
//
// Pure, so it is tested headless (scripts/test-seat-conflicts.js), and the
// same algorithm is in the Windows and Mac versions with the same tests.
//
//   scout    { id, uname, btype }
//   adults   [{ id, uname, final, project, room }] in sign-in order;
//            final/project are the roles "Chair", "Member" or "Unavailable"
//   waiting  the OTHER waiting scouts [{ id, uname, btype }] in queue order
//   returns  { chairId, memberIds, problems } -- memberIds excludes the chair
var BOARD_TYPES = ["Final", "Project"];

function adultRoleFor(adult, btype) {
   return btype === "Project" ? adult.project : adult.final;
}

function adultIsFree(adult) {
   return adult.room === "" || adult.room === "-";
}

// Same test as findUnitConflicts: a blank unit on either side is no match.
function sharesUnit(adult, scout) {
   return !!scout.uname && !!adult.uname && adult.uname === scout.uname;
}

function canSitFor(adult, scout) {
   var role = adultRoleFor(adult, scout.btype);
   return (role === "Chair" || role === "Member") && !sharesUnit(adult, scout);
}

function canChairFor(adult, scout) {
   return adultRoleFor(adult, scout.btype) === "Chair" && !sharesUnit(adult, scout);
}

function chairQualifications(adult) {
   var n = 0;
   for (var t = 0; t < BOARD_TYPES.length; t++) {
      if (adultRoleFor(adult, BOARD_TYPES[t]) === "Chair") {
         n++;
      }
   }
   return n;
}

// Members besides the chair at the district's working size.
function membersBesideChair(btype) {
   return (btype === "Project" ? PROJECT_MIN_MEMBERS : BOARD_MIN_MEMBERS) - 1;
}

// How many of the other waiting scouts, in queue order, can each still get a
// full board at once from `pool` (entries sorted by preference).
function countSeatable(pool, waiting) {
   var used = {};
   var seated = 0;
   for (var w = 0; w < waiting.length; w++) {
      var t = waiting[w];
      var chair = null;
      for (var i = 0; i < pool.length && chair === null; i++) {
         if (!used[pool[i].adult.id] && canChairFor(pool[i].adult, t)) {
            chair = pool[i].adult.id;
         }
      }
      if (chair === null) {
         continue;
      }
      var need = membersBesideChair(t.btype);
      var picked = [chair];
      for (var j = 0; j < pool.length && picked.length <= need; j++) {
         var a = pool[j].adult;
         if (!used[a.id] && a.id !== chair && canSitFor(a, t)) {
            picked.push(a.id);
         }
      }
      if (picked.length === need + 1) {
         for (var k = 0; k < picked.length; k++) {
            used[picked[k]] = true;
         }
         seated++;
      }
   }
   return seated;
}

function proposeBoard(scout, adults, waiting) {
   var result = { chairId: null, memberIds: [], problems: [] };
   var need = membersBesideChair(scout.btype);

   // Every free adult, with the keys that rank them: chair qualifications,
   // then how many other waiting scouts they could sit for, then sign-in order.
   var pool = [];
   for (var i = 0; i < adults.length; i++) {
      var a = adults[i];
      if (!adultIsFree(a)) {
         continue;
      }
      var useful = 0;
      for (var w = 0; w < waiting.length; w++) {
         if (canSitFor(a, waiting[w])) {
            useful++;
         }
      }
      pool.push({ adult: a, chairs: chairQualifications(a), useful: useful, order: i });
   }
   pool.sort(function (x, y) {
      return (x.chairs - y.chairs) || (x.useful - y.useful) || (x.order - y.order);
   });

   var chairs = pool.filter(function (p) { return canChairFor(p.adult, scout); });
   var sitters = pool.filter(function (p) { return canSitFor(p.adult, scout); });

   // [seatable, -chairsUsed, -usefulness]: larger is better.
   var best = null;
   var bestScore = null;
   var better = function (s, t) {
      for (var n = 0; n < s.length; n++) {
         if (s[n] !== t[n]) {
            return s[n] > t[n];
         }
      }
      return false;
   };

   var triedChairs = {};
   chairs.forEach(function (chair) {
      var chairProfile = chair.adult.uname + "|" + chair.adult.final + "|" + chair.adult.project;
      if (triedChairs[chairProfile]) {
         return;
      }
      triedChairs[chairProfile] = true;
      var others = sitters.filter(function (p) { return p.adult.id !== chair.adult.id; });
      // Each combination of `need` members, in preference order.
      var combo = [];
      var visit = function (start) {
         if (combo.length === need) {
            var board = [chair].concat(combo);
            var taken = {};
            var chairsUsed = 0;
            var usefulness = 0;
            board.forEach(function (p) {
               taken[p.adult.id] = true;
               chairsUsed += p.chairs;
               usefulness += p.useful;
            });
            var rest = pool.filter(function (p) { return !taken[p.adult.id]; });
            var score = [countSeatable(rest, waiting), -chairsUsed, -usefulness];
            if (bestScore === null || better(score, bestScore)) {
               bestScore = score;
               best = board;
            }
            return;
         }
         // Adults from the same unit with the same roles are interchangeable
         // here, so only the first of them is tried in each seat: the answer
         // is the same, and the search is far smaller.
         var tried = {};
         for (var n = start; n < others.length; n++) {
            var profile = others[n].adult.uname + "|" + others[n].adult.final + "|" + others[n].adult.project;
            if (tried[profile]) {
               continue;
            }
            tried[profile] = true;
            combo.push(others[n]);
            visit(n + 1);
            combo.pop();
         }
      };
      visit(0);
   });

   if (best !== null) {
      result.chairId = best[0].adult.id;
      result.memberIds = best.slice(1).map(function (p) { return p.adult.id; });
      return result;
   }

   // No full board: propose what there is, best first, and say what is short.
   if (chairs.length > 0) {
      result.chairId = chairs[0].adult.id;
   } else {
      result.problems.push("No " + scout.btype + " Chairs Available.");
   }
   for (var s = 0; s < sitters.length && result.memberIds.length < need; s++) {
      if (sitters[s].adult.id !== result.chairId) {
         result.memberIds.push(sitters[s].adult.id);
      }
   }
   if (result.memberIds.length < need) {
      result.problems.push("Only " + result.memberIds.length + " " + scout.btype + " Members Available");
   }
   return result;
}

// Node (unit tests) picks this up; browsers ignore it and use the global.
if (typeof module !== "undefined" && module.exports) {
   module.exports = {
      proposeBoard: proposeBoard,
      findUnitConflicts: findUnitConflicts,
      hasNonUnitMember: hasNonUnitMember,
      checkBoardSize: checkBoardSize,
      checkProjectSize: checkProjectSize,
      BOARD_MIN_MEMBERS: BOARD_MIN_MEMBERS,
      BOARD_MAX_MEMBERS: BOARD_MAX_MEMBERS,
      PROJECT_MIN_MEMBERS: PROJECT_MIN_MEMBERS
   };
}

function ProcessSeatBoard(s_id) {

   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");
   var s_uname = schedulerScoutGrid.getColumnValue(s_id, "UnitName");
   var s_btype = schedulerScoutGrid.getColumnValue(s_id, "BoardType");
   var s_room = schedulerScoutGrid.getColumnValue(s_id, "Room");
   var s_status = schedulerScoutGrid.getColumnValue(s_id, "Status");

   if (s_status == "Seated") {
      ebAlert("Schedule Error", "Scout " + s_first + " " + s_last + " board is already seated.");
      return;
   } else if (s_status == "InProgress") {
      ebAlert("Schedule Error", "Scout is currently in a board see room " + s_room);
      return;
   } else if (s_status == "Completed") {
      ebAlert("Schedule Error", "Scout has already completed his " + s_btype + " board");
      return;
   } else if (s_status == "Postponed") {
      ebAlert("Schedule Error", "Scout has already postponed his " + s_btype + " board");
      return;
   } else if (s_status != "Registered" && s_status != "Verified") {
      // "Verified" is accepted for legacy records only; Verify was removed and
      // nothing sets that status anymore.
      ebAlert("Schedule Error", "Unknown Status: " + s_status);
      return;
   }

   var selected_leader_str = schedulerLeaderGrid.getCheckedRowIds();

   if (!selected_leader_str) {
      ebAlert("Schedule Error", "No Leaders Selected");
      return;
   }

   var selected_leaders = selected_leader_str.split(",");
   var leader_names = "";
   var member_ids = "";
   var chair_id = "";
   var member_name_arr = [];
   var member_ids_arr = [];
   var member_arr = [];
   // Just the selected members qualified to chair THIS board type. The dialog
   // below used to offer every selected member, so once the qualified chairs
   // were all sitting on other boards the operator could hand the gavel to a
   // plain Member and the server took it. The Chair designation is binding:
   // promoting someone is a deliberate change on the Admin page, not a side
   // effect of their being the only name left in a dropdown.
   var chair_ids_arr = [];
   var chair_names_arr = [];

   for (var i = 0; i < selected_leaders.length; i++) {
      var l_id = selected_leaders[i];
      var l_last = schedulerLeaderGrid.getColumnValue(l_id, "Last");
      var l_first = schedulerLeaderGrid.getColumnValue(l_id, "First");
      var l_uname = schedulerLeaderGrid.getColumnValue(l_id, "UnitName");
      var l_final = schedulerLeaderGrid.getColumnValue(l_id, "FinalBoard");
      var l_project = schedulerLeaderGrid.getColumnValue(l_id, "ProjectReview");
      var l_room = schedulerLeaderGrid.getColumnValue(l_id, "Room");
      var l_fi = "";

      if (l_first && l_first.length > 0) {
         l_fi = "" + l_first.charAt(0);
      }

      if (l_room === "N/A") {
         // Room "N/A" is the Disable button's marker for someone who has gone
         // home. Reporting that as "assigned to a board in room N/A" sent the
         // operator looking for a room that does not exist.
         ebAlert("Schedule Error",
            "Member '" + l_last + ", " + l_first + "' has been disabled for tonight."
            + "<br/>Use Enable on the Adult Board Members panel if they are back.");
         return;
      }

      if (l_room.length > 0) {
         ebAlert("Schedule Error",
            "Member '" + l_last + ", " + l_first + "' is already assigned to a board in room " + l_room + ".");
         return;
      }

      // Same-unit overlap is collected, not rejected here: every offender is
      // reported together in one warning after this loop, and the reviewer
      // may override it. The blocks above (already seated, Unavailable) stay
      // hard failures — those are impossible, not merely inadvisable.
      member_arr.push({ id: l_id, last: l_last, first: l_first, uname: l_uname });

      var is_chair = ((s_btype == "Project") && (l_project == "Chair"))
         || ((s_btype == "Final") && (l_final == "Chair"));

      if ((chair_id == "") && is_chair) {
         chair_id = l_id;
      }

      if ((chair_id == "")
            && (((s_btype == "Project") && (l_project == "Unavailable"))
               || ((s_btype == "Final") && (l_final == "Unavailable")))) {
         ebAlert("Schedule Error",
            "Member '" + l_last + ", " + l_first + "' is currently Unavailable for " + s_btype + " Boards."
            + "'. Please select another leader.");
         return;
      }

      var short_name = l_fi + " " + l_last;
      if (leader_names.length > 0) {
         leader_names += ", ";
      }
      leader_names = leader_names + short_name;
      member_name_arr.push(short_name);
      if (is_chair) {
         chair_ids_arr.push(l_id);
         chair_names_arr.push(short_name);
      }
      if (member_ids.length > 0) {
         member_ids += ",";
      }
      member_ids = member_ids + l_id;
      member_ids_arr.push(l_id);
   }

   var proceedToRoomCheck = function () {
      // Check Room
      var rm_id = roomView.getSelected();
      if (!rm_id) {
         ebAlert("Schedule Error", "No room selected, please select a room and retry.");
         return;
      }

      var rm_data = roomView.get(rm_id);
      if (rm_data.BoardType !== s_btype) {
         ebConfirm("Schedule",
            "You have selected a " + rm_data.BoardType + " room for a " + s_btype + " Board.<br/><br/>Is this correct ?",
            function (result) {
               if (result) {
                  showChairDialog(rm_id);
               }
            });
         return;
      } else if (rm_data.Scout.length > 2) {
         ebAlert("Schedule Error",
            "Room " + rm_data.Room + " already occupied. Please select a different room.");
         return;
      }
      showChairDialog(rm_id);
   };

   var showChairDialog = function (rm_id) {
      // Qualified chairs only. requireQualifiedChair() has already refused the
      // seating if this list is empty, so the dropdown is never rendered blank.
      var opts = "";
      for (var o = 0; o < chair_names_arr.length; o++) {
         var sel = (chair_ids_arr[o] === chair_id) ? " selected" : "";
         opts += "<option value=\"" + chair_ids_arr[o] + "\"" + sel + ">" + chair_names_arr[o] + "</option>";
      }
      ebModalForm("Seat Board",
         "<label>Chair: <select name='Chair'>" + opts + "</select></label>",
         [{ name: "Okay", label: "Okay" }, { name: "Cancel", label: "Cancel" }],
         function (name, body) {
            if (name == "Okay") {
               var actual_chair_id = body.querySelector("select[name='Chair']").value;
               SendSeatRequest(rm_id, s_id, actual_chair_id, member_ids);
            }
         });
   };

   // A board must be chaired by someone designated Chair for that board type.
   // When the qualified chairs are all busy the answer is to promote someone
   // on the Admin page (Adults tab), not to seat a Member in the chair -- so
   // this is a hard stop, and the message says where to go to fix it.
   var requireQualifiedChair = function () {
      if (chair_ids_arr.length > 0) {
         return true;
      }
      var role_col = (s_btype == "Project") ? "Project" : "Final";
      ebAlert("Schedule Error",
         "<p style='text-align: left'>"
         + "<b>None of the selected board members is qualified to chair a "
         + s_btype + " board:</b><br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
         + leader_names
         + "<br/><br/>Select a member whose <b>" + role_col
         + "</b> role is <b>Chair</b>, or, if someone here should be chairing,"
         + " promote them on the Admin page (Adults tab) by setting their <b>"
         + role_col + "</b> role to <b>Chair</b> first.</p>");
      return false;
   };

   var proceedToCountChecks = function () {
      if (!requireQualifiedChair()) {
         return;
      }

      var size_verdict = checkBoardSize(selected_leaders.length);

      if (s_btype == "Final") {
         if (size_verdict === "too-few") {
            ebAlert("Schedule Error",
               "<p style='text-align: left; font-size: small'>Only " + selected_leaders.length
               + " board member(s) selected:<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" + leader_names + "<br/>"
               + "Three (3) required for Final Boards."
               + "<br/>Please select " + (BOARD_MIN_MEMBERS - selected_leaders.length) + " more leaders."
               + "</p>");
            return;
         } else if (size_verdict === "too-many") {
            // Not overridable: six is a national ceiling, not a local
            // preference, so there is no correct reason to seat seven.
            ebAlert("Schedule Error",
               "<p style='text-align: left'>You have selected " + selected_leaders.length
               + " board members:<br/><br/>&nbsp;&nbsp;&nbsp;" + leader_names
               + "<br/><br/>A board of review may have no more than six (6) members"
               + " (Guide to Advancement 8.0.0.3)."
               + "<br/>Please remove " + (selected_leaders.length - BOARD_MAX_MEMBERS)
               + " member(s).</p>");
            return;
         } else if (size_verdict === "over-preferred") {
            ebConfirm("Schedule",
               "You have selected " + selected_leaders.length
               + " board members:<br/><br/>&nbsp;&nbsp;&nbsp;" + leader_names
               + "<br/><br/>Only 3 are required.<br/><br/>Is this correct ?",
               function (res) {
                  if (res) {
                     proceedToRoomCheck();
                  }
               });
            return;
         }
      } else if (s_btype == "Project") {
         var project_verdict = checkProjectSize(selected_leaders.length);

         if (project_verdict === "too-few") {
            ebAlert("Schedule Error",
               "Only " + selected_leaders.length + " board members selected:<br/><br/>&nbsp;&nbsp;&nbsp;" + leader_names
               + "<br/><br/>Two (2) required for Project Reviews."
               + "<br/>Please select " + (PROJECT_MIN_MEMBERS - selected_leaders.length) + " more leaders.");
            return;
         } else if (project_verdict === "too-many") {
            // Same ceiling as a board of review, and refused the same way:
            // six is a limit, not a preference, so there is nothing to confirm.
            ebAlert("Schedule Error",
               "<p style='text-align: left'>You have selected " + selected_leaders.length
               + " board members:<br/><br/>&nbsp;&nbsp;&nbsp;" + leader_names
               + "<br/><br/>A project review may have no more than six (6) members."
               + "<br/>Please remove " + (selected_leaders.length - BOARD_MAX_MEMBERS)
               + " member(s).</p>");
            return;
         } else if (project_verdict === "over-preferred") {
            ebConfirm("Schedule",
               "You have selected " + selected_leaders.length
               + " board members:<br/><br/>&nbsp;&nbsp;&nbsp;" + leader_names
               + "<br/><br/>Only 2 are required.<br/><br/>Is this correct ?",
               function (res) {
                  if (res) {
                     proceedToRoomCheck();
                  }
               });
            return;
         }
      } else {
         ebAlert("Schedule Error", "No BoardType selected for scout " + s_last);
         return;
      }

      proceedToRoomCheck();
   };

   // Unit conflicts are raised before the head-count checks so the reviewer
   // resolves "who is on this board" before "how many", and a swap does not
   // re-ask the count question.
   var unit_conflicts = findUnitConflicts(s_uname, member_arr);

   if (unit_conflicts.length > 0) {
      var conflict_names = "";
      for (var c = 0; c < unit_conflicts.length; c++) {
         conflict_names += "<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
            + unit_conflicts[c].last + ", " + unit_conflicts[c].first;
      }

      // No bypass below the national floor: with nobody from outside the
      // unit there is no more permissive rule left to fall back on.
      if (!hasNonUnitMember(s_uname, member_arr)) {
         ebAlert("Schedule Error",
            "<p style='text-align: left'>"
            + "<b>Every selected board member is in " + s_uname
            + ", the same unit as scout " + s_first + " " + s_last + ":</b>"
            + conflict_names
            + "<br/><br/>A board of review held at the unit level must include"
            + " at least one district or council representative who is not"
            + " affiliated with the unit (Guide to Advancement 8.0.3.0)."
            + "<br/><br/>Please add a board member from outside "
            + s_uname + ".</p>");
         return;
      }

      ebConfirm("Unit Conflict Warning",
         "<p style='text-align: left'>"
         + "<b>" + unit_conflicts.length + " selected board member"
         + (unit_conflicts.length == 1 ? " is" : "s are")
         + " in " + s_uname + ", the same unit as scout "
         + s_first + " " + s_last + ":</b>"
         + conflict_names
         + "<br/><br/>This council does not permit adults from the scout's own"
         + " unit to sit on a board of review.<br/><br/>"
         + "Continuing falls back to the national requirement, which this"
         + " board still meets: at least one member is not affiliated with "
         + s_uname + ".<br/><br/>"
         + "Seat this board anyway ?</p>",
         function (res) {
            if (res) {
               proceedToCountChecks();
            }
         });
      return;
   }

   proceedToCountChecks();
}

function SendSeatRequest(room_id, s_id, chair_id, member_ids) {
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");

   ebAction("/seat-board", {
      RoomID: room_id,
      ScoutID: s_id,
      ChairID: chair_id,
      MemberIDs: member_ids
   })
      .then(function (res) {
         if (res.ok) {
            ebMessage("Seated Successful", "Success: " + s_first + " " + s_last + " Seated OK");
            // Mark these adults occupied and unchecked in the local grid right
            // away, rather than waiting on refresh_all() below. Without this, a
            // fast click to the next Registered scout re-runs auto-select
            // against the pre-seat local data (the server round trip hasn't
            // landed yet) and re-picks members who are now in this room; worse,
            // the server never clears Sel on seat, so left alone it stays "1"
            // forever and getCheckedRowIds() keeps reporting them as the
            // operator's already-chosen board for every scout seated after.
            var rm_data = roomView.get(room_id);
            schedulerLeaderGrid.markSeated(member_ids.split(","), rm_data ? rm_data.Room : "");
         } else {
            ebAlert("Seat Error", s_first + " " + s_last + " Seat Failed.<br/> " + res.text);
         }
      })
      .catch(function () {
         ebAlert("Seat Error", s_first + " " + s_last + " Seat Failed.");
      })
      .then(function () {
         setTimeout(function () {
            refresh_all();
         }, 500);
      });
}
