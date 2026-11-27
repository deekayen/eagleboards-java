package shkc.core;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Base class for the handlers that carry out a board step (seat, start,
 * complete, reset, undo, and so on).
 *
 * <p>These handlers answer in plain text: {@code OK.} when the step was done, or
 * the reason it was refused. The browser adapter ({@code ebAction} in eb-data.js)
 * counts only a 200 answer that starts with "OK" as success and shows any other
 * text to the operator as the reason. The original program also called
 * {@code setStatus(304)} for a refusal, but only after the body was written, when
 * the status can no longer change, so a refusal reaches the browser (and the
 * access line in the event log) as 200. Search the log for {@code REFUSED}, not
 * for a status.
 */
public abstract class BoardHandler implements WebServer.WebHandler {
   /** The shared event state: the record files, the lock and the Undo log. */
   protected final EventData data;

   protected BoardHandler(EventData data) {
      this.data = data;
   }

   /**
    * A youth as the event log names them: the record ID, then the registration
    * number (P3 or W5) when there is one. Never a phone number, email address,
    * or birthdate.
    */
   protected static String logScout(ScoutRecord scout) {
      String regNum = scout.getRegNum();
      return Log.clean(scout.getID()) + (regNum == null || regNum.isEmpty() ? "" : " (" + Log.clean(regNum) + ")");
   }

   /** The IDs of these adults, comma separated, for the event log. */
   protected static String logAdultIds(List<AdultRecord> adults) {
      StringBuilder ids = new StringBuilder();
      for (AdultRecord adult : adults) {
         if (ids.length() > 0) {
            ids.append(',');
         }
         ids.append(Log.clean(adult.getID()));
      }
      return ids.length() == 0 ? "(none)" : ids.toString();
   }

   /** Whether a free-text request parameter was supplied, without saying what it held. */
   protected static String logSupplied(String value) {
      return value == null || value.isEmpty() ? "none" : "supplied";
   }

   /** Writes the request and its parameters to the verbose log. */
   protected void logRequest(HttpServletRequest request) {
      Log.request(request);
   }

   /**
    * Refuses the request, with {@code message} as the reason the operator sees.
    *
    * <p>The body is UTF-8 and the length counts its bytes ({@link ResponseText}), so a
    * reason with an accented letter in a name is sent whole.
    */
   protected void sendError(String message, HttpServletResponse response) throws IOException {
      String body = String.valueOf(message);
      // The one place a refusal is logged: the access line shows the status,
      // this line shows the handler and the reason the operator was given.
      Log.info(this.getClass().getSimpleName() + ": REFUSED: " + body);
      ResponseText.write(response, "text/plain", body);
      response.setStatus(304);
   }

   /** Answers {@code OK.} with status 200. */
   public void sendSuccess(HttpServletResponse response) throws IOException {
      // No line here: the access line already says which POST answered 200, and the
      // handler's own line says what it did.
      String body = "OK.";
      ResponseText.write(response, "text/plain", body);
      response.setStatus(200);
   }
}
