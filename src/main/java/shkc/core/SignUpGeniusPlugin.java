package shkc.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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

   // What the last importReport did, for its summary line.
   private int entriesInOtherMonths;
   private int entriesWithoutDestination;
   private int adultsAdded;
   private int adultsRefreshed;
   private int adultsSkipped;
   private int youthAdded;
   private int youthAlreadyThere;
   private int youthDroppedForSharedEmail;

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
      // The key itself is never printed or logged, only its last few characters.
      System.out.println("\n\n   Loading Preregistrations from SignupGenius: key " + PreRegistrationLoader.keyEnding(this.key) + "\n");
      if (this.signupId == null) {
         System.out.println("\n   Looking up SignupID from SignupGenius...\n");
         this.signupId = this.client.findTodaysSignupId();
      } else {
         Log.info("signup import: using the sign-up id given on the command line, id=" + Log.clean(this.signupId));
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
      JsonNode data = report.get("data");
      JsonNode signups = data == null ? null : data.get("signup");
      if (signups == null) {
         // The next line fails with a NullPointerException, as it always did; this says why.
         Log.warn("signup import: the report has no data.signup list (top-level members: " + memberNames(report)
            + (data == null ? "" : ", data members: " + memberNames(data)) + ")");
      }
      this.entriesInOtherMonths = 0;
      this.adultsAdded = 0;
      this.adultsRefreshed = 0;
      this.adultsSkipped = 0;
      this.youthAdded = 0;
      this.youthAlreadyThere = 0;
      this.youthDroppedForSharedEmail = 0;
      this.entriesWithoutDestination = 0;
      for (int i = 0; i < signups.size(); i++) {
         JsonNode entry = signups.get(i);
         if (entry.get("startdatestring").asText().startsWith(month)) {
            this.importEntry(entry);
         } else {
            this.entriesInOtherMonths++;
         }
      }

      this.scoutsScheduledRecords.store();
      Log.info("signup import: month=" + month + " signups=" + signups.size() + " outside the month=" + this.entriesInOtherMonths
         + " | youth added=" + this.youthAdded + " already there (same email and ID)=" + this.youthAlreadyThere
         + " dropped for sharing another youth's email=" + this.youthDroppedForSharedEmail
         + " | adults added=" + this.adultsAdded + " refreshed=" + this.adultsRefreshed + " skipped=" + this.adultsSkipped
         + (this.entriesWithoutDestination > 0 ? " | no file to put " + this.entriesWithoutDestination + " entries in" : ""));
   }

   /** The names of a JSON object's members, never their values. */
   private static String memberNames(JsonNode object) {
      List<String> names = new ArrayList<>();
      object.fieldNames().forEachRemaining(names::add);
      return names.toString();
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
         } else {
            this.entriesWithoutDestination++;
         }
      } else if (this.scoutsScheduledRecords != null) {
         this.importScout(firstName, lastName, item, unit, leader, email);
      } else {
         this.entriesWithoutDestination++;
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

      // This record has no ID yet (it gets one when the file is next read), so name the adult as the ID will read.
      String adultName = "ADULT:" + adult.getLast() + ":" + adult.getFirst() + ":" + adult.getUnit();
      List<AdultRecord> sameEmail = this.adultHistoryRecords.get("Email", email);
      if (sameEmail.isEmpty()) {
         Log.verbose("SIGNUP-GENIUS/ADDING ADULT " + adult);
         this.adultHistoryRecords.add(adult, false);
         this.adultsAdded++;
         Log.info("signup import: adult " + adultName + " added to the adult history");
      } else if (sameEmail.size() == 1) {
         Log.verbose("SIGNUP-GENIUS/UPDATING ADULT : " + sameEmail.get(0));
         sameEmail.get(0).updateFrom(adult, ADULT_COLUMNS_TO_REFRESH);
         this.adultsRefreshed++;
         Log.info("signup import: adult " + adultName + " matched " + sameEmail.get(0).getID()
            + " by email, Phone refreshed, no new record");
      } else {
         this.adultsSkipped++;
         Log.warn("signup import: adult " + adultName + " SKIPPED: " + sameEmail.size()
            + " adults in the history share this email, so none was guessed");
      }

      this.adultHistoryRecords.store();
   }

   private void importScout(String firstName, String lastName, String item, String unit, String leader, String email) {
      ScoutRecord scout = this.scoutsScheduledRecords.createNew();
      boolean hasFirstName = PreRegFieldConverters.FIRST_NAME.convert(scout, "", firstName);
      boolean hasLastName = PreRegFieldConverters.LAST_NAME.convert(scout, "", lastName);
      // No phone number for a youth (SPEC.md D-8); an adult's is kept above.
      boolean knownItem = PreRegFieldConverters.SCOUT_BOARD_TYPE.convert(scout, "", item);
      PreRegFieldConverters.UNIT_AS_TYPED.convert(scout, "", unit);
      PreRegFieldConverters.LEADER.convert(scout, "", leader);
      scout.setValue("Email", email);
      scout.setValue("ID", "SCOUT:" + scout.getLast() + ":" + scout.getFirst() + ":" + scout.getUnit());
      if (!hasFirstName || !hasLastName) {
         // The record is still kept, as it always was, under an ID that has a blank part.
         Log.warn("signup import: youth " + scout.getID() + " has no " + (hasFirstName ? "" : "first ") + (hasLastName ? "" : "last ")
            + "name in the sign-up, so its ID is incomplete");
      }
      if (!knownItem) {
         Log.warn("signup import: youth " + scout.getID() + " has an item that is neither a review nor a project, board type left at its default, item=\""
            + Log.clean(item) + "\"");
      }

      List<ScoutRecord> sameEmail = this.scoutsScheduledRecords.get("Email", email);
      if (sameEmail.isEmpty()) {
         this.scoutsScheduledRecords.add(scout, true);
         this.youthAdded++;
         Log.verbose("SIGNUP-GENIUS/ADDING SCOUT " + scout);
         Log.info("signup import: youth " + scout.getID() + " added to " + this.scoutsScheduledRecords.getFileName()
            + " (board " + Log.safeValue("BoardType", scout.getValue("BoardType")) + ")");
      } else {
         // Two youth from one family can share a parent's email: the second is dropped, which is worth knowing
         // when "the scout who RSVP'd was not pre-filled".
         boolean sameYouth = false;
         for (ScoutRecord existing : sameEmail) {
            sameYouth = sameYouth || existing.getID().equals(scout.getID());
         }
         if (sameYouth) {
            // The same RSVP read again, as every restart does: nothing is lost.
            this.youthAlreadyThere++;
            Log.info("signup import: youth " + scout.getID() + " NOT added, already imported (same email and ID)");
         } else {
            // A different youth: the RSVP is lost, and this scout will sign in as a walk-in
            // with nothing pre-filled, which is how "I RSVP'd but it didn't know me" starts.
            this.youthDroppedForSharedEmail++;
            Log.warn("signup import: youth " + scout.getID() + " NOT added, " + sameEmail.size()
               + " other youth already have the same email as " + sameEmail.get(0).getID()
               + ", so this RSVP is lost and the scout will sign in as a walk-in");
         }
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
