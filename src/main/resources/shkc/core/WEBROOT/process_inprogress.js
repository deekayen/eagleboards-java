// ------------------------------------------------------------------------
// process_inprogress.js — Seated -> InProgress transition
// (/inprogress-board). "Start" button on the scout toolbar.
// ------------------------------------------------------------------------

function InitializeInProgressBoard() {
   // dialogs are created on demand now; kept for scheduler.html parity
}

function ProcessInProgressBoard(s_id) {
   var s_status = schedulerScoutGrid.getColumnValue(s_id, "Status");
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");

   if (s_status == "Seated") {
      ebConfirm("Start Board",
         "Start board for <b>" + s_first + " " + s_last + "</b> ?",
         function (result) {
            if (result) {
               SendInProgressRequest(s_id);
            }
         });
   } else {
      ebAlert("Start Error",
         "Invalid Status: '" + s_status + "'<br/>Expected: 'Seated'");
   }
}

function SendInProgressRequest(s_id) {
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");

   ebAction("/inprogress-board", { ScoutID: s_id })
      .then(function (res) {
         if (res.ok) {
            ebMessage("Started", s_first + " " + s_last + " Started OK");
            SCHEDULER_locateAdults(s_id, false);
         } else {
            ebAlert("Start Error",
               s_first + " " + s_last + " Start failed.<br/> " + res.text);
         }
      })
      .catch(function () {
         ebAlert("Start Error", s_first + " " + s_last + " Start Failed.");
      })
      .then(function () {
         setTimeout(function () {
            refresh_all();
         }, 500);
      });
}
