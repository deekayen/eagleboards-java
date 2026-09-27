// ------------------------------------------------------------------------
// process_complete.js — InProgress -> Completed transition
// (/complete-board). Collects the board Result and free-text Notes before
// posting. (Project cost / BSA hours / other hours are no longer collected.)
// ------------------------------------------------------------------------

function ProcessCompleteBoard(s_id) {
   var s_last = youthStore.getColumnValue(s_id, "Last");
   var s_first = youthStore.getColumnValue(s_id, "First");
   var s_status = youthStore.getColumnValue(s_id, "Status");

   if (s_status == "Completed") {
      ebAlert("Complete error", s_first + " " + s_last + " has already completed their board.", "scout");
      return;
   } else if (s_status == "InProgress") {
      ebModalForm("Complete board: " + s_first + " " + s_last,
         "<label>Result<br/><select name='Result'>"
         + "<option value='Approved' selected>Approved</option>"
         + "<option value='Adjourned'>Adjourned</option>"
         + "<option value='NotApproved'>Not approved</option>"
         + "</select></label><br/><br/>"
         + "<label>Notes<br/><textarea name='Notes' rows='4' style='width: 300px;'></textarea></label>",
         [{ name: "Cancel", label: "Cancel" }, { name: "Complete", label: "Complete" }],
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
      ebAlert("Complete error", s_first + " " + s_last + " has not been seated yet.", "scout");
      return;
   }
}

function SendCompleteRequest(s_id, result, notes) {
   var s_last = youthStore.getColumnValue(s_id, "Last");
   var s_first = youthStore.getColumnValue(s_id, "First");

   ebAction("/complete-board", {
      ScoutID: s_id,
      Result: result,
      Notes: urlEntities(notes)
   })
      .then(function (res) {
         if (res.ok) {
            // Status shows Completed already (D-14); the message offers Undo
            // (O-2) and says who came with the youth, to hear the result.
            var label = (result === "NotApproved") ? "Not approved" : result;
            var found = SCHEDULER_locateText(s_id, true);
            ebMessage("Completed", ebEscapeHtml(s_first + " " + s_last) + ": " + label + "."
               + (found ? "<br/>" + found : ""), "scout", "Undo");
         } else {
            ebAlert("Complete error",
               s_first + " " + s_last + " complete failed.<br/> " + res.text, "scout");
         }
      })
      .catch(function () {
         ebAlert("Complete error", s_first + " " + s_last + " Complete Failed.", "scout");
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
