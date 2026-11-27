package shkc.core;

import java.io.File;

/**
 * Loads the youth and adults who signed up before the event, so that signing
 * in at the tablet can pre-fill their details.
 *
 * <p>There are two sources, and the first one given wins: a pre-registration
 * spreadsheet saved as CSV ({@code -p}), or the SignUpGenius web service
 * ({@code -sugkey}). With neither, the event starts without any.
 */
final class PreRegistrationLoader {
   /** A SignUpGenius key is longer than ten characters; anything shorter is treated as "no key". */
   private static final int MIN_SIGNUP_GENIUS_KEY_LENGTH = 11;

   /** How much of a key the log shows, so the key in use can be told apart from another. */
   private static final int KEY_ENDING_CHARS = 4;

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
         Log.info("pre-registrations: source is the spreadsheet " + preregFile
            + (sugKey != null ? " (a SignUpGenius key was also given and is ignored, the first source wins)" : ""));
         loadSpreadsheet(preregFile);
      } else if (sugKey != null && sugKey.length() >= MIN_SIGNUP_GENIUS_KEY_LENGTH) {
         Log.info("pre-registrations: source is SignUpGenius, sign-up id "
            + (sugId == null ? "not given (look up today's)" : Log.clean(sugId)) + ", key ending " + keyEnding(sugKey));
         loadSignUpGenius(sugKey, sugId);
      } else {
         Log.info("pre-registrations: none loaded, no -prereg file and "
            + (sugKey == null ? "no SignUpGenius key" : "a SignUpGenius key of only " + sugKey.length()
               + " characters, too short to be one, so it was ignored"));
         System.out.println("\n   no prereg-file or SignupGenius DB loaded \n\n");
      }
   }

   /** The last four characters of a key, enough to tell which one was used without revealing it. */
   static String keyEnding(String key) {
      return key == null || key.length() < 2 * KEY_ENDING_CHARS ? "(hidden)" : "..." + key.substring(key.length() - KEY_ENDING_CHARS);
   }

   private void loadSpreadsheet(String preregFile) throws Exception {
      File spreadsheet = new File(preregFile);
      if (!spreadsheet.exists()) {
         Log.warn("pre-registrations: spreadsheet " + spreadsheet.getAbsolutePath() + " REFUSED: no such file, the event cannot start with it");
         throw new Exception("error: no preregistration file '" + preregFile + "' found.");
      }

      System.out.println("\n\n   loading SCOUT pre-registrations: " + preregFile + "\n\n");
      NegaPreRegScoutRecordConverter scoutConverter = new NegaPreRegScoutRecordConverter(this.scoutsScheduled);
      int youthBefore = this.scoutsScheduled.getRecords().size();
      this.scoutsScheduled.clearAll();
      Log.info("pre-registrations: youth in " + this.scoutsScheduled.getFileName() + " cleared (" + youthBefore + " before), reloading from the sheet");
      scoutConverter.convert(spreadsheet);
      this.scoutsScheduled.store();
      System.out.println("\n\n   loading ADULT pre-registrations: " + preregFile + "\n\n");
      NegaPreRegAdultRecordConverter adultConverter = new NegaPreRegAdultRecordConverter(this.adultHistory);
      int adultsBefore = this.adultHistory.getRecords().size();
      adultConverter.convert(spreadsheet);
      this.adultHistory.store();
      Log.info("pre-registrations: spreadsheet done, youth now " + this.scoutsScheduled.getRecords().size()
         + " (was " + youthBefore + "), adult history now " + this.adultHistory.getRecords().size() + " (was " + adultsBefore + ")");
   }

   /** A SignUpGenius outage must not stop the event, so a failure is printed and the event starts without it. */
   private void loadSignUpGenius(String sugKey, String sugId) {
      try {
         SignUpGeniusPlugin signUpGenius = new SignUpGeniusPlugin(sugKey, sugId, this.adultHistory, this.scoutsScheduled);
         signUpGenius.populatePreRegistrations();
      } catch (Exception signUpFailure) {
         // An HTTP client's message can quote the request's URL, and the URL holds the key.
         Log.warn("pre-registrations: SignUpGenius FAILED, the event starts without them: "
            + maskKey(String.valueOf(signUpFailure.getMessage()), sugKey), redactedCopy(signUpFailure, sugKey));
         System.out.println("\n\n   SignupGenius Error: " + maskKey(signUpFailure.getMessage(), sugKey) + "\n\n");
      }
   }

   /** {@code text} with any {@code key=} query value masked and, as a second guard, any appearance of the key itself. */
   static String maskKey(String text, String key) {
      String masked = Log.redact(text);
      if (masked != null && key != null && !key.isEmpty()) {
         masked = masked.replace(key, "***");
      }
      return masked;
   }

   /**
    * The exception with its message (and its causes') masked by {@link #maskKey}, so the stack
    * trace written to the console and file cannot carry the key.
    */
   static Throwable redactedCopy(Throwable failure, String key) {
      if (failure == null) {
         return null;
      }
      Exception copy = new Exception(failure.getClass().getName() + ": " + maskKey(failure.getMessage(), key),
         redactedCopy(failure.getCause(), key));
      copy.setStackTrace(failure.getStackTrace());
      return copy;
   }
}
