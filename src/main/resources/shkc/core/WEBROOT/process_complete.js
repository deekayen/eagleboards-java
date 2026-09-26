// ------------------------------------------------------------------------
// process_complete.js — InProgress -> Completed transition
// (/complete-board). Collects the board Result and free-text Notes before
// posting. (Project cost / BSA hours / other hours are no longer collected.)
// ------------------------------------------------------------------------

function ProcessCompleteBoard(s_id) {
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");
   var s_status = schedulerScoutGrid.getColumnValue(s_id, "Status");

   if (s_status == "Completed") {
      ebAlert("Complete Error", s_first + " " + s_last + " has already completed his board ", "scout");
      return;
   } else if (s_status == "InProgress") {
      ebModalForm("Complete Board: " + s_first + " " + s_last,
         "<label>Board Result:<br/><select name='Result'>"
         + "<option value='Approved' selected>Approved</option>"
         + "<option value='Adjourned'>Adjourned</option>"
         + "<option value='NotApproved'>NotApproved</option>"
         + "</select></label><br/><br/>"
         + "<label>Notes:<br/><textarea name='Notes' rows='4' style='width: 300px;'></textarea></label>",
         [{ name: "Complete", label: "Complete" }, { name: "Cancel", label: "Cancel" }],
         function (name, body) {
            if (name == "Complete") {
               var result = body.querySelector("select[name='Result']").value;
               var notes = body.querySelector("textarea[name='Notes']").value;

               // strip any markup from the notes (parity with the old editor)
               var sidx = notes.indexOf('<');
               var lidx = notes.lastIndexOf('>');
               if (sidx >= 0 && lidx > sidx) {
                  notes = notes.substring(0, sidx);
               }

               SendCompleteRequest(s_id, result, notes);
            }
         });
   } else {
      ebAlert("Complete Error", s_first + " " + s_last + " has not been seated yet.", "scout");
      return;
   }
}

function SendCompleteRequest(s_id, result, notes) {
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");

   ebAction("/complete-board", {
      ScoutID: s_id,
      Result: result,
      Notes: urlEntities(notes)
   })
      .then(function (res) {
         if (res.ok) {
            // Status shows Completed already (D-14); SCHEDULER_locateAdults
            // below shows who to bring in, which is the useful message here,
            // with Undo (O-2) added to that same message.
            SCHEDULER_locateAdults(s_id, true, "Undo");
         } else {
            ebAlert("Complete Error",
               s_first + " " + s_last + " complete failed.<br/> " + res.text, "scout");
         }
      })
      .catch(function () {
         ebAlert("Complete Error", s_first + " " + s_last + " Complete Failed.", "scout");
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
