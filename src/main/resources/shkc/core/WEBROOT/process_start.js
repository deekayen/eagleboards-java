// ------------------------------------------------------------------------
// process_start.js — Seated -> InProgress transition (/inprogress-board).
//
// "Seat board" gives the members the room to read the application, references
// and project workbook. This is the second step: the scout is brought in and
// the interview begins. Seating used to do both at once, which left no way to
// tell a board still reading the paperwork from one already talking to the
// candidate -- and so no way to time them apart.
// ------------------------------------------------------------------------

function ProcessStartReview(s_id) {
   var s_status = youthStore.getColumnValue(s_id, "Status");
   var s_last = youthStore.getColumnValue(s_id, "Last");
   var s_first = youthStore.getColumnValue(s_id, "First");
   var s_room = youthStore.getColumnValue(s_id, "Room");

   if (s_status === "InProgress") {
      ebAlert("Start error",
         "The review for " + s_first + " " + s_last + " has already started"
         + (s_room ? " in room " + s_room : "") + ".", "scout");
      return;
   }

   if (s_status !== "Seated") {
      // The server enforces this too; catching it here keeps the reviewer
      // from having to read a raw endpoint error.
      ebAlert("Start error",
         "The board for " + s_first + " " + s_last + " has not been seated yet."
         + "<br/>Use <b>Seat board</b> first, then start the review."
         + "<br/><br/>Current status: '" + s_status + "'", "scout");
      return;
   }

   // Whoever came to support this scout -- often their Scoutmaster, who may
   // be sitting on another board right now -- introduces them. Name the room
   // so someone can step in and fetch them for a moment.
   var supporting = SCHEDULER_supportingAdults(s_id);
   var fetchText = "";
   if (supporting.length > 0) {
      fetchText = "<br/><br/>Bring out to introduce them:";
      supporting.forEach(function (a) {
         fetchText += "<br/>&nbsp;&nbsp;<b>" + ebEscapeHtml(a.name) + "</b> — "
            + (a.room === "Main" ? "main room"
               : a.room === "N/A" ? "marked as gone home"
               : "on the board in room <b>" + ebEscapeHtml(a.room) + "</b>");
      });
   }

   ebConfirm("Start the review?",
      "Bring <b>" + ebEscapeHtml(s_first + " " + s_last) + "</b> in to room "
      + ebEscapeHtml(s_room) + "."
      + "<br/><br/>Do this once the board members have finished reading the"
      + " application, references and project workbook."
      + fetchText,
      function (result) {
         if (result) {
            SendStartReviewRequest(s_id);
         }
      }, "Start review");
}

function SendStartReviewRequest(s_id) {
   var s_last = youthStore.getColumnValue(s_id, "Last");
   var s_first = youthStore.getColumnValue(s_id, "First");

   ebAction("/inprogress-board", { ScoutID: s_id })
      .then(function (res) {
         if (res.ok) {
            // Status shows InProgress already (D-14); the message here is
            // only to offer Undo (O-2).
            ebMessage("Review started", s_first + " " + s_last + ".", "scout", "Undo");
         } else {
            ebAlert("Start error",
               s_first + " " + s_last + " could not be started.<br/> " + res.text, "scout");
         }
      })
      .catch(function () {
         ebAlert("Start error", s_first + " " + s_last + " could not be started.", "scout");
      })
      .then(function () {
         setTimeout(function () {
            refresh_all();
         }, 500);
      });
}
