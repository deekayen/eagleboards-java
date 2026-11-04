package shkc.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * The part of the SignUpGenius import that talks to the web service: finding
 * today's sign-up and downloading its report. Nothing here touches the event's
 * data files; {@link SignUpGeniusPlugin} turns what comes back into records.
 */
public class SignUpGeniusClient {
   private static final String API_BASE = "https://api.signupgenius.com/v2/k/signups/";

   private final String key;

   /** @param key the SignUpGenius API key */
   public SignUpGeniusClient(String key) {
      this.key = key;
   }

   /**
    * Finds the active sign-up for the Eagle board that covers today: its dates
    * include today and its title mentions both "eagle" and "board".
    *
    * @throws Exception if SignUpGenius cannot be reached or has no such sign-up
    */
   public String findTodaysSignupId() throws Exception {
      JsonNode response = this.fetchJson(API_BASE + "created/active/?user_key=" + this.key);
      String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
      JsonNode data = response.get("data");

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
            return foundId;
         }

         System.out.println("out of date range: " + title + "(" + foundId + ")");
      }

      throw new Exception("no valid )in range SignUpIds found.");
   }

   /**
    * Downloads the report of everyone who filled a slot of the sign-up.
    *
    * @throws Exception if SignUpGenius cannot be reached
    */
   public JsonNode fetchFilledReport(String signupId) throws Exception {
      String url = API_BASE + "report/filled/" + signupId + "/?user_key=" + this.key;
      Log.verbose("REQUESTING: signup data (URL: " + url + ")");
      return this.fetchJson(url);
   }

   /** Sends the request and parses the JSON that comes back. */
   private JsonNode fetchJson(String url) throws Exception {
      InputStream responseStream;
      try {
         responseStream = new FileLocator(".").getInputStream(url);
      } catch (IOException connectFailure) {
         Log.verbose("signup request error: " + connectFailure);
         throw new Exception("error connecting to SignUpGenius server (" + connectFailure.getMessage());
      }

      return new ObjectMapper().readTree(new InputStreamReader(responseStream));
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
