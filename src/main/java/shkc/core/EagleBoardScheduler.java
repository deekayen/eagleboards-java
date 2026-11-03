package shkc.core;

import java.io.IOException;
import java.time.LocalDate;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * The program's starting point: reads the command line, tells the operator
 * which URLs to open, sets up the event's data files and starts the web
 * server.
 *
 * <p>Nothing about how an event runs lives here. The work is in the classes
 * this one wires together: {@link EventFiles} (where the files are),
 * {@link EventData} (the loaded records and the lock), {@link EventRoutes}
 * (which URL is answered by which handler) and {@link WebServer}.
 */
public class EagleBoardScheduler {
   /** The port used when {@code -port} is not given. */
   private static final int DEFAULT_PORT = 8080;

   private static final String DEBUG_LOG_FILE = "eagle-board-scheduler.log";

   // Strong reference so the -debug logger configuration survives GC
   // (java.util.logging holds loggers weakly).
   private static Logger debugLogger = null;

   private final WebServer server;

   static {
      System.setProperty("java.awt.headless", "false");
   }

   public static void main(String[] args) throws Exception {
      Commandline cmdline = parseCommandLine(args);

      if (cmdline.hasFlag("debug")) {
         enableDebugLog();
      }

      if (cmdline.hasFlag("?:help")) {
         usage();
         System.exit(1);
      }

      Log.setVerbose(cmdline.hasFlag("v:verbose"));

      String today = LocalDate.now().toString();
      String dataDir = cmdline.getOption("d:dir", today);
      String preregFile = cmdline.getOption("p:prereg");
      String configFile = cmdline.getOption("c:config");
      String adultsFile = cmdline.getOption("a:adults");
      int port = cmdline.getIntOption("port", DEFAULT_PORT);
      String sugKey = cmdline.getOption("sugkey", null);
      String sugId = cmdline.getOption("sugid", null);
      // -bind <ip-prefix>: restrict the listener and the advertised URLs to the
      // single interface we actually serve from (e.g. "192.168." for the venue
      // wifi), so virtual adapters don't each pop their own dialog. When absent,
      // behavior is unchanged: listen on every interface and list them all.
      String bindPrefix = cmdline.getOption("bind", null);

      boolean showPopup = args.length == 0 || cmdline.hasFlag("w:windows");
      NetworkAddresses.announce(port, bindPrefix, showPopup);
      System.out.println("\n\n URL: http://<ip-address>:" + port + "\n\n");

      try {
         EagleBoardScheduler scheduler = new EagleBoardScheduler(port, dataDir, preregFile, adultsFile, configFile, sugKey, sugId);
         scheduler.run();
      } catch (Exception startupFailure) {
         startupFailure.printStackTrace();
         System.err.println("\n\n  ERROR: " + startupFailure.getMessage() + "\n\n");
         // SPEC.md D-9: end the process, so a script sees the failure and the
         // -w window listing URLs for a server that never started goes with it.
         System.exit(1);
      }
   }

   /** Reads the arguments, or prints what was wrong with them and exits. */
   private static Commandline parseCommandLine(String[] args) {
      String shortFlags = "vw?";
      String[] longFlags = new String[]{"verbose", "help", "debug", "windows"};
      String shortOptions = "dapc";
      String[] longOptions = new String[]{"dir", "prereg", "adults", "config", "port", "sugkey", "sugid", "bind"};

      try {
         return new Commandline(args, shortFlags, shortOptions, longFlags, longOptions);
      } catch (Exception parseFailure) {
         System.out.println("\n\n   ERROR:  invalid command line argument(s): " + parseFailure.getMessage() + "\n\n");
         System.exit(1);
         return null;
      }
   }

   /** {@code -debug}: send everything the program logs to a file as well. */
   private static void enableDebugLog() throws IOException {
      FileHandler logFile = new FileHandler(DEBUG_LOG_FILE);
      logFile.setFormatter(new SimpleFormatter());
      logFile.setLevel(Level.ALL);
      debugLogger = Logger.getLogger("shkc");
      debugLogger.setLevel(Level.ALL);
      debugLogger.addHandler(logFile);
      System.out.println("DEBUGGING ENABLED");
   }

   public static void usage() {
      System.out.println(
         "\n\n\n   USAGE\n\n"
         + "          java -jar eagle-board-scheduler.jar <options>\n\n"
         + "      OPTIONS\n\n"
         + "         -w[indows]                     : popup dialog for config info\n"
         + "         -v[erbose]                     : print verbose messages\n"
         + "         -h[elp]                        : print this message\n\n"
         + "         -d[ir]    <data-directory>     : directory where data files live\n\n"
         + "         -a[dult]  <adult-history-file> : file containing adult auto-fill data\n"
         + "         -c[onfig] <config-file>        : scheduler config file\n"
         + "         -p[rereg] <prereg-file>        : preregistration data file (csv) from district website\n"
         + "         -sugkey <signup-genius-key>    : SignupGenius API KEY\n"
         + "         -sugid  <signup-id>            : SignupGenius signup ID (optional)\n\n"
         + "         -bind   <ip-prefix>            : only listen on / advertise the interface whose\n"
         + "                                          IPv4 address starts with this (e.g. 192.168.);\n"
         + "                                          127.0.0.1 stays reachable either way;\n"
         + "                                          default is every interface\n\n"
      );
   }

   /**
    * Opens the event's files, loads the pre-registrations and registers the
    * handlers. Nothing is served until {@link #run()}.
    *
    * @param port             the port to listen on
    * @param dataDir          the event's folder (created if missing)
    * @param preregFile       pre-registration CSV, or null
    * @param adultHistoryPath adult history file, or null for the one in the folder
    * @param configFileName   settings file, or null
    * @param sugKey           SignUpGenius API key, or null
    * @param sugId            SignUpGenius sign-up ID, or null
    */
   public EagleBoardScheduler(int port, String dataDir, String preregFile, String adultHistoryPath, String configFileName, String sugKey, String sugId) throws Exception {
      EventFiles files = EventFiles.resolve(dataDir, adultHistoryPath, configFileName);

      this.server = new WebServer("WEBROOT");
      this.server.setPort(port);

      EventData data = openEventData(files);
      new PreRegistrationLoader(data.getScoutsScheduled(), data.getAdultHistory()).load(preregFile, sugKey, sugId);
      EventRoutes.register(this.server, data);
   }

   /** Starts the web server and blocks until it stops. */
   public void run() throws Exception {
      this.server.start();
   }

   /** Loads the six record files, starting any that are missing. */
   private static EventData openEventData(EventFiles files) throws IOException {
      DataRecordFile<ConfigRecord> config = new DataRecordFile<>(files.config(), new ConfigRecord.Factory());
      DataRecordFile<ScoutRecord> scouts = new DataRecordFile<>(files.scouts(), new ScoutRecord.Factory());
      DataRecordFile<AdultRecord> adults = new DataRecordFile<>(files.adults(), new AdultRecord.Factory());
      DataRecordFile<RoomRecord> rooms = new DataRecordFile<>(files.rooms(), new RoomRecord.Factory());
      DataRecordFile<AdultRecord> adultHistory = new DataRecordFile<>(files.adultHistory(), new AdultRecord.Factory());
      if (files.adultHistoryIsNew()) {
         // DataRecordFile made it empty; the header row makes it a history
         // every version reads as holding nobody yet.
         adultHistory.store();
         System.out.println("\n   Started a new, empty adult history: " + files.adultHistory().getAbsolutePath() + "\n");
      }
      DataRecordFile<ScoutRecord> scoutsScheduled = new DataRecordFile<>(files.scoutsScheduled(), new ScoutRecord.Factory());
      addDefaultConfigIfMissing(config);

      return new EventData(files.dataRoot(), scouts, scoutsScheduled, adults, adultHistory, rooms, config);
   }

   /** The settings are a single row named DEFAULT; a new install has none yet. */
   private static void addDefaultConfigIfMissing(DataRecordFile<ConfigRecord> config) throws IOException {
      if (config.get("DEFAULT") == null) {
         ConfigRecord defaultConfig = config.addNew("DEFAULT", true);
         defaultConfig.setValue("Name", "DEFAULT");
         defaultConfig.setValue("ID", "DEFAULT");
         config.store();
      }
   }
}
