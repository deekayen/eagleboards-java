// ------------------------------------------------------------------------
// process_reset.js — Verified/Seated/InProgress -> Registered reset
// (/reset-board). Frees the room and board members.
// ------------------------------------------------------------------------

function ProcessResetBoard(s_id) {
   var s_status = schedulerScoutGrid.getColumnValue(s_id, "Status");
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");

   if (s_status === "Verified" || s_status === "Seated" || s_status === "InProgress") {
      ebConfirm("Reset Board",
         "Reset board for <b>" + s_first + " " + s_last + "</b> ?",
         function (result) {
            if (result) {
               SendResetRequest(s_id);
            }
         });
   } else {
      ebAlert("Reset Error",
         "Reset Error<br/>Invalid Status: '" + s_status + "'<br/>Expected: 'Verified' | 'Seated' | 'InProgress'");
   }
}

function SendResetRequest(s_id) {
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");

   ebAction("/reset-board", { ScoutID: s_id })
      .then(function (res) {
         if (res.ok) {
            ebMessage("Reset", s_first + " " + s_last + " Reset OK");
         } else {
            ebAlert("Reset Error",
               s_first + " " + s_last + " Reset failed.<br/> " + res.text);
         }
      })
      .catch(function () {
         ebAlert("Reset Error", s_first + " " + s_last + " Reset Failed.");
      })
      .then(function () {
         setTimeout(function () {
            refresh_all();
         }, 500);
      });
}
