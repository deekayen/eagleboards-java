// ------------------------------------------------------------------------
// process_reset.js — Verified/Seated/InProgress -> Registered reset
// (/reset-board). Frees the room and board members.
// ------------------------------------------------------------------------

function ProcessResetBoard(s_id) {
   var s_status = youthStore.getColumnValue(s_id, "Status");
   var s_last = youthStore.getColumnValue(s_id, "Last");
   var s_first = youthStore.getColumnValue(s_id, "First");

   if (s_status === "Verified" || s_status === "Seated" || s_status === "InProgress") {
      ebConfirm("Reset " + ebEscapeHtml(s_first + " " + s_last) + "'s board?",
         "They go back to waiting, and the room and its members are freed. This can't be undone here.",
         function (result) {
            if (result) {
               SendResetRequest(s_id);
            }
         }, "Reset");
   } else {
      ebAlert("Reset error",
         "Reset Error<br/>Invalid Status: '" + s_status + "'<br/>Expected: 'Verified' | 'Seated' | 'InProgress'", "scout");
   }
}

function SendResetRequest(s_id) {
   var s_last = youthStore.getColumnValue(s_id, "Last");
   var s_first = youthStore.getColumnValue(s_id, "First");

   ebAction("/reset-board", { ScoutID: s_id })
      .then(function (res) {
         if (res.ok) {
            // Status shows Registered already (D-14): no separate message.
         } else {
            ebAlert("Reset error",
               s_first + " " + s_last + " Reset failed.<br/> " + res.text, "scout");
         }
      })
      .catch(function () {
         ebAlert("Reset error", s_first + " " + s_last + " Reset Failed.", "scout");
      })
      .then(function () {
         setTimeout(function () {
            refresh_all();
         }, 500);
      });
}
