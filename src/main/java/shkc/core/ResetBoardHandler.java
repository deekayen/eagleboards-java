package shkc.core;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles {@code /reset-board}: tears down a board that is seated or in
 * review and puts the youth back in the queue as {@code Registered}.
 *
 * <p>Request parameter: {@code ScoutID}. The youth must be {@code Seated},
 * {@code InProgress} or (legacy) {@code Verified}. The room card is emptied,
 * the board's members are freed, and the youth's room, chair, and members are
 * cleared. Answers {@code OK.} (status 200), or the reason for the refusal
 * (status 304).
 *
 * <p>Reset is confirm-only: it is not recorded in the Undo log.
 */
public class ResetBoardHandler extends BoardHandler {
   public ResetBoardHandler(EventData data) {
      super(data);
   }

   @Override
   public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      synchronized (this.data.getLock()) {
         this.logRequest(request);
         String scoutId = request.getParameter("ScoutID");
         Log.verbose("ScoutID: " + scoutId);

         ScoutRecord scout = this.data.getScouts().get(scoutId);
         if (scout == null) {
            this.sendError("Invalid Scout ID" + scoutId, response);
            return;
         }
         String status = scout.getStatus();
         String boardRoom = scout.getRoom();
         Log.info("reset-board: scout=" + EventData.scoutLabel(scout) + " status=" + status + " room=" + Log.safeValue("Room", boardRoom)
            + " chair=" + Log.safeValue("BoardChairID", scout.getValue("BoardChairID"))
            + " members=[" + Log.safeValue("BoardMembersIDs", scout.getValue("BoardMembersIDs")) + "]");
         if (!ScoutStatus.IN_PROGRESS.equals(status) && !ScoutStatus.SEATED.equals(status) && !ScoutStatus.VERIFIED.equals(status)) {
            this.sendError("Invalid Scout Status '" + status + "' expected 'Verified', 'InProgress' or 'Seated' '", response);
            return;
         }

         // By name, or (for a room renamed under the board) the card
         // that still names the scout.
         RoomRecord room = this.data.findRoomOfBoard(scout);
         if (room == null) {
            Log.info("reset-board: scout=" + EventData.scoutLabel(scout) + " no room card matches room=" + Log.safeValue("Room", boardRoom)
               + " (deleted or renamed on the Admin page); freeing the members by room name anyway");
         } else if (!room.getRoom().equals(boardRoom)) {
            Log.info("reset-board: scout=" + EventData.scoutLabel(scout) + " holds room=" + Log.safeValue("Room", boardRoom)
               + " but the card found by the scout's name is room=" + Log.safeValue("Room", room.getRoom()) + " (renamed under the board)");
         }

         // Released by the scout's room name, as in CompleteBoardHandler:
         // a room renamed or deleted under a board must not strand its
         // members, who would otherwise stay committed to it for the rest of the
         // event. Reset offers no Undo, so no undo builder is passed.
         int adultsReleased = this.data.releaseAdultsFromRoom(boardRoom, null);

         if (room != null) {
            room.setScout("");
            room.setLeaders("");
         }

         scout.setStatus(ScoutStatus.REGISTERED);
         scout.setRoom("");
         scout.setBoardChair("");
         scout.setBoardChairID("");
         scout.setBoardMemberIDs("");
         scout.setBoardMembers("");
         scout.updateFields(true);
         this.data.getScouts().store();
         this.data.getRooms().store();
         this.data.getAdults().store();
         Log.info("reset-board: scout=" + EventData.scoutLabel(scout) + " status " + status + " -> " + ScoutStatus.REGISTERED
            + ", room " + (room == null ? Log.safeValue("Room", boardRoom) + " (no card)" : Log.safeValue("Room", room.getRoom()) + ": occupied -> free")
            + ", adults_released=" + adultsReleased + ", chair and members cleared, no Undo recorded"
            + (this.data.getUndoLog().isEmpty() ? "" : " (Undo still holds '" + this.data.getUndoLog().description() + "', which may now be stale)")
            + " -> reset");
         this.sendSuccess(response);
      }
   }
}
