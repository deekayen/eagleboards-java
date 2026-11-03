package shkc.core;

import java.io.File;

/**
 * Loads the youth and adults who signed up before the event, so that signing
 * in at the tablet can pre-fill their details.
 *
 * <p>There are two sources, and the first one given wins: a pre-registration
 * spreadsheet saved as CSV ({@code -p}), or the SignUpGenius web service
 * ({@code -sugkey}). With neither, the event simply starts without any.
 */
final class PreRegistrationLoader {
   /** A SignUpGenius key is longer than ten characters; anything shorter is treated as "no key". */
   private static final int MIN_SIGNUP_GENIUS_KEY_LENGTH = 11;

   private final DataRecordFile<ScoutRecord> scoutsScheduled;
   private final DataRecordFile<AdultRecord> adultHistory;

   /**
    * @param scoutsScheduled where the youth reservations are put
    * @param adultHistory    where the adult volunteers are put
    */
   PreRegistrationLoader(DataRecordFile<ScoutRecord> scoutsScheduled, DataRecordFile<AdultRecord> adultHistory) {
      this.scoutsScheduled = scoutsScheduled;
      this.adultHistory = adultHistory;
   }

   /**
    * Loads from the first source that was given.
    *
    * @param preregFile the {@code -p} CSV path, or null
    * @param sugKey     the SignUpGenius API key, or null
    * @param sugId      the SignUpGenius sign-up ID, or null for all of them
    * @throws Exception if the CSV file named does not exist or cannot be converted
    */
   void load(String preregFile, String sugKey, String sugId) throws Exception {
      if (preregFile != null) {
         loadSpreadsheet(preregFile);
      } else if (sugKey != null && sugKey.length() >= MIN_SIGNUP_GENIUS_KEY_LENGTH) {
         loadSignUpGenius(sugKey, sugId);
      } else {
         System.out.println("\n   no prereg-file or SignupGenius DB loaded \n\n");
      }
   }

   private void loadSpreadsheet(String preregFile) throws Exception {
      File spreadsheet = new File(preregFile);
      if (!spreadsheet.exists()) {
         throw new Exception("error: no preregistration file '" + preregFile + "' found.");
      }

      System.out.println("\n\n   loading SCOUT pre-registrations: " + preregFile + "\n\n");
      NegaPreRegScoutRecordConverter scoutConverter = new NegaPreRegScoutRecordConverter(this.scoutsScheduled);
      this.scoutsScheduled.clearAll();
      scoutConverter.convert(spreadsheet);
      this.scoutsScheduled.store();
      System.out.println("\n\n   loading ADULT pre-registrations: " + preregFile + "\n\n");
      NegaPreRegAdultRecordConverter adultConverter = new NegaPreRegAdultRecordConverter(this.adultHistory);
      adultConverter.convert(spreadsheet);
      this.adultHistory.store();
   }

   /** A SignUpGenius outage must not stop the event, so a failure is printed and the event starts without it. */
   private void loadSignUpGenius(String sugKey, String sugId) {
      try {
         SignUpGeniusPlugin signUpGenius = new SignUpGeniusPlugin(sugKey, sugId, this.adultHistory, this.scoutsScheduled);
         signUpGenius.populatePreRegistrations();
      } catch (Exception signUpFailure) {
         System.out.println("\n\n   SignupGenius Error: " + signUpFailure.getMessage() + "\n\n");
      }
   }
}
