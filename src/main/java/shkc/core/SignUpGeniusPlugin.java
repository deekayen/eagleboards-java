package shkc.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class SignUpGeniusPlugin {
   private String _key;
   private String _signupID;
   private NegaPreRegScoutRecordConverter.FirstNameConverter _firstNameConverter = new NegaPreRegScoutRecordConverter.FirstNameConverter();
   private NegaPreRegScoutRecordConverter.LastNameConverter _lastNameConverter = new NegaPreRegScoutRecordConverter.LastNameConverter();
   private NegaPreRegScoutRecordConverter.PhoneConverter _phoneConverter = new NegaPreRegScoutRecordConverter.PhoneConverter();
   private NegaPreRegScoutRecordConverter.LeaderConverter _leaderConverter = new NegaPreRegScoutRecordConverter.LeaderConverter();
   private NegaPreRegScoutRecordConverter.UnitConverter _unitConverter = new NegaPreRegScoutRecordConverter.UnitConverter();
   private NegaPreRegScoutRecordConverter.BoardTypeConverter _boardTypeConverter = new NegaPreRegScoutRecordConverter.BoardTypeConverter();
   private DataRecordFile<AdultRecord> _adultHistoryRecords;
   private DataRecordFile<ScoutRecord> _scoutsScheduledRecords;

   public SignUpGeniusPlugin(String key, String signupId, DataRecordFile<AdultRecord> adultHistory, DataRecordFile<ScoutRecord> scoutsScheduled) {
      this._key = key;
      this._signupID = signupId;
      this._adultHistoryRecords = adultHistory;
      this._scoutsScheduledRecords = scoutsScheduled;
   }

   public void populatePreRegistrations() throws Exception {
      System.out.println("\n\n   Loading Preregistrations from SignupGenius: " + this._key + "\n");
      if (this._signupID == null) {
         System.out.println("\n   Looking up SignupID from SignupGenius...\n");
         this._signupID = this.getSignupID();
      }

      System.out.println("\n   SignupID : " + this._signupID + "\n");
      FileLocator locator = new FileLocator(".");

      InputStream responseStream;
      try {
         String url = "https://api.signupgenius.com/v2/k/signups/report/filled/" + this._signupID + "/?user_key=" + this._key;
         EagleBoardScheduler.verbose("REQUESTING: signup data (URL: " + url + ")");
         responseStream = locator.getInputStream(url);
      } catch (IOException connectFailure) {
         EagleBoardScheduler.verbose("signup request error: " + connectFailure);
         throw new Exception("error connecting to SignUpGenius server (" + connectFailure.getMessage());
      }

      JsonNode response = new ObjectMapper().readTree(new InputStreamReader(responseStream));
      SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy-MM");
      String thisMonth = monthFormat.format(new Date());
      JsonNode data = response.get("data");
      JsonNode signups = data.get("signup");

      for (int i = 0; i < signups.size(); i++) {
         JsonNode entry = signups.get(i);
         String startDate = entry.get("startdatestring").asText();
         if (startDate.startsWith(thisMonth)) {
            String firstName = entry.get("firstname").asText();
            String lastName = entry.get("lastname").asText();
            String item = entry.get("item").asText();
            String email = entry.get("email").asText();
            JsonNode customFields = entry.get("customfields");
            String unit = this.getCustomField(customFields, 0);
            String phone = this.getCustomField(customFields, 1);
            String leader = this.getCustomField(customFields, 2);
            EagleBoardScheduler.verbose(" EVALUATING: " + firstName + " " + lastName + " (" + item + ")");
            if (item.toLowerCase().contains("adult")) {
               if (this._adultHistoryRecords != null) {
                  AdultRecord adult = this._adultHistoryRecords.createNew();
                  this._firstNameConverter.convert(adult, "", firstName);
                  this._lastNameConverter.convert(adult, "", lastName);
                  this._phoneConverter.convert(adult, "", phone);
                  adult.setValue("ProjectReview", "Member");
                  adult.setValue("FinalBoard", "Member");
                  this._unitConverter.convert(adult, "", unit);
                  adult.setValue("Email", email);
                  List existingAdults = this._adultHistoryRecords.get("Email", email);
                  if (existingAdults != null && existingAdults.size() != 0) {
                     if (existingAdults.size() == 1) {
                        EagleBoardScheduler.verbose("SIGNUP-GENIUS/UPDATING ADULT : " + existingAdults.get(0));
                        ((AdultRecord)existingAdults.get(0)).updateFrom(adult, new String[]{"Phone"});
                     }
                  } else {
                     EagleBoardScheduler.verbose("SIGNUP-GENIUS/ADDING ADULT " + adult);
                     this._adultHistoryRecords.add(adult, false);
                  }

                  this._adultHistoryRecords.store();
               }
            } else if (this._scoutsScheduledRecords != null) {
               ScoutRecord scout = this._scoutsScheduledRecords.createNew();
               this._firstNameConverter.convert(scout, "", firstName);
               this._lastNameConverter.convert(scout, "", lastName);
               this._phoneConverter.convert(scout, "", phone);
               this._boardTypeConverter.convert(scout, "", item);
               this._unitConverter.convert(scout, "", unit);
               this._leaderConverter.convert(scout, "", leader);
               scout.setValue("Email", email);
               scout.setValue("ID", "SCOUT:" + scout.getLast() + ":" + scout.getFirst() + ":" + scout.getUnit());
               List existingScouts = this._scoutsScheduledRecords.get("Email", email);
               if (existingScouts == null || existingScouts.size() == 0) {
                  this._scoutsScheduledRecords.add(scout, true);
                  EagleBoardScheduler.verbose("SIGNUP-GENIUS/ADDING SCOUT " + scout);
               }
            }
         }
      }

      this._scoutsScheduledRecords.store();
   }

   private String getCustomField(JsonNode fields, int index) {
      try {
         JsonNode field = fields.get(index);
         return field.get("value").asText();
      } catch (Exception missing) {
         return "";
      }
   }

   private String getSignupID() throws Exception {
      FileLocator locator = new FileLocator(".");

      InputStream responseStream;
      try {
         responseStream = locator.getInputStream("https://api.signupgenius.com/v2/k/signups/created/active/?user_key=" + this._key);
      } catch (IOException connectFailure) {
         throw new Exception("error connecting to SignUpGenius server (" + connectFailure.getMessage());
      }

      JsonNode response = new ObjectMapper().readTree(new InputStreamReader(responseStream));
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
      String today = dateFormat.format(new Date());
      JsonNode data = response.get("data");

      for (int i = 0; i < data.size(); i++) {
         JsonNode entry = data.get(i);
         String signupId = entry.get("signupid").asText();
         String endDate = entry.get("enddatestring").asText();
         String title = entry.get("title").asText();
         String startDate = entry.get("startdatestring").asText();
         if (today.compareTo(endDate) <= 0 && today.compareTo(startDate) >= 0 && title.toLowerCase().contains("board") && title.toLowerCase().contains("eagle")) {
            System.out
               .println(
                  "Using SignUp {\n   id         : "
                     + signupId
                     + "\n   title      : "
                     + title
                     + "\n   start-date : "
                     + startDate
                     + "\n   end-date   : "
                     + endDate
                     + "\n}"
               );
            return signupId;
         }

         System.out.println("out of date range: " + title + "(" + signupId + ")");
      }

      throw new Exception("no valid )in range SignUpIds found.");
   }
}
