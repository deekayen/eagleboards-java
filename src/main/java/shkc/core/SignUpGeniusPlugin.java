package shkc.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Imports the event's RSVPs from SignUpGenius at startup.
 *
 * <p>Each person who signed up for this month becomes a record: a youth goes
 * into the scouts-scheduled file, and a volunteer whose item mentions "adult"
 * goes into the adult history, so that sign-in can pre-fill their details.
 * Someone already on file (same email) is not added twice.
 *
 * <p>If no sign-up ID was given, the plugin looks one up: the active sign-up
 * whose dates cover today and whose title mentions both "eagle" and "board".
 */
public class SignUpGeniusPlugin {
   private static final String API_BASE = "https://api.signupgenius.com/v2/k/signups/";

   /** Positions of the custom questions on the sign-up form, in the order they are asked. */
   private static final int UNIT_FIELD_INDEX = 0;
   private static final int PHONE_FIELD_INDEX = 1;
   private static final int LEADER_FIELD_INDEX = 2;

   /** What a sign-up may change on an adult already in the history. */
   private static final String[] ADULT_COLUMNS_TO_REFRESH = {"Phone"};

   private final String key;
   private String signupId;
   private final DataRecordFile<AdultRecord> adultHistoryRecords;
   private final DataRecordFile<ScoutRecord> scoutsScheduledRecords;

   /**
    * @param key the SignUpGenius API key
    * @param signupId the sign-up to read, or null to look for today's
    * @param adultHistory where volunteers are added; null to skip adults
    * @param scoutsScheduled where youth are added; null to skip youth
    */
   public SignUpGeniusPlugin(String key, String signupId, DataRecordFile<AdultRecord> adultHistory, DataRecordFile<ScoutRecord> scoutsScheduled) {
      this.key = key;
      this.signupId = signupId;
      this.adultHistoryRecords = adultHistory;
      this.scoutsScheduledRecords = scoutsScheduled;
   }

   /**
    * Reads the sign-up report and adds this month's people to the data files.
    *
    * @throws Exception if SignUpGenius cannot be reached or has no sign-up for today
    */
   public void populatePreRegistrations() throws Exception {
      System.out.println("\n\n   Loading Preregistrations from SignupGenius: " + this.key + "\n");
      if (this.signupId == null) {
         System.out.println("\n   Looking up SignupID from SignupGenius...\n");
         this.signupId = this.findTodaysSignupId();
      }

      System.out.println("\n   SignupID : " + this.signupId + "\n");
      String url = API_BASE + "report/filled/" + this.signupId + "/?user_key=" + this.key;
      Log.verbose("REQUESTING: signup data (URL: " + url + ")");
      JsonNode response = this.fetchJson(url);

      String thisMonth = new SimpleDateFormat("yyyy-MM").format(new Date());
      JsonNode signups = response.get("data").get("signup");
      for (int i = 0; i < signups.size(); i++) {
         JsonNode entry = signups.get(i);
         if (entry.get("startdatestring").asText().startsWith(thisMonth)) {
            this.importEntry(entry);
         }
      }

      this.scoutsScheduledRecords.store();
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

   /** Adds one person from the report: a volunteer if their item says "adult", otherwise a youth. */
   private void importEntry(JsonNode entry) throws IOException {
      String firstName = entry.get("firstname").asText();
      String lastName = entry.get("lastname").asText();
      String item = entry.get("item").asText();
      String email = entry.get("email").asText();
      JsonNode customFields = entry.get("customfields");
      String unit = this.getCustomField(customFields, UNIT_FIELD_INDEX);
      String phone = this.getCustomField(customFields, PHONE_FIELD_INDEX);
      String leader = this.getCustomField(customFields, LEADER_FIELD_INDEX);
      Log.verbose(" EVALUATING: " + firstName + " " + lastName + " (" + item + ")");

      if (item.toLowerCase().contains("adult")) {
         if (this.adultHistoryRecords != null) {
            this.importAdult(firstName, lastName, phone, unit, email);
         }
      } else if (this.scoutsScheduledRecords != null) {
         this.importScout(firstName, lastName, item, unit, leader, email);
      }
   }

   private void importAdult(String firstName, String lastName, String phone, String unit, String email) throws IOException {
      AdultRecord adult = this.adultHistoryRecords.createNew();
      PreRegFieldConverters.FIRST_NAME.convert(adult, "", firstName);
      PreRegFieldConverters.LAST_NAME.convert(adult, "", lastName);
      PreRegFieldConverters.PHONE.convert(adult, "", phone);
      adult.setValue("ProjectReview", AdultRole.MEMBER);
      adult.setValue("FinalBoard", AdultRole.MEMBER);
      PreRegFieldConverters.UNIT_AS_TYPED.convert(adult, "", unit);
      adult.setValue("Email", email);

      List<AdultRecord> sameEmail = this.adultHistoryRecords.get("Email", email);
      if (sameEmail.isEmpty()) {
         Log.verbose("SIGNUP-GENIUS/ADDING ADULT " + adult);
         this.adultHistoryRecords.add(adult, false);
      } else if (sameEmail.size() == 1) {
         Log.verbose("SIGNUP-GENIUS/UPDATING ADULT : " + sameEmail.get(0));
         sameEmail.get(0).updateFrom(adult, ADULT_COLUMNS_TO_REFRESH);
      }

      this.adultHistoryRecords.store();
   }

   private void importScout(String firstName, String lastName, String item, String unit, String leader, String email) {
      ScoutRecord scout = this.scoutsScheduledRecords.createNew();
      PreRegFieldConverters.FIRST_NAME.convert(scout, "", firstName);
      PreRegFieldConverters.LAST_NAME.convert(scout, "", lastName);
      // No phone number for a youth (SPEC.md D-8); an adult's is kept above.
      PreRegFieldConverters.SCOUT_BOARD_TYPE.convert(scout, "", item);
      PreRegFieldConverters.UNIT_AS_TYPED.convert(scout, "", unit);
      PreRegFieldConverters.LEADER.convert(scout, "", leader);
      scout.setValue("Email", email);
      scout.setValue("ID", "SCOUT:" + scout.getLast() + ":" + scout.getFirst() + ":" + scout.getUnit());

      List<ScoutRecord> sameEmail = this.scoutsScheduledRecords.get("Email", email);
      if (sameEmail.isEmpty()) {
         this.scoutsScheduledRecords.add(scout, true);
         Log.verbose("SIGNUP-GENIUS/ADDING SCOUT " + scout);
      }
   }

   /** The answer to the sign-up's custom question at {@code index}, or "" if it was not answered. */
   private String getCustomField(JsonNode fields, int index) {
      try {
         JsonNode field = fields.get(index);
         return field.get("value").asText();
      } catch (Exception missing) {
         return "";
      }
   }

   /** Finds the active sign-up for the Eagle board that covers today. */
   private String findTodaysSignupId() throws Exception {
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
