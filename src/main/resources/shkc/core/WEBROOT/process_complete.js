// ------------------------------------------------------------------------
// process_complete.js — InProgress -> Completed transition
// (/complete-board). Collects Result, Notes, Project Cost, BSA Hours and
// Other Hours in a dialog before posting.
// ------------------------------------------------------------------------

function InitializeCompleteBoard() {
   // dialogs are created on demand now; kept for scheduler.html parity
}

function ProcessCompleteBoard(s_id) {
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");
   var s_status = schedulerScoutGrid.getColumnValue(s_id, "Status");

   if (s_status == "Completed") {
      ebAlert("Complete Error", s_first + " " + s_last + " has already completed his board ");
      return;
   } else if (s_status == "InProgress") {
      ebModalForm("Complete Board: " + s_first + " " + s_last,
         "<label>Board Result:<br/><select name='Result'>"
         + "<option value='Approved' selected>Approved</option>"
         + "<option value='Suspended'>Suspended</option>"
         + "<option value='NotApproved'>NotApproved</option>"
         + "</select></label><br/><br/>"
         + "<label>Notes:<br/><textarea name='Notes' rows='4' style='width: 300px;'>Completion Notes...</textarea></label><br/><br/>"
         + "<label>Project Cost: <input type='text' name='Cost' size='8' value=''/></label><br/>"
         + "<label>BSA Hours: <input type='text' name='BSAHours' size='8' value=''/></label><br/>"
         + "<label>Other Hours: <input type='text' name='OtherHours' size='8' value=''/></label>",
         [{ name: "Complete", label: "Complete" }, { name: "Cancel", label: "Cancel" }],
         function (name, body) {
            if (name == "Complete") {
               var result = body.querySelector("select[name='Result']").value;
               var notes = body.querySelector("textarea[name='Notes']").value;
               var project_cost = body.querySelector("input[name='Cost']").value;
               var bsa_hours = body.querySelector("input[name='BSAHours']").value;
               var other_hours = body.querySelector("input[name='OtherHours']").value;

               // strip any markup from the notes (parity with the old editor)
               var sidx = notes.indexOf('<');
               var lidx = notes.lastIndexOf('>');
               if (sidx >= 0 && lidx > sidx) {
                  notes = notes.substring(0, sidx);
               }

               SendCompleteRequest(s_id, result, notes, project_cost, bsa_hours, other_hours);
            }
         });
   } else {
      ebAlert("Complete Error", s_first + " " + s_last + " has not been seated yet.");
      return;
   }
}

function SendCompleteRequest(s_id, result, notes, project_cost, bsa_hours, other_hours) {
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");

   ebAction("/complete-board", {
      ScoutID: s_id,
      Result: result,
      Notes: urlEntities(notes),
      Cost: urlEntities(project_cost),
      BSAHours: urlEntities(bsa_hours),
      OtherHours: urlEntities(other_hours)
   })
      .then(function (res) {
         if (res.ok) {
            ebMessage("Completed", s_first + " " + s_last + " Completed OK");
            SCHEDULER_locateAdults(s_id, true);
         } else {
            ebAlert("Complete Error",
               s_first + " " + s_last + " complete failed.<br/> " + res.text);
         }
      })
      .catch(function () {
         ebAlert("Complete Error", s_first + " " + s_last + " Complete Failed.");
      })
      .then(function () {
         setTimeout(function () {
            refresh_all();
         }, 500);
      });
}

function urlEntities(str) {
   return String(str).replace(/&/g, 'and').replace(/=/, "eq");
}
