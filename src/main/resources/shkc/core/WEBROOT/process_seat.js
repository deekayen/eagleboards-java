// ------------------------------------------------------------------------
// process_seat.js — Registered -> InProgress transition (/seat-board).
//
// Validates the selection (scout status, checked adults, unit conflicts,
// availability, member counts per board type, selected room), then shows
// the chair-confirmation dialog and posts the seat request.
// ------------------------------------------------------------------------

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

      if (l_room.length > 0) {
         ebAlert("Schedule Error",
            "Member '" + l_last + ", " + l_first + "' is already assigned to a board in room " + l_room + ".");
         return;
      }

      if (l_uname == s_uname) {
         ebAlert("Schedule Error",
            "Member '" + l_last + ", " + l_first + "' is in "
            + s_uname + " with scout '" + s_last
            + "'. Please select another leader.");
         return;
      }

      if ((chair_id == "")
            && (((s_btype == "Project") && (l_project == "Chair"))
               || ((s_btype == "Final") && (l_final == "Chair")))) {
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
      var opts = "";
      for (var o = 0; o < member_name_arr.length; o++) {
         var sel = (member_ids_arr[o] === chair_id) ? " selected" : "";
         opts += "<option value=\"" + member_ids_arr[o] + "\"" + sel + ">" + member_name_arr[o] + "</option>";
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

   if (s_btype == "Final") {
      if (selected_leaders.length < 3) {
         ebAlert("Schedule Error",
            "<p style='text-align: left; font-size: small'>Only " + selected_leaders.length
            + " board member(s) selected:<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" + leader_names + "<br/>"
            + "Three (3) required for Final Boards."
            + "<br/>Please select " + (3 - selected_leaders.length) + " more leaders."
            + "</p>");
         return;
      } else if (selected_leaders.length > 3) {
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
      if (selected_leaders.length < 2) {
         ebAlert("Schedule Error",
            "Only " + selected_leaders.length + " board members selected:<br/><br/>&nbsp;&nbsp;&nbsp;" + leader_names
            + "<br/><br/>Two (2) required for Project Reviews."
            + "<br/>Please select " + (2 - selected_leaders.length) + " more leaders.");
         return;
      } else if (selected_leaders.length > 2) {
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
