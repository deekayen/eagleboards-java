// ------------------------------------------------------------------------
// process_postpone.js — Registered/Verified -> Postponed transition
// (/postpone-board).
// ------------------------------------------------------------------------

function ProcessPostponeBoard(s_id) {
   var s_status = youthStore.getColumnValue(s_id, "Status");
   var s_last = youthStore.getColumnValue(s_id, "Last");
   var s_first = youthStore.getColumnValue(s_id, "First");

   if (s_status === "Registered" || s_status === "Verified") {
      ebConfirm("Postpone " + ebEscapeHtml(s_first + " " + s_last) + "?",
         "No board at this event, usually because the paperwork isn't in order. This can't be undone here.",
         function (result) {
            if (result) {
               SendPostponeRequest(s_id);
            }
         }, "Postpone");
   } else {
      ebAlert("Postpone error",
         "Postpone Error<br/>Invalid Status: '" + s_status + "'<br/>Expected: 'Registered' | 'Verified'", "scout");
   }
}

function SendPostponeRequest(s_id) {
   var s_last = youthStore.getColumnValue(s_id, "Last");
   var s_first = youthStore.getColumnValue(s_id, "First");

   ebAction("/postpone-board", { ScoutID: s_id })
      .then(function (res) {
         if (res.ok) {
            // Status shows Postponed already (D-14): no separate message.
         } else {
            ebAlert("Postpone error",
               s_first + " " + s_last + " Postpone failed.<br/> " + res.text, "scout");
         }
      })
      .catch(function () {
         ebAlert("Postpone error", s_first + " " + s_last + " Postpone Failed.", "scout");
      })
      .then(function () {
         setTimeout(function () {
            refresh_all();
         }, 500);
      });
}
