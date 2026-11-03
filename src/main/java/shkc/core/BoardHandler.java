package shkc.core;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Base class for the handlers that carry out a board step (seat, start,
 * complete, reset, undo, and so on).
 *
 * <p>These handlers answer in plain text: {@code OK.} with status 200 when the
 * step was done, or the reason it was refused with status 304. The browser
 * adapter ({@code ebAction} in eb-data.js) counts only a 200 answer that starts
 * with "OK" as success and shows any other text to the operator as the reason.
 * The odd 304 is inherited from the original program and kept, because the
 * pages key off the status.
 */
public abstract class BoardHandler implements WebServer.WebHandler {
   /** The shared event state: the record files, the lock and the Undo log. */
   protected final EventData data;

   protected BoardHandler(EventData data) {
      this.data = data;
   }

   /** Writes the request and its parameters to the verbose log. */
   protected void logRequest(HttpServletRequest request) {
      Log.request(request);
   }

   /**
    * Refuses the request, with {@code message} as the reason the operator sees.
    *
    * <p>The content length counts characters, not bytes, as the original
    * program did; a reason with an accented letter is therefore one byte short.
    */
   protected void sendError(String message, HttpServletResponse response) throws IOException {
      String body = String.valueOf(message);
      response.setContentLength(body.length());
      response.setContentType("text/plain");
      response.getOutputStream().write(body.getBytes());
      response.setStatus(304);
   }

   /** Answers {@code OK.} with status 200. */
   public void sendSuccess(HttpServletResponse response) throws IOException {
      String body = "OK.";
      response.setContentLength(body.length());
      response.setContentType("text/plain");
      response.getOutputStream().write(body.getBytes());
      response.setStatus(200);
   }
}
