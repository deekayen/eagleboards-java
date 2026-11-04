package shkc.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
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
 * Talking to the web service is {@link SignUpGeniusClient}'s job; this class
 * only turns the report it brings back into records, which {@link #importReport}
 * does without any network.
 */
public class SignUpGeniusPlugin {
   /** Positions of the custom questions on the sign-up form, in the order they are asked. */
   private static final int UNIT_FIELD_INDEX = 0;
   private static final int PHONE_FIELD_INDEX = 1;
   private static final int LEADER_FIELD_INDEX = 2;

   /** What a sign-up may change on an adult already in the history. */
   private static final String[] ADULT_COLUMNS_TO_REFRESH = {"Phone"};

   private final String key;
   private final SignUpGeniusClient client;
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
      this.client = new SignUpGeniusClient(key);
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
         this.signupId = this.client.findTodaysSignupId();
      }

      System.out.println("\n   SignupID : " + this.signupId + "\n");
      JsonNode report = this.client.fetchFilledReport(this.signupId);

      String thisMonth = new SimpleDateFormat("yyyy-MM").format(new Date());
      this.importReport(report, thisMonth);
   }

   /**
    * Adds everyone in a downloaded report whose slot is in {@code month}, then
    * saves the youth file.
    *
    * @param report the parsed {@code report/filled} response
    * @param month  the month to import, as {@code yyyy-MM}; other months' slots are skipped
    */
   public void importReport(JsonNode report, String month) throws IOException {
      JsonNode signups = report.get("data").get("signup");
      for (int i = 0; i < signups.size(); i++) {
         JsonNode entry = signups.get(i);
         if (entry.get("startdatestring").asText().startsWith(month)) {
            this.importEntry(entry);
         }
      }

      this.scoutsScheduledRecords.store();
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
}
