// ------------------------------------------------------------------------
// process_verify.js — Registered -> Verified transition (/verify-board).
// ------------------------------------------------------------------------

function InitializeVerifyBoard() {
   // dialogs are created on demand now; kept for scheduler.html parity
}

function ProcessVerifyBoard(s_id) {
   var s_status = schedulerScoutGrid.getColumnValue(s_id, "Status");
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");

   if (s_status == "Registered") {
      ebConfirm("Verify Board",
         "Verify <b>" + s_first + " " + s_last + "</b> ?",
         function (result) {
            if (result) {
               SendVerifyRequest(s_id);
            }
         });
   } else {
      ebAlert("Verify Error",
         "Invalid Status: '" + s_status + "'<br/>Expected: 'Registered'");
   }
}

function SendVerifyRequest(s_id) {
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");

   ebAction("/verify-board", { ScoutID: s_id })
      .then(function (res) {
         if (res.ok) {
            ebMessage("Verified",
               s_first + " " + s_last + " Verified OK, <br/><br/> COLLECT PROJECT COST<br/> &amp; BSA &amp; OTHER HOURS!! ");
         } else {
            ebAlert("Verify Error",
               s_first + " " + s_last + " Verify Failed.<br/> " + res.text);
         }
      })
      .catch(function () {
         ebAlert("Verify Error", s_first + " " + s_last + " Verify Failed.");
      })
      .then(function () {
         setTimeout(function () {
            refresh_all();
         }, 500);
      });
}
