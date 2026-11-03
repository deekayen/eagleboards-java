package shkc.core;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles {@code /postpone-board}: sends away a youth who arrived unprepared
 * and so never sees a board.
 *
 * <p>Request parameter: {@code ScoutID}. Only a {@code Registered} (or legacy
 * {@code Verified}) youth can be postponed; once a board is seated the board's
 * own decision is the result. Answers {@code OK.} (status 200) once the youth
 * is {@code Postponed}, or the reason for the refusal (status 304).
 *
 * <p>Postponing is confirm-only: it is not recorded in the Undo log.
 */
public class PostponeBoardHandler extends BoardHandler {
   public PostponeBoardHandler(EventData data) {
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
         if (!ScoutStatus.REGISTERED.equals(scout.getStatus()) && !ScoutStatus.VERIFIED.equals(scout.getStatus())) {
            this.sendError("ERROR: Invalid Status '" + scout.getStatus() + "', expected '"
               + ScoutStatus.REGISTERED + " | " + ScoutStatus.VERIFIED + "'", response);
            return;
         }

         scout.setStatus(ScoutStatus.POSTPONED);
         scout.updateFields(true);
         this.data.getScouts().store();
         this.sendSuccess(response);
      }
   }
}
