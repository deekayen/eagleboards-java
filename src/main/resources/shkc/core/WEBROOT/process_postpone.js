// ------------------------------------------------------------------------
// process_postpone.js — Registered/Verified -> Postponed transition
// (/postpone-board).
// ------------------------------------------------------------------------

function ProcessPostponeBoard(s_id) {
   var s_status = schedulerScoutGrid.getColumnValue(s_id, "Status");
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");

   if (s_status === "Registered" || s_status === "Verified") {
      ebConfirm("Postpone Board",
         "Postpone board for <b>" + s_first + " " + s_last + "</b> ?",
         function (result) {
            if (result) {
               SendPostponeRequest(s_id);
            }
         });
   } else {
      ebAlert("Postpone Error",
         "Postpone Error<br/>Invalid Status: '" + s_status + "'<br/>Expected: 'Registered' | 'Verified'");
   }
}

function SendPostponeRequest(s_id) {
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");

   ebAction("/postpone-board", { ScoutID: s_id })
      .then(function (res) {
         if (res.ok) {
            ebMessage("Postpone", s_first + " " + s_last + " Postpone OK");
         } else {
            ebAlert("Postpone Error",
               s_first + " " + s_last + " Postpone failed.<br/> " + res.text);
         }
      })
      .catch(function () {
         ebAlert("Postpone Error", s_first + " " + s_last + " Postpone Failed.");
      })
      .then(function () {
         setTimeout(function () {
            refresh_all();
         }, 500);
      });
}
