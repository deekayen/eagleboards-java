package shkc.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The part of the SignUpGenius import that talks to the web service: finding
 * today's sign-up and downloading its report. Nothing here touches the event's
 * data files; {@link SignUpGeniusPlugin} turns what comes back into records.
 *
 * <p>The API key travels in the request's URL, so no URL and no key is written
 * to the event log, and a URL that appears in the console's verbose output or
 * in an exception message is passed through {@link Log#redact} first.
 */
public class SignUpGeniusClient {
   private static final String API_BASE = "https://api.signupgenius.com/v2/k/signups/";

   /** The longest piece of the service's own error message that is logged. */
   private static final int MAX_SERVICE_MESSAGE_CHARS = 200;

   /** What the JDK's HTTP client says when the service answers with an error status. */
   private static final Pattern HTTP_STATUS_IN_MESSAGE = Pattern.compile("response code: (\\d{3})");

   private final String key;

   /** @param key the SignUpGenius API key */
   public SignUpGeniusClient(String key) {
      this.key = key;
      // The service's own messages and the HTTP client's errors are logged, and either
      // may quote the key in a form that has no "key=" in front of it.
      Log.registerSecret(key);
   }

   /**
    * Finds the active sign-up for the Eagle board that covers today: its dates
    * include today and its title mentions both "eagle" and "board".
    *
    * @throws Exception if SignUpGenius cannot be reached or has no such sign-up
    */
   public String findTodaysSignupId() throws Exception {
      JsonNode response = this.fetchJson(API_BASE + "created/active/?user_key=" + this.key, "the list of active sign-ups");
      String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
      JsonNode data = response.get("data");
      if (data == null) {
         this.logResponseWithoutData(response, "the list of active sign-ups");
      }

      for (int i = 0; i < data.size(); i++) {
         JsonNode entry = data.get(i);
         String foundId = entry.get("signupid").asText();
         String endDate = entry.get("enddatestring").asText();
         String title = entry.get("title").asText();
         String startDate = entry.get("startdatestring").asText();
         if (coversDay(today, startDate, endDate) && isEagleBoardTitle(title)) {
            System.out.println(
               "Using SignUp {\n   id         : " + foundId
                  + "\n   title      : " + title
                  + "\n   start-date : " + startDate
                  + "\n   end-date   : " + endDate
                  + "\n}");
            Log.info("signup lookup: " + data.size() + " active sign-ups, using id=" + foundId + " title=\"" + title
               + "\" dates=" + startDate + " to " + endDate + " (today is " + today + ")");
            return foundId;
         }

         System.out.println("out of date range: " + title + "(" + foundId + ")");
         Log.info("signup lookup: not used id=" + foundId + " title=\"" + title + "\" dates=" + startDate + " to " + endDate
            + (coversDay(today, startDate, endDate) ? ": title lacks 'eagle' and 'board'" : ": today " + today + " is outside its dates"));
      }

      Log.warn("signup lookup: none of the " + data.size() + " active sign-ups covers " + today
         + " with 'eagle' and 'board' in its title, so there is nothing to import (pass -sugid to name one)");
      throw new Exception("no valid )in range SignUpIds found.");
   }

   /**
    * Downloads the report of everyone who filled a slot of the sign-up.
    *
    * @throws Exception if SignUpGenius cannot be reached
    */
   public JsonNode fetchFilledReport(String signupId) throws Exception {
      String url = API_BASE + "report/filled/" + signupId + "/?user_key=" + this.key;
      Log.verbose("REQUESTING: signup data (URL: " + Log.redact(url) + ")");
      return this.fetchJson(url, "the filled report of sign-up " + Log.clean(signupId));
   }

   /**
    * Sends the request and parses the JSON that comes back.
    *
    * @param what describes the request for the log; it must not hold the URL, which holds the key
    */
   private JsonNode fetchJson(String url, String what) throws Exception {
      long startedMillis = System.currentTimeMillis();
      InputStream responseStream;
      try {
         responseStream = new FileLocator(".").getInputStream(url);
      } catch (IOException connectFailure) {
         String shown = Log.redact(connectFailure.getMessage());
         Log.verbose("signup request error: " + connectFailure.getClass().getName() + ": " + shown);
         // FileLocator keeps the real failure (a 403, an unknown host) as the cause of its "invalid location".
         Throwable reason = connectFailure.getCause() != null ? connectFailure.getCause() : connectFailure;
         String reasonShown = Log.redact(reason.getMessage());
         Matcher status = HTTP_STATUS_IN_MESSAGE.matcher(String.valueOf(reasonShown));
         Log.warn("signup request FAILED: " + what + " after " + (System.currentTimeMillis() - startedMillis) + " ms"
            + (status.find() ? ", http status " + status.group(1) : ", no http status (not reached: offline, DNS, or a timeout?)")
            + ": " + reason.getClass().getSimpleName() + " " + reasonShown);
         throw new Exception("error connecting to SignUpGenius server (" + shown);
      }

      try (InputStream openStream = responseStream) {
         JsonNode parsed = new ObjectMapper().readTree(new InputStreamReader(openStream));
         Log.info("signup request ok: " + what + " answered and parsed in " + (System.currentTimeMillis() - startedMillis) + " ms");
         return parsed;
      } catch (IOException readFailure) {
         Log.warn("signup request FAILED: " + what + " answered but the body could not be read as JSON: "
            + readFailure.getClass().getSimpleName() + " " + Log.redact(readFailure.getMessage()));
         throw readFailure;
      }
   }

   /** Says what the service sent instead of a "data" member (often an error message), before the caller's null check fails. */
   private void logResponseWithoutData(JsonNode response, String what) {
      String message = response.path("message").asText("");
      Log.warn("signup response for " + what + " has no \"data\" member, so the import will fail: success="
         + response.path("success").asText("?") + " message=\""
         + Log.redact(message.length() > MAX_SERVICE_MESSAGE_CHARS ? message.substring(0, MAX_SERVICE_MESSAGE_CHARS) : message) + "\"");
   }

   private static boolean isEagleBoardTitle(String title) {
      String lowerCaseTitle = title.toLowerCase();
      return lowerCaseTitle.contains("board") && lowerCaseTitle.contains("eagle");
   }

   /**
    * Whether a sign-up's dates cover {@code day} (yyyy-MM-dd). Compared on the
    * date alone: the API's strings carry a time too, and "2026-09-22" sorts
    * before "2026-09-22 18:30:00", which missed a sign-up on its first day.
    */
   public static boolean coversDay(String day, String startDate, String endDate) {
      return day.compareTo(dateOnly(startDate)) >= 0 && day.compareTo(dateOnly(endDate)) <= 0;
   }

   private static String dateOnly(String stamp) {
      return stamp.length() > 10 ? stamp.substring(0, 10) : stamp;
   }
}
