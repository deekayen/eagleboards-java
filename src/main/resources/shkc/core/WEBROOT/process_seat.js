
var seatPopup;

//function InitializeSeatBoard()
//{
//
//  seatPopup = new dhtmlXPopup({
//      toolbar: schedulerToolbar,
//      id: "Seat" //attaches popup to the "Seat" button
//   });
//   alert("Seatpopup Initialized...");
//}

function ProcessSeatBoard(s_id)
{ // Seat

   //alert("NEW-ProcessSeatBoard");

   schedulerScoutGrid.getSeatPopup().hide();

//   var s_id = scoutGrid.getSelectedRowId();
//
//   if (!s_id)
//   {
//      dhtmlx.alert({
//         title: "Schedule Error",
//         type: "alert-error",
//         text: "No Scout Selected !!"
//      });
//      return;
//   }

   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");
   var s_uname = schedulerScoutGrid.getColumnValue(s_id, "UnitName");
   var s_btype = schedulerScoutGrid.getColumnValue(s_id, "BoardType");
   var s_room = schedulerScoutGrid.getColumnValue(s_id, "Room");
   var s_status = schedulerScoutGrid.getColumnValue(s_id, "Status");

   if (s_status == "Registered")
   {
      dhtmlx.alert({
         title: "Schedule Error",
         type: "alert-error",
         text: "Scout is not verified yes, please Verify " + s_first + " " + s_last
      });
      return;
   }
   else if (s_status == "Seated")
   {
      dhtmlx.alert({
         title: "Schedule Error",
         type: "alert-error",
         text: "Scout " + s_first + " " + s_last + " board is already seated."
      });
      return;
   }
   else if (s_status == "InProgress")
   {
      dhtmlx.alert({
         title: "Schedule Error",
         type: "alert-error",
         text: "Scout is currently in a board see room " + s_room
      });
      return;
   }
   else if (s_status == "Completed")
   {
      dhtmlx.alert({
         title: "Schedule Error",
         type: "alert-error",
         text: "Scout has already completed his " + s_btype + " board"
      });
      return;
   }
   else if (s_status == "Postponed")
   {
      dhtmlx.alert({
         title: "Schedule Error",
         type: "alert-error",
         text: "Scout has already postponed his " + s_btype + " board"
      });
      return;
   }
   else if (s_status != "Verified")
   {
      dhtmlx.alert({
         title: "Schedule Error",
         type: "alert-error",
         text: "Unknown Status: " + s_status
      });
      return;
   }

  // var selected_leader_str = schedulerLeaderGrid.getSelectedRowId(); //leaderGrid.getSelectedRowId();
   var selected_leader_str = schedulerLeaderGrid.getCheckedRowIds();
   
  // alert(selected_leader_str);
   if (!selected_leader_str)
   {
      dhtmlx.alert({
         title: "Schedule Error",
         type: "alert-error",
         text: "No Leaders Selected"
      });
      return;
   }

   var selected_leaders = selected_leader_str.split(",");
   var leader_names = "";
   var member_ids = "";
   var chair_id = "";
   var chair_name = "";
   var member_name_arr = [];
   var member_ids_arr = []

   for (i = 0; i < selected_leaders.length; i++)
   {
//ADULT: Last,First,UType,Unit,Final,Project, RM#

      var l_id = selected_leaders[i];
      var l_last = schedulerLeaderGrid.getColumnValue(l_id, "Last");
      var l_first = schedulerLeaderGrid.getColumnValue(l_id, "First");
      var l_uname = schedulerLeaderGrid.getColumnValue(l_id, "UnitName");
      var l_final = schedulerLeaderGrid.getColumnValue(l_id, "FinalBoard");
      var l_project = schedulerLeaderGrid.getColumnValue(l_id, "ProjectReview");
      var l_room = schedulerLeaderGrid.getColumnValue(l_id, "Room");
      var l_fi = "";

      //if (l_last == null)
      //{
      //   alert("Last name for: " + l_id + " == null");
      //}
      if (l_first && l_first.length > 0)
      {
         l_fi = "" + l_first.charAt(0);
      }

      if (l_room.length > 0)
      {
         dhtmlx.alert({
            title: "Schedule Error",
            type: "alert-error",
            text: "Member '" + l_last + ", " + l_first + "' is already assigned to a board in room " + l_room + "."
         });
         return;
      }


      if (l_uname == s_uname)
      {
         dhtmlx.alert({
            title: "Schedule Error",
            type: "alert-error",
            text: "Member '" + l_last + ", " + l_first + "' is in "
                    + s_uname + " with scout '" + s_last
                    + "'. Please select another leader."
         });
         return;
      }
      if ((chair_id == "") &&
              (((s_btype == "Project") && (l_project == "Chair"))
                      || ((s_btype == "Final") && (l_final == "Chair"))
                      ))
      {
         chair_id = l_id;
         chair_name = l_last + ", " + l_first;
      }

      if ((chair_id == "") &&
              (((s_btype == "Project") && (l_project == "Unavailable"))
                      || ((s_btype == "Final") && (l_final == "Unavailable"))
                      ))
      {
         dhtmlx.alert({
            title: "Schedule Error",
            type: "alert-error",
            text: "Member '" + l_last + ", " + l_first + "' is currently Unavailable for " + s_btype + " Boards."
                    + "'. Please select another leader."
         });
         return;
      }

      var short_name = l_fi + " " + l_last;
      if (leader_names.length > 0)
      {
         leader_names += ", ";
      }
      leader_names = leader_names + short_name;
      member_name_arr.push(short_name);
      if (member_ids.length > 0)
      {
         member_ids += ", ";
      }
      member_ids = member_ids + l_id;
      member_ids_arr.push(l_id);
   }
   if (s_btype == "Final")
   {
      if (selected_leaders.length < 3)
      {
         dhtmlx.alert({
            title: "Schedule Error",
            type: "alert-error",
            text: "<p style='text-align: left; font-size: small'>Only " + selected_leaders.length
                    + " board member(s) selected:<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" + leader_names + "<br/>"
                    + "Three (3) required for Final Boards."
                    + "<br/>Please select " + (3 - selected_leaders.length) + " more leaders."
                    + "</p>"
         });
         return;
      }
      else if (selected_leaders.length > 3)
      {
         var res = window.confirm("You have selected " + selected_leaders.length
                 + " board members \n\n     " + leader_names + ".\n\n Only 3 are required.\n\n Is this correct ? ");
         if (!res)
         {
            return;
         }
      }
   }
   else if (s_btype == "Project")
   {
      if (selected_leaders.length < 2)
      {
         dhtmlx.alert({
            title: "Schedule Error",
            type: "alert-error",
            text: "Only " + selected_leaders.length + " board members selected: \n\n     " + leader_names
                    + "\n\n Two (2) required for Project Reviews."
                    + "\n\nPlease select " + (2 - selected_leaders.length) + " more leaders."
         });
         return;
      }
      else if (selected_leaders.length > 2)
      {
         var res = window.confirm("You have selected " + selected_leaders.length
                 + " board members \n\n     " + leader_names + ".\n\n Only 2 are required.\n\n Is this correct ? ");
         if (!res)
         {
            return;
         }
      }
   }
   else
   {
      dhtmlx.alert({
         title: "Schedule Error",
         type: "alert-error",
         text: "No BoardType selected for scout " + s_last
      });
      return;
   }

// Check Room

   var rm_id = roomView.getSelected();
   if (!rm_id)
   {
      dhtmlx.alert({
         title: "Schedule Error",
         type: "alert-error",
         text: "No room selected, please select a room and retry."
      });
      return;
   }

   var rm_data = roomView.get(rm_id);
   if (rm_data.BoardType !== s_btype)
   {
      var res = window.confirm("You have selected a " + rm_data.BoardType
              + " room for a " + s_btype + " Board. \n\n Is this correct ? ");
      if (!res)
      {
         return;
      }
   }
   else if (rm_data.Scout.length > 2)
   {
      dhtmlx.alert({
         title: "Schedule Error",
         type: "alert-error",
         text: "Room " + rm_data.Room + " already occupied. Please select a different room."
      });
      return;
   }

   seatPopup = schedulerScoutGrid.getSeatPopup();
   seatPopup.clear();

   var seat_form = seatPopup.attachForm([
      {type: "select", inputWidth: 150, label: "Chair: ", name: "Chair", options: [
         ]},
      {type: "block", list: [
            {type: "button", name: "Okay", value: "Okay"},
            {type: "newcolumn"},
            {type: "button", offsetLeft: 50, name: "Cancel", value: "Cancel"}
         ]}]);

   var opts = seat_form.getOptions("Chair");
   for (var o = 0; o < member_name_arr.length; o++)
   {
      opts.add(new Option(member_name_arr[o], member_ids_arr[o]));
   }

   if (chair_id && chair_id.length > 0)
   {
      seat_form.setItemValue("Chair", chair_id);
   }

   seat_form.attachEvent("onButtonClick", function(id) {

      if (id == "Okay") //defines addition
      {
         var actual_chair_id = seat_form.getItemValue("Chair");
         SendSeatRequest(rm_id, s_id, actual_chair_id, member_ids);

         seatPopup.hide();
         seatPopup.clear();
      }
      else
      {
         seatPopup.hide();
         seatPopup.clear();
      }
   });

   console.log("PRE-seat show");
   seatPopup.show("Seat");
   console.log("POST-seat show... ");
   // ALL GOOD, make the call to schedule... 
}

function SendSeatRequest(room_id, s_id, chair_id, member_ids)
{
   var s_last = scoutGrid.cells(s_id, 1).getValue();
   var s_first = scoutGrid.cells(s_id, 2).getValue();

   xmlHttp = new XMLHttpRequest();
   xmlHttp.onreadystatechange = function() {
      if (xmlHttp.readyState === 4) {
         if (xmlHttp.status === 200) {
            if (xmlHttp.responseText.length > 4)
            {
               dhtmlx.alert({
                  title: "Seat Error",
                  type: "alert-error",
                  text: s_first + " " + s_last
                          + " complete failed.<br/> " + xmlHttp.responseText
               });
            }
            else
            {
               dhtmlx.message({
                  title: "Seated Successful",
                  type: "OK",
                  text: "Success: " + s_first + " " + s_last + " Seated OK"
               });
            }
         } else {
            dhtmlx.alert({
               title: "Seat Error",
               type: "alert-error",
               text: s_first + " " + s_last + " Seat Failed."
            });
         }
      }
      setTimeout(function() {
         refresh_all();
      }, 500);
   }

   xmlHttp.open("GET", "/seat-board?RoomID=" + room_id + "&ScoutID=" + s_id + "&ChairID=" + chair_id + "&MemberIDs=" + member_ids, false);
   xmlHttp.send(null);
}
