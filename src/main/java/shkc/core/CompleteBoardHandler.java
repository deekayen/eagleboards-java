package shkc.core;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles {@code /complete-board}: records the board's decision and ends the
 * review.
 *
 * <p>Request parameters: {@code ScoutID} and {@code Result}, which must be
 * {@code Approved}, {@code Adjourned} or {@code NotApproved} (see
 * {@link ScoutResult}); optionally {@code Notes}, {@code Cost}, {@code BSAHours}
 * and {@code OtherHours}, which are joined into the youth's {@code Notes}
 * column. The youth must be {@code InProgress}.
 *
 * <p>The youth becomes {@code Completed} with room {@link RoomMarker#NO_ROOM},
 * the room card is emptied and the board's members are freed. Answers
 * {@code OK.} (status 200), or the reason for the refusal (status 304). The
 * change is undoable.
 */
public class CompleteBoardHandler extends BoardHandler {
   public CompleteBoardHandler(EventData data) {
      super(data);
   }

   @Override
   public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      synchronized (this.data.getLock()) {
         this.logRequest(request);
         String scoutId = request.getParameter("ScoutID");
         String result = request.getParameter("Result");
         String notes = request.getParameter("Notes");
         String cost = request.getParameter("Cost");
         String bsaHours = request.getParameter("BSAHours");
         String otherHours = request.getParameter("OtherHours");
         Log.verbose("ScoutID: " + scoutId);
         Log.verbose("Result: " + result);
         Log.verbose("Notes: " + notes);
         Log.verbose("ProjectCost: " + cost);
         Log.verbose("BSA Hours: " + bsaHours);
         Log.verbose("Other Hours: " + otherHours);

         // Exactly the three results the Complete dialog offers. This used
         // to accept any string of five or more characters, so a typo or a
         // hand-built request went into the district's record as a result.
         if (!isBoardDecision(result)) {
            this.sendError("Invalid Result '" + result + "', expected Approved, Adjourned or NotApproved", response);
            return;
         }

         ScoutRecord scout = this.data.getScouts().get(scoutId);
         if (scout == null) {
            this.sendError("Invalid Scout ID" + scoutId, response);
            return;
         }

         // "Seated" is deliberately no longer accepted. It used to be,
         // harmlessly, because seating went straight to InProgress and
         // no record was ever left sitting in Seated. Now that seating
         // only convenes the board, a Seated scout is one still waiting
         // outside the room, and completing there would record a result
         // for a review that never happened. The error message already
         // said "expected 'InProgress'"; the check now matches it.
         if (!ScoutStatus.IN_PROGRESS.equals(scout.getStatus())) {
            this.sendError("Invalid Scout Status '" + scout.getStatus() + "' expected '" + ScoutStatus.IN_PROGRESS + "'", response);
            return;
         }

         this.complete(scout, result, buildNotes(notes, cost, bsaHours, otherHours));
         this.sendSuccess(response);
      }
   }

   /** Whether {@code result} is one of the three decisions a board can record. */
   private static boolean isBoardDecision(String result) {
      return ScoutResult.APPROVED.equals(result)
         || ScoutResult.ADJOURNED.equals(result)
         || ScoutResult.NOT_APPROVED.equals(result);
   }

   /**
    * Joins the free-text notes with the project cost and hours, when given,
    * into the single {@code Notes} value the youth's record keeps.
    */
   private static String buildNotes(String notes, String cost, String bsaHours, String otherHours) {
      StringBuilder fullNotes = new StringBuilder(notes == null ? "" : notes);
      if (cost != null) {
         fullNotes.append("(Cost: ").append(cost).append(")");
      }
      if (bsaHours != null) {
         fullNotes.append("(BSA: ").append(bsaHours).append(" hrs)");
      }
      if (otherHours != null) {
         fullNotes.append("(Other: ").append(otherHours).append(" hrs)");
      }
      return fullNotes.toString();
   }

   /**
    * Ends the review: frees the board's members, empties the room card, marks
    * the youth completed with the result, saves the three files and commits
    * the change to the Undo log.
    */
   private void complete(ScoutRecord scout, String result, String notes) throws IOException {
      // By name, or (for a room renamed under the board) the card that
      // still names the scout. Without that it stayed "occupied" by a board
      // that had finished, and nothing could be seated there.
      RoomRecord room = this.data.findRoomOfBoard(scout);

      // The adults are released by the room name the SCOUT holds, not through
      // the room record (see EventData.releaseAdultsFromRoom): if the room was
      // renamed or deleted on the Admin page while the board sat in it, the
      // review still happened and must still be recorded.
      UndoLog.Builder undo = this.data.getUndoLog().begin();
      this.data.releaseAdultsFromRoom(scout.getRoom(), undo);

      String oldScoutStatus = scout.getStatus();
      String oldScoutRoom = scout.getRoom();
      String oldScoutNotes = scout.getValue("Notes");
      String oldScoutResult = scout.getValue("Result");
      scout.setStatus(ScoutStatus.COMPLETED);
      scout.setRoom(RoomMarker.NO_ROOM);
      scout.setNotes(notes);
      scout.setResult(result);
      undo.field("Scout", scout, "Status", oldScoutStatus);
      undo.field("Scout", scout, "Room", oldScoutRoom);
      undo.field("Scout", scout, "Notes", oldScoutNotes);
      undo.field("Scout", scout, "Result", oldScoutResult);
      if (room != null) {
         String oldRoomScout = room.getScout();
         String oldRoomLeaders = room.getLeaders();
         room.setScout("");
         room.setLeaders("");
         undo.field("Room", room, "Scout", oldRoomScout);
         undo.field("Room", room, "Leaders", oldRoomLeaders);
      }

      scout.updateFields(true);
      this.data.getScouts().store();
      this.data.getRooms().store();
      this.data.getAdults().store();
      undo.commit("Complete board for " + scout.getFullName());
   }
}
