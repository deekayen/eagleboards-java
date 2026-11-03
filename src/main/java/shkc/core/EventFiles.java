package shkc.core;

import java.io.File;
import java.io.IOException;

/**
 * Where an event keeps its files: the data folder and the CSV and properties
 * files inside it (or, for the adult history and the settings, wherever the
 * command line pointed).
 *
 * @param dataRoot            the event's folder, normally named for today's date
 * @param scouts              the youth signed in today
 * @param adults              the adults signed in today
 * @param rooms               the rooms
 * @param scoutsScheduled     the youth who reserved a slot beforehand
 * @param adultHistory        every adult who has ever signed in
 * @param adultHistoryIsNew   true when {@code adultHistory} did not exist yet
 * @param config              the settings file
 */
record EventFiles(File dataRoot, File scouts, File adults, File rooms, File scoutsScheduled,
      File adultHistory, boolean adultHistoryIsNew, File config) {

   private static final String ADULT_HISTORY_FILE_NAME = "adult_history.csv";
   private static final String CONFIG_FILE_NAME = "config.properties";

   /**
    * Works out the files from the command line, creating the data folder if
    * it is missing.
    *
    * @param dataDir          the {@code -d} value: the event's folder
    * @param adultHistoryPath the {@code -a} value, or null for {@code adult_history.csv} in the folder
    * @param configFileName   the {@code -c} value, or null to use {@code config.properties}
    * @throws IOException if the data folder cannot be created
    * @throws Exception   if {@code -c} names a file that does not exist
    */
   static EventFiles resolve(String dataDir, String adultHistoryPath, String configFileName) throws Exception {
      File dataRoot = new File(dataDir);
      if (!dataRoot.exists() && !dataRoot.mkdir()) {
         throw new IOException("cannot create data directory: " + dataRoot.getAbsolutePath());
      }

      File adultHistory = adultHistoryPath != null ? new File(adultHistoryPath) : new File(dataRoot, ADULT_HISTORY_FILE_NAME);

      // SPEC.md D-9: a new install has no adult history, and no release ships
      // one (it would hold participant data), so a missing one is started
      // empty rather than refused. The full path is printed, so a
      // mistyped -a shows up as a new history in an unexpected place.
      boolean adultHistoryIsNew = !adultHistory.exists();

      if (configFileName != null && !new File(configFileName).exists()) {
         throw new Exception("error: config  file '" + configFileName + "' does not exist.");
      }

      return new EventFiles(dataRoot, new File(dataRoot, "scouts.csv"), new File(dataRoot, "adults.csv"),
         new File(dataRoot, "rooms.csv"), new File(dataRoot, "scouts_scheduled.csv"),
         adultHistory, adultHistoryIsNew, findConfig(dataRoot, configFileName));
   }

   /**
    * The settings file to load. {@code -c} must name an existing file (checked
    * by the caller), but what is then loaded is that name looked up
    * <em>inside the data folder</em>, and failing that the
    * {@code config.properties} in the current directory. This is how the
    * original program behaved and is kept so existing launch scripts keep
    * working.
    */
   private static File findConfig(File dataRoot, String configFileName) {
      String name = configFileName != null ? configFileName : CONFIG_FILE_NAME;
      File inDataRoot = new File(dataRoot, name);
      return inDataRoot.exists() ? inDataRoot : new File(CONFIG_FILE_NAME);
   }
}
