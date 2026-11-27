package shkc.core;

import java.io.IOException;
import java.time.LocalDate;

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

   private final WebServer server;
   private final EventMonitor monitor;

   /** The running program's monitor, for the line the shutdown hook writes. Null until the data is loaded. */
   private static volatile EventMonitor runningMonitor = null;

   static {
      System.setProperty("java.awt.headless", "false");
   }

   public static void main(String[] args) throws Exception {
      Commandline cmdline = parseCommandLine(args);

      if (cmdline.hasFlag("?:help")) {
         usage();
         System.exit(1);
      }

      Log.setVerbose(cmdline.hasFlag("v:verbose"));
      Log.setDebugToFile(cmdline.hasFlag("debug"));
      installCrashLogging();
      logStartup(args, cmdline);

      String today = LocalDate.now().toString();
      String dataDir = cmdline.getOption("d:dir", today);
      String preregFile = cmdline.getOption("p:prereg");
      String configFile = cmdline.getOption("c:config");
      String adultsFile = cmdline.getOption("a:adults");
      int port = cmdline.getIntOption("port", DEFAULT_PORT);
      String sugKey = cmdline.getOption("sugkey", null);
      String sugId = cmdline.getOption("sugid", null);
      // From here the key is masked wherever it appears in the log or on the console,
      // whatever the text around it looks like (a service's error message, say).
      Log.registerSecret(sugKey);
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
         // A failure before the log was attached (a mistyped -c, say) would otherwise be
         // on the console only, and the folder is usually fine: write it where the log goes.
         attachLogIfFolderExists(dataDir);
         Log.error("the program could not start: " + startupFailure.getMessage()
            + (hasCause(startupFailure, java.net.BindException.class)
               ? " (port " + port + " is in use: is another Eagle Boards still running, or use -port to pick another)" : ""), startupFailure);
         System.err.println("\n\n  ERROR: " + startupFailure.getMessage() + "\n\n");
         // SPEC.md D-9: end the process, so a script sees the failure and the
         // -w window listing URLs for a server that never started goes with it.
         System.exit(1);
      }
   }

   /** Starts the event log in {@code dataDir} if none is open yet and the folder is there. Never creates the folder. */
   private static void attachLogIfFolderExists(String dataDir) {
      try {
         java.io.File folder = new java.io.File(dataDir);
         if (Log.getLogFile() == null && folder.isDirectory()) {
            Log.attachFile(folder);
         }
      } catch (RuntimeException ignored) {
         // The failure being reported matters more than where it is written.
      }
   }

   private static boolean hasCause(Throwable failure, Class<? extends Throwable> kind) {
      for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
         if (kind.isInstance(cause)) {
            return true;
         }
      }
      return false;
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

   /**
    * Makes sure a thread that dies from an exception nobody caught, and the
    * program being stopped, each leave a line in the event log. A log that
    * simply ends, with no shutdown line, means the process was killed or the
    * power went.
    */
   private static void installCrashLogging() {
      Thread.setDefaultUncaughtExceptionHandler((thread, failure) ->
         Log.error("uncaught exception in thread " + thread.getName(), failure));
      long startedMillis = System.currentTimeMillis();
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
         // The last counts, taken on another thread and waited for only briefly: a request
         // stuck holding the data lock must not keep the program from stopping.
         EventMonitor monitor = runningMonitor;
         if (monitor != null) {
            Thread closing = new Thread(() -> monitor.logBoards("closing"), "closing-snapshot");
            closing.setDaemon(true);
            closing.start();
            try {
               closing.join(1000L);
            } catch (InterruptedException interrupted) {
               Thread.currentThread().interrupt();
            }
         }
         Log.info("=== shutting down after " + (System.currentTimeMillis() - startedMillis) / 1000L + " s ===");
      }, "shutdown"));
   }

   /**
    * The first lines of every run in the event log: which build, on what
    * machine, started how. These answer the questions asked first about any
    * report ("which version was that?", "was the clock right?").
    */
   private static void logStartup(String[] args, Commandline cmdline) {
      Log.info("=== Eagle Boards starting ===");
      Log.info("version " + versionText() + ", jar " + jarText());
      Log.info("java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + "), "
         + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch")
         + ", " + Runtime.getRuntime().availableProcessors() + " cpus, max heap "
         + Runtime.getRuntime().maxMemory() / (1024L * 1024L) + " MB");
      Log.info("clock " + java.time.ZonedDateTime.now() + ", working directory " + new java.io.File("").getAbsolutePath());
      Log.info("arguments: " + Log.redactArgs(args));
      if (cmdline.hasFlag("debug")) {
         Log.info("DEBUG detail is on: the log also holds what -verbose prints, which includes personal details");
      }
   }

   /** The version the jar was built as, or {@code unknown (not run from a built jar)}. */
   private static String versionText() {
      String version = EagleBoardScheduler.class.getPackage().getImplementationVersion();
      return version != null ? version : "unknown (not run from a built jar)";
   }

   /** The path, size and date of the jar being run, since an older jar left in target/ looks identical. */
   private static String jarText() {
      try {
         java.io.File jar = new java.io.File(EagleBoardScheduler.class.getProtectionDomain().getCodeSource().getLocation().toURI());
         if (jar.isFile()) {
            return jar.getAbsolutePath() + " (" + jar.length() + " bytes, built " + java.time.Instant.ofEpochMilli(jar.lastModified()) + ")";
         }
         return jar.getAbsolutePath() + " (a class folder, not a jar)";
      } catch (Exception unknown) {
         return "unknown";
      }
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
      // From here on the event log is a file in the data folder; what was
      // logged before this point (the startup lines) is written to it now.
      Log.attachFile(files.dataRoot());
      Log.info("event log: " + Log.getLogFile());
      if (Log.getLogFile() != null) {
         // The first question after a problem at an event is where the log is.
         System.out.println("\n   Event log: " + Log.getLogFile().getAbsolutePath() + "\n");
      }
      Log.info("data folder " + files.dataRoot().getAbsolutePath() + ", adult history " + files.adultHistory().getAbsolutePath()
         + (files.adultHistoryIsNew() ? " (new)" : "") + ", settings " + files.config().getAbsolutePath() + ", port " + port
         + ", bind " + (WebServer.getBindHost() == null ? "every interface" : WebServer.getBindHost()));

      this.server = new WebServer("WEBROOT");
      this.server.setPort(port);

      EventData data = openEventData(files);
      this.monitor = new EventMonitor(data);
      runningMonitor = this.monitor;
      this.monitor.logSnapshot("loaded");
      this.monitor.logBoards("loaded");
      logImplausibleSettings(data);
      new PreRegistrationLoader(data.getScoutsScheduled(), data.getAdultHistory()).load(preregFile, sugKey, sugId);
      // The counts again: "loaded" is before the import, and the first heartbeat is minutes away.
      this.monitor.logSnapshot("ready");
      EventRoutes.register(this.server, data);
   }

   /** Starts the web server and blocks until it stops. */
   public void run() throws Exception {
      this.monitor.start();
      new StallWatchdog(this.server).start();
      // "listening" is logged by the server once the port is bound.
      this.server.start();
   }

   /** One warning for each setting that cannot give the timers the operator probably meant. */
   private static void logImplausibleSettings(EventData data) {
      synchronized (data.getLock()) {
         ConfigRecord config = data.getConfig().get("DEFAULT");
         if (config != null) {
            for (String problem : config.implausibleSettings()) {
               Log.warn("settings: " + problem);
            }
         }
      }
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
