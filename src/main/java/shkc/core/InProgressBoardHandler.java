package shkc.core;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles {@code /inprogress-board}: "Start Review". The youth is brought in
 * to meet the board that {@link SeatBoardHandler} convened.
 *
 * <p>Request parameter: {@code ScoutID}. The youth must be {@code Seated}.
 * Answers {@code OK.} (status 200) once the youth is {@code InProgress}, or the
 * reason for the refusal (status 304). The change is undoable.
 *
 * <p>Writing the status stamps the record's last-update time, which restarts
 * the room card's timer, so the interview is timed from this step and not
 * from the seating.
 */
public class InProgressBoardHandler extends BoardHandler {
   public InProgressBoardHandler(EventData data) {
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
            this.sendError("ERROR: Invalid Scout ID" + scoutId, response);
            return;
         }
         if (!ScoutStatus.SEATED.equals(scout.getStatus())) {
            this.sendError("ERROR: Invalid Status '" + scout.getStatus() + "', expected '" + ScoutStatus.SEATED + "'", response);
            return;
         }

         String oldScoutStatus = scout.getStatus();
         scout.setStatus(ScoutStatus.IN_PROGRESS);
         UndoLog.Builder undo = this.data.getUndoLog().begin();
         undo.field("Scout", scout, "Status", oldScoutStatus);
         scout.updateFields(true);
         this.data.getScouts().store();
         undo.commit("Start review for " + scout.getFullName());
         this.sendSuccess(response);
      }
   }
}
