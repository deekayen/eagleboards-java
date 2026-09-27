// ------------------------------------------------------------------------
// process_change.js — change the members of a board already seated or in
// review (/change-board-members). Someone has to leave, or the chair changes
// hands. The status and the room timer are unchanged: it is the same board.
//
// The members and chair come from the details pane (boardBuilder in
// scheduler_event.js), which has already checked the rules as they were
// picked; this asks about the overridable ones, as Seat board does, and the
// server enforces the rest again.
// ------------------------------------------------------------------------

function ProcessChangeMembers(s_id) {
   var s_last = youthStore.getColumnValue(s_id, "Last");
   var s_first = youthStore.getColumnValue(s_id, "First");
   var s_uname = youthStore.getColumnValue(s_id, "UnitName");
   var s_btype = youthStore.getColumnValue(s_id, "BoardType");
   var member_ids = boardBuilder.memberIds();
   var members = member_ids.map(function (id) {
      return {
         id: id,
         uname: adultStore.getColumnValue(id, "UnitName"),
         last: adultStore.getColumnValue(id, "Last"),
         first: adultStore.getColumnValue(id, "First")
      };
   });

   var send = function () {
      SendChangeMembersRequest(s_id, boardBuilder.chairId, member_ids.join(","));
   };

   var checkSize = function () {
      var verdict = (s_btype === "Project") ? checkProjectSize(member_ids.length) : checkBoardSize(member_ids.length);
      if (verdict === "over-preferred") {
         var usual = (s_btype === "Project") ? PROJECT_MIN_MEMBERS : BOARD_MIN_MEMBERS;
         ebConfirm("Save " + member_ids.length + " members?",
            "The usual board is " + usual + ".",
            function (ok) {
               if (ok) {
                  send();
               }
            }, "Save anyway");
         return;
      }
      send();
   };

   // Same-unit members: overridable while someone from outside the unit
   // sits too (the national rule), as at Seat board.
   var conflicts = findUnitConflicts(s_uname, members);
   if (conflicts.length > 0 && hasNonUnitMember(s_uname, members)) {
      ebConfirm("Unit conflict",
         "<p>" + conflicts.map(function (c) { return ebEscapeHtml(c.first + " " + c.last); }).join(", ")
         + (conflicts.length === 1 ? " is" : " are") + " in " + ebEscapeHtml(s_uname)
         + ", the same unit as " + ebEscapeHtml(s_first + " " + s_last) + ".</p>"
         + "<p>This council does not permit adults from the youth's own unit on a board of review."
         + " Continuing falls back to the national requirement, which this board still meets.</p>",
         function (ok) {
            if (ok) {
               checkSize();
            }
         }, "Save anyway");
      return;
   }
   checkSize();
}

function SendChangeMembersRequest(s_id, chair_id, member_ids) {
   var name = youthStore.getColumnValue(s_id, "First") + " " + youthStore.getColumnValue(s_id, "Last");

   ebAction("/change-board-members", {
      ScoutID: s_id,
      ChairID: chair_id,
      MemberIDs: member_ids
   })
      .then(function (res) {
         if (res.ok) {
            // The pane and room card show the new board (D-14); the message
            // is there to offer Undo (O-2).
            ebMessage("Members changed", ebEscapeHtml(name) + "'s board.", "scout", "Undo");
            stopChangingMembers();
         } else {
            ebAlert("Change error", ebEscapeHtml(name) + "'s board was not changed.<br/>" + ebEscapeHtml(res.text), "scout");
         }
      })
      .catch(function () {
         ebAlert("Change error", ebEscapeHtml(name) + "'s board was not changed.", "scout");
      })
      .then(refresh_all);
}
