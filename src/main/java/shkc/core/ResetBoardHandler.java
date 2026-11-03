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
 * the board's members are freed and the youth's room, chair and members are
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
         if (!ScoutStatus.IN_PROGRESS.equals(status) && !ScoutStatus.SEATED.equals(status) && !ScoutStatus.VERIFIED.equals(status)) {
            this.sendError("Invalid Scout Status '" + status + "' expected 'Verified', 'InProgress' or 'Seated' '", response);
            return;
         }

         // By name, or -- for a room renamed under the board -- the card
         // that still names the scout.
         RoomRecord room = this.data.findRoomOfBoard(scout);

         // Released by the scout's room name, as in CompleteBoardHandler:
         // a room renamed or deleted under a board must not strand its
         // members, who would otherwise stay committed to it for the rest of the
         // event. Reset offers no Undo, so no undo builder is passed.
         this.data.releaseAdultsFromRoom(scout.getRoom(), null);

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
         this.sendSuccess(response);
      }
   }
}
