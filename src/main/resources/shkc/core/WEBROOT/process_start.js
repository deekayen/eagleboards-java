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

   ebConfirm("Start the review?",
      "Bring <b>" + ebEscapeHtml(s_first + " " + s_last) + "</b> in to room "
      + ebEscapeHtml(s_room) + "."
      + "<br/><br/>Do this once the board members have finished reading the"
      + " application, references and project workbook."
      + introductionText(s_id, s_first),
      function (result) {
         if (result) {
            SendStartReviewRequest(s_id);
         }
      }, "Start review");
}

// A board of review starts with an introduction (SPEC.md D-23): the adult
// linked to the youth -- usually their Scoutmaster, who may be sitting on
// another board right now -- introduces them. Name where they are, so
// someone can step in and fetch them. With no one linked, name the youth's
// leader if signed in, never a parent. A project review has no
// introduction, so nobody is named: "".
function introductionText(s_id, s_first) {
   var intro = introductionFor(
      {
         id: s_id,
         uname: youthStore.getColumnValue(s_id, "UnitName"),
         btype: youthStore.getColumnValue(s_id, "BoardType"),
         leader: youthStore.getColumnValue(s_id, "Leader")
      },
      adultStore.rows.map(function (a) {
         return { id: a.id, first: a.First, last: a.Last, name: fullName(a), uname: a.UnitName, room: a.Room, supporting: a.Supporting };
      }));
   if (!intro) {
      return "";
   }

   function lines(adults) {
      return adults.map(function (a) {
         return "<br/>&nbsp;&nbsp;<b>" + ebEscapeHtml(a.name) + "</b> — "
            + (a.room === "" || a.room === "-" ? "main room"
               : a.room === "N/A" ? "marked as gone home"
               : "on the board in room <b>" + ebEscapeHtml(a.room) + "</b>");
      }).join("");
   }

   if (intro.introducers.length > 0) {
      return "<br/><br/>First fetch whoever introduces them to the board:" + lines(intro.introducers);
   }
   if (intro.leaders.length > 0) {
      return "<br/><br/>No one has said they'll introduce them. Their leader:" + lines(intro.leaders);
   }
   return "<br/><br/>No one has said they'll introduce them, and their leader hasn't signed in."
      + " Ask " + ebEscapeHtml(s_first) + " who will.";
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
