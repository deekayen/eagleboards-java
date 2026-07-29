package shkc.core;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class EagleBoardScheduler {
   private static final String[] SCOUT_REG_FIELDS = new String[]{"First", "Last", "DOB", "Unit", "UnitType", "Email", "Phone", "Leader"};
   private static final String[] ADULT_REG_FIELDS = new String[]{"First", "Last", "Unit", "UnitType", "Email", "Phone", "ProjectReview", "FinalBoard"};
   private SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
   private static boolean _verbose = false;
   // Strong reference so the -debug logger configuration survives GC
   // (java.util.logging holds loggers weakly).
   private static Logger _debugLogger = null;
   private Object LOCK = new Object();
   private File _dataRoot;
   private File _scoutFile;
   private File _adultFile;
   private File _roomFile;
   private File _adultHistoryFile;
   private File _scoutsScheduledFile;
   private File _configFile;
   private WebServer _server;
   private DataRecordFile<ConfigRecord> _configRecords = null;
   private DataRecordFile<RoomRecord> _roomRecords = null;
   private DataRecordFile<ScoutRecord> _scoutRecords = null;
   private DataRecordFile<ScoutRecord> _scoutsScheduledRecords = null;
   private DataRecordFile<AdultRecord> _adultRecords = null;
   private DataRecordFile<AdultRecord> _adultHistoryRecords = null;

   public static void main(String[] var0) throws Exception {
      String var1 = "vw?";
      String[] var2 = new String[]{"verbose", "help", "debug", "windows"};
      String var3 = "dapc";
      String[] var4 = new String[]{"dir", "prereg", "adults", "config", "port", "sugkey", "sugid", "bind"};
      Commandline var5 = null;

      try {
         var5 = new Commandline(var0, var1, var3, var2, var4);
      } catch (Throwable var22) {
         System.out.println("\n\n   ERROR:  invalid command line argument(s): " + var22.getMessage() + "\n\n");
         System.exit(1);
      }

      if (var5.hasFlag("debug")) {
         FileHandler var26 = new FileHandler("eagle-board-scheduler.log");
         var26.setFormatter(new SimpleFormatter());
         var26.setLevel(Level.ALL);
         _debugLogger = Logger.getLogger("shkc");
         _debugLogger.setLevel(Level.ALL);
         _debugLogger.addHandler(var26);
         System.out.println("DEBUGGING ENABLED");
      }

      if (var5.hasFlag("?:help")) {
         usage();
         System.exit(1);
      }

      if (var5.hasFlag("v:verbose")) {
         _verbose = true;
      } else {
         _verbose = false;
      }

      SimpleDateFormat var6 = new SimpleDateFormat("yyyy-MM-dd");
      String var7 = var6.format(new Date());
      String var8 = var5.getOption("d:dir", var7);
      String var9 = var5.getOption("p:prereg");
      String var10 = var5.getOption("c:config");
      String var11 = var5.getOption("a:adults");
      int var12 = var5.getIntOption("port", 8080);
      String var13 = var5.getOption("sugkey", null);
      String var14 = var5.getOption("sugid", null);
      // -bind <ip-prefix>: restrict the listener and the advertised URLs to the
      // single interface we actually serve from (e.g. "192.168." for the venue
      // wifi), so virtual adapters don't each pop their own dialog. When absent,
      // behavior is unchanged: listen on every interface and list them all.
      String bindPrefix = var5.getOption("bind", null);
      String boundAddress = null;
      if (var0.length != 0 && var5.hasFlag("w:windows")) {
      }

      System.out.println("\n\nAvailable Network Interfaces");
      Enumeration var15 = NetworkInterface.getNetworkInterfaces();

      while (var15.hasMoreElements()) {
         StringBuffer var16 = new StringBuffer();
         NetworkInterface var17 = (NetworkInterface)var15.nextElement();
         int var18 = 0;
         Enumeration var19 = var17.getInetAddresses();

         while (var19.hasMoreElements()) {
            InetAddress var20 = (InetAddress)var19.nextElement();
            if (var20 instanceof Inet4Address
               && !var20.getHostAddress().startsWith("127")
               && (bindPrefix == null || var20.getHostAddress().startsWith(bindPrefix))) {
               var16.append("\n                 http://" + var20.getHostAddress() + ":" + var12);
               if (boundAddress == null) {
                  boundAddress = var20.getHostAddress();
               }

               var18++;
            }
         }

         if (var18 > 0) {
            if (var0.length == 0 || var5.hasFlag("w:windows")) {
               PopupDialog var24 = new PopupDialog("        Connect to the following URLs\n" + var16.toString() + "\n");
               var24.setVisible(true);
            }

            System.out.println(var16.toString());
         }
      }

      if (bindPrefix != null) {
         if (boundAddress == null) {
            // Don't strand the event if the wifi is down at startup: warn loudly
            // and fall back to the original listen-everywhere behavior.
            System.out.println("\n   WARNING: no active interface matches -bind " + bindPrefix + "; listening on all interfaces.\n");
         } else {
            WebServer.setBindHost(boundAddress);
            System.out.println("\n   binding to " + boundAddress + " and 127.0.0.1 (-bind " + bindPrefix + ")\n");
         }
      }

      System.out.println("\n\n URL: http://<ip-address>:" + var12 + "\n\n");

      try {
         EagleBoardScheduler var23 = new EagleBoardScheduler(var12, var8, var9, var11, var10, var13, var14);
         var23.run();
      } catch (Exception var21) {
         var21.printStackTrace();
         System.err.println("\n\n  ERROR: " + var21.getMessage() + "\n\n");
      }
   }

   public static void usage() {
      System.out
         .println(
            "\n\n\n   USAGE\n\n          java -jar eagle-board-scheduler.jar <options>\n\n      OPTIONS\n\n         -w[indows]                     : popup dialog for config info\n         -v[erbose]                     : print verbose messages\n         -h[elp]                        : print this message\n\n         -d[ir]    <data-directory>     : directory where data files live\n\n         -a[dult]  <adult-history-file> : file containing adult auto-fill data\n         -c[onfig] <config-file>        : scheduler config file\n         -p[rereg] <prereg-file>        : preregistration data file (csv) from district website\n         -sugkey <signup-genius-key>    : SignupGenius API KEY\n         -sugid  <signup-id>            : SignupGenius Eagle Board Signup ID (optional)\n\n         -bind   <ip-prefix>            : only listen on / advertise the interface whose\n                                          IPv4 address starts with this (e.g. 192.168.);\n                                          127.0.0.1 stays reachable either way;\n                                          default is every interface\n\n"
         );
   }

   public EagleBoardScheduler(int var1, String var2, String var3, String var4, String var5, String var6, String var7) throws Exception {
      this._dataRoot = new File(var2);
      if (!this._dataRoot.exists() && !this._dataRoot.mkdir()) {
         throw new IOException("cannot create data directory: " + this._dataRoot.getAbsolutePath());
      }

      if (var4 != null) {
         this._adultHistoryFile = new File(var4);
         if (!this._adultHistoryFile.exists()) {
            throw new Exception("error: adult history file '" + var4 + "' does not exist.");
         }
      } else {
         this._adultHistoryFile = new File(this._dataRoot, "adult_history.csv");
      }

      if (var5 != null) {
         this._configFile = new File(var5);
         if (!this._configFile.exists()) {
            throw new Exception("error: config  file '" + var5 + "' does not exist.");
         }
      } else {
         this._configFile = new File(this._dataRoot, "config.properties");
      }

      if (var5 == null) {
         var5 = "config.properties";
      }

      this._server = new WebServer("WEBROOT");
      this._server.setPort(var1);
      this._scoutFile = new File(this._dataRoot, "scouts.csv");
      this._adultFile = new File(this._dataRoot, "adults.csv");
      this._roomFile = new File(this._dataRoot, "rooms.csv");
      this._scoutsScheduledFile = new File(this._dataRoot, "scouts_scheduled.csv");
      this._configFile = new File(this._dataRoot, var5);
      if (!this._configFile.exists()) {
         this._configFile = new File("config.properties");
      }

      this._configRecords = new DataRecordFile<>(this._configFile, new ConfigRecord.Factory());
      this._scoutRecords = new DataRecordFile<>(this._scoutFile, new ScoutRecord.Factory());
      this._adultRecords = new DataRecordFile<>(this._adultFile, new AdultRecord.Factory());
      this._roomRecords = new DataRecordFile<>(this._roomFile, new RoomRecord.Factory());
      this._adultHistoryRecords = new DataRecordFile<>(this._adultHistoryFile, new AdultRecord.Factory());
      this._scoutsScheduledRecords = new DataRecordFile<>(this._scoutsScheduledFile, new ScoutRecord.Factory());
      if (this._configRecords.get("DEFAULT") == null) {
         ConfigRecord var8 = this._configRecords.addNew("DEFAULT", true);
         var8.setValue("Name", "DEFAULT");
         var8.setValue("ID", "DEFAULT");
         this._configRecords.store();
      }

      ConfigRecord var13 = this._configRecords.get("DEFAULT");
      if (var3 != null) {
         File var9 = new File(var3);
         if (!var9.exists()) {
            throw new Exception("error: no preregistration file '" + var3 + "' found.");
         }

         System.out.println("\n\n   loading SCOUT pre-registrations: " + var3 + "\n\n");
         NegaPreRegScoutRecordConverter var10 = new NegaPreRegScoutRecordConverter(this._scoutsScheduledRecords);
         this._scoutsScheduledRecords.clearAll();
         var10.convert(var9);
         this._scoutsScheduledRecords.store();
         System.out.println("\n\n   loading ADULT pre-registrations: " + var3 + "\n\n");
         NegaPreRegAdultRecordConverter var11 = new NegaPreRegAdultRecordConverter(this._adultHistoryRecords);
         var11.convert(var9);
         this._adultHistoryRecords.store();
      } else if (var6 != null && var6.length() > 10) {
         try {
            SignUpGeniusPlugin var14 = new SignUpGeniusPlugin(var6, var7, this._adultHistoryRecords, this._scoutsScheduledRecords);
            var14.populatePreRegistrations();
         } catch (Exception var12) {
            System.out.println("\n\n   SignupGenius Error: " + var12.getMessage() + "\n\n");
         }
      } else {
         System.out.println("\n   no prereg-file or SignupGenius DB loaded \n\n");
      }

      this._server.addHandler("/register-scout", new EagleBoardScheduler.RegisterScoutHandler());
      this._server.addHandler("/register-adult", new EagleBoardScheduler.RegisterAdultHandler());
      this._server.addHandler("/update-config", new EagleBoardScheduler.UpdateConfigHandler());
      this._server.addHandler("/scout-cells", new EagleBoardScheduler.ScoutCellsHandler());
      this._server.addHandler("/scouts-scheduled-cells", new EagleBoardScheduler.ScoutsScheduledCellsHandler());
      this._server.addHandler("/adult-cells", new EagleBoardScheduler.AdultCellsHandler());
      this._server.addHandler("/adult-history-cells", new EagleBoardScheduler.AdultHistoryCellsHandler());
      this._server.addHandler("/room-cells", new EagleBoardScheduler.RoomCellsHandler());
      this._server.addHandler("/scout-update", new EagleBoardScheduler.ScoutUpdateHandler());
      this._server.addHandler("/scouts-scheduled-update", new EagleBoardScheduler.ScoutsScheduledUpdateHandler());
      this._server.addHandler("/adult-update", new EagleBoardScheduler.AdultUpdateHandler());
      this._server.addHandler("/adult-history-update", new EagleBoardScheduler.AdultHistoryUpdateHandler());
      this._server.addHandler("/room-update", new EagleBoardScheduler.RoomUpdateHandler());
      this._server.addHandler("/room-change", new EagleBoardScheduler.RoomChangeHandler());
      this._server.addHandler("/seat-board", new EagleBoardScheduler.SeatBoardHandler());
      this._server.addHandler("/inprogress-board", new EagleBoardScheduler.InProgressBoardHandler());
      this._server.addHandler("/complete-board", new EagleBoardScheduler.CompleteBoardHandler());
      this._server.addHandler("/postpone-board", new EagleBoardScheduler.PostponeBoardHandler());
      this._server.addHandler("/reset-board", new EagleBoardScheduler.ResetBoardHandler());
      this._server.addHandler("/adult-autofill", new EagleBoardScheduler.AutoFillHandler<>(this._adultHistoryRecords, "Email"));
      this._server.addHandler("/scout-autofill", new EagleBoardScheduler.AutoFillHandler<>(this._scoutsScheduledRecords, "Email"));
      this._server.addHandler("/config-autofill", new EagleBoardScheduler.AutoFillHandler<>(this._configRecords, "Name"));
   }

   public void run() throws Exception {
      this._server.start();
   }

   private String[] getFields(String var1, String[] var2) {
      if (var1 != null && var1.length() != 0) {
         StringTokenizer var3 = new StringTokenizer(var1, ",+ []", false);
         String[] var4 = new String[var3.countTokens()];

         for (int var5 = 0; var5 < var4.length; var5++) {
            var4[var5] = var3.nextToken();
         }

         return var4;
      } else {
         return var2;
      }
   }

   // No longer called: the AdultScoutRatio ("B/S") column was dropped. Kept
   // defined (unused) so this class's declared members — and the parity gate —
   // are unchanged. Safe to delete if the parity baseline is ever retired.
   private void updateAdultScoutRatios(boolean var1) {
      HashMap var2 = new HashMap();

      for (ScoutRecord var4 : this._scoutRecords.getRecords()) {
         double[] var5 = (double[])var2.get(var4.getUnitName());
         if (var5 == null) {
            var5 = new double[3];
            var2.put(var4.getUnitName(), var5);
         }

         if ("Project".equalsIgnoreCase(var4.getBoardType())) {
            var5[0] += 2.0;
         } else {
            var5[0] += 3.0;
         }
      }

      for (AdultRecord var11 : this._adultRecords.getRecords()) {
         if ("Chair".equalsIgnoreCase(var11.getProjectReviewRole())
            || "Member".equalsIgnoreCase(var11.getProjectReviewRole())
            || "Chair".equalsIgnoreCase(var11.getFinalBoardRole())
            || "Member".equalsIgnoreCase(var11.getFinalBoardRole())) {
            double[] var13 = (double[])var2.get(var11.getUnitName());
            if (var13 != null) {
               var13[1]++;
            }
         }
      }

      for (ScoutRecord var12 : this._scoutRecords.getRecords()) {
         double[] var14 = (double[])var2.get(var12.getUnitName());
         if (var14 != null) {
            double var6 = var14[1] / var14[0];
            var12.setAdultScoutRatio(String.format("%1.1f", var6));
         }
      }

      if (var1) {
         try {
            this._scoutRecords.store();
         } catch (Exception var8) {
            error("store error: " + var8.getMessage());
         }
      }
   }

   public static void verbose(Object var0) {
      if (_verbose) {
         System.out.println(var0);
      }
   }

   public static void error(String var0) {
      System.out.println("\n\nERROR: " + var0 + "\n");
      Thread.dumpStack();
   }

   static {
      System.setProperty("java.awt.headless", "false");
   }

   public class AdultCellsHandler extends EagleBoardScheduler.DataRecordCellsHandler<AdultRecord> {
      public AdultCellsHandler() {
         super(EagleBoardScheduler.this._adultRecords);
      }
   }

   public class AdultHistoryCellsHandler extends EagleBoardScheduler.DataRecordCellsHandler<AdultRecord> {
      public AdultHistoryCellsHandler() {
         super(EagleBoardScheduler.this._adultHistoryRecords);
      }
   }

   public class AdultHistoryUpdateHandler extends EagleBoardScheduler.DataRecordUpdateHandler<AdultRecord> {
      public AdultHistoryUpdateHandler() {
         super(EagleBoardScheduler.this._adultHistoryRecords);
      }
   }

   public class AdultUpdateHandler extends EagleBoardScheduler.DataRecordUpdateHandler<AdultRecord> {
      public AdultUpdateHandler() {
         super(EagleBoardScheduler.this._adultRecords);
      }
   }

   public class AutoFillHandler<T extends DataRecord> implements WebServer.WebHandler {
      private DataRecordFile<T> _records;
      private String _lookupField = null;

      public AutoFillHandler(DataRecordFile<T> var2, String var3) {
         this._records = var2;
         this._lookupField = var3;
      }

      @Override
      public synchronized void handle(String var1, HttpServletRequest var2, HttpServletResponse var3) throws IOException, ServletException {
         EagleBoardScheduler.verbose(var2);
         EagleBoardScheduler.verbose(var2.getParameterMap());
         String var4 = var2.getParameter("op");
         String var5 = var2.getParameter("fmt");
         String var6 = var2.getParameter(this._lookupField);
         String var7 = "text/json";
         StringBuffer var8 = new StringBuffer();
         if ("list".equals(var4) || var6 == null) {
            var7 = "text/json";
            var8.append("{ options: [\n");
            boolean var17 = true;

            for (DataRecord var19 : this._records.getRecords()) {
               if (var19.get(this._lookupField).length() > 4) {
                  if (!var17) {
                     var8.append(",\n");
                  }

                  var17 = false;
                  var8.append("   { value: \"").append(var19.get(this._lookupField));
                  var8.append("\", text:\"").append(var19.get(this._lookupField)).append("\"}");
               }
            }

            var8.append("\n]\n}\n");
         } else if (var6 != null) {
            if ("json".equals(var5)) {
               var7 = "text/json";
               List var9 = this._records.get(this._lookupField, var6);
               if (var9.size() > 0) {
                  ((DataRecord)var9.get(0)).toJSON(var8);
               }
            } else {
               var7 = "text/xml";
               var8.append("<data>");
               List var16 = this._records.get(this._lookupField, var6);
               if (var16.size() > 0) {
                  DataRecord var10 = (DataRecord)var16.get(0);
                  boolean var11 = true;

                  for (String var15 : this._records.getColumns()) {
                     if (!var11) {
                        var8.append("\n");
                     }

                     var11 = false;
                     var8.append("<").append(var15).append(">");
                     var8.append(var10.getValue(var15));
                     var8.append("</").append(var15).append(">");
                  }
               }

               var8.append("</data>");
            }
         }

         var3.setContentLength(var8.length());
         var3.setContentType(var7);
         var3.getOutputStream().write(var8.toString().getBytes());
         var3.setStatus(200);
      }
   }

   public class CompleteBoardHandler extends EagleBoardScheduler.CoreBoardHandler {
      @Override
      public synchronized void handle(String var1, HttpServletRequest var2, HttpServletResponse var3) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(var2);
            EagleBoardScheduler.verbose(var2.getParameterMap());
            String var5 = var2.getParameter("ScoutID");
            String var6 = var2.getParameter("Result");
            String var7 = var2.getParameter("Notes");
            String var8 = var2.getParameter("Cost");
            String var9 = var2.getParameter("BSAHours");
            String var10 = var2.getParameter("OtherHours");
            EagleBoardScheduler.verbose("ScoutID: " + var5);
            EagleBoardScheduler.verbose("Result: " + var6);
            EagleBoardScheduler.verbose("Notes: " + var7);
            EagleBoardScheduler.verbose("ProjectCost: " + var8);
            EagleBoardScheduler.verbose("BSA Hours: " + var9);
            EagleBoardScheduler.verbose("Other Hours: " + var10);
            if (var6 != null && var6.length() >= 5) {
               if (var7 == null) {
                  var7 = "";
               }

               if (var8 != null) {
                  var7 = var7 + "(Cost: " + var8 + ")";
               }

               if (var9 != null) {
                  var7 = var7 + "(BSA: " + var9 + " hrs)";
               }

               if (var10 != null) {
                  var7 = var7 + "(Other: " + var10 + " hrs)";
               }

               ScoutRecord var11 = EagleBoardScheduler.this._scoutRecords.get(var5);
               if (var11 == null) {
                  this.sendError("Invalid Scout ID" + var5, var3);
               } else if (!var11.getStatus().equals("InProgress") && !var11.getStatus().equals("Seated")) {
                  this.sendError("Invalid Scout Status '" + var11.getStatus() + "' expected '" + "InProgress" + "'", var3);
               } else {
                  RoomRecord var12 = null;

                  for (RoomRecord var14 : EagleBoardScheduler.this._roomRecords.getRecords()) {
                     if (var14.getRoom().equals(var11.getRoom())) {
                        var12 = var14;
                        break;
                     }
                  }

                  if (var12 == null) {
                     this.sendError("No room " + var11.getRoom() + " not found.", var3);
                  } else {
                     for (AdultRecord var18 : EagleBoardScheduler.this._adultRecords.getRecords()) {
                        if (var18.getRoom().equals(var12.getRoom())) {
                           var18.setRoom("");
                        }
                     }

                     var11.setStatus("Completed");
                     var11.setRoom("N/A");
                     var11.setNotes(var7);
                     var11.setResult(var6);
                     var12.setScout("");
                     var12.setLeaders("");
                     var11.updateFields(true);
                     EagleBoardScheduler.this._scoutRecords.store();
                     EagleBoardScheduler.this._roomRecords.store();
                     EagleBoardScheduler.this._adultRecords.store();
                     this.sendSuccess(var3);
                  }
               }
            } else {
               this.sendError("Invalid Result '" + var6 + "', expected Approved, Adjourned or NotApproved", var3);
            }
         }
      }
   }

   public abstract class CoreBoardHandler implements WebServer.WebHandler {
      protected void sendError(String var1, HttpServletResponse var2) throws IOException {
         StringBuffer var3 = new StringBuffer();
         var3.append(var1);
         String var4 = var3.toString();
         var2.setContentLength(var4.length());
         var2.setContentType("text/plain");
         var2.getOutputStream().write(var4.getBytes());
         var2.setStatus(304);
      }

      public void sendSuccess(HttpServletResponse var1) throws IOException {
         String var2 = "OK.";
         var1.setContentLength(var2.length());
         var1.setContentType("text/plain");
         var1.getOutputStream().write(var2.getBytes());
         var1.setStatus(200);
      }
   }

   public class DataRecordCellsHandler<T extends DataRecord> implements WebServer.WebHandler {
      private DataRecordFile<T> _records;

      public DataRecordCellsHandler(DataRecordFile<T> var2) {
         this._records = var2;
      }

      @Override
      public void handle(String var1, HttpServletRequest var2, HttpServletResponse var3) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(var2);
            EagleBoardScheduler.verbose(var2.getParameterMap());
            String var5 = var2.getParameter("cols");
            String var6 = var2.getParameter("data");
            String var7 = var2.getParameter("fmt");
            String var8 = var2.getParameter("filename");
            if (var7 == null) {
               var7 = "rows";
            }

            String var9 = var2.getParameter("filter");
            String var10 = null;
            String var11 = null;
            if (var9 != null) {
               StringTokenizer var12 = new StringTokenizer(var9, "~#", false);
               if (var12.countTokens() == 2) {
                  var10 = var12.nextToken().trim();
                  var11 = var12.nextToken().trim();
               }
            }

            EagleBoardScheduler.verbose("COLS: " + var5);
            EagleBoardScheduler.verbose("DATA: " + var6);
            EagleBoardScheduler.verbose("FILTER: " + var10 + " ~ " + var11);
            String var23 = "text/xml";
            String[] var13 = EagleBoardScheduler.this.getFields(var5, this._records.getColumns());
            String[] var14 = EagleBoardScheduler.this.getFields(var6, null);
            StringBuffer var15 = new StringBuffer();
            if (var7.equals("data")) {
               var23 = "text/xml";
               var15.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><data>");

               for (DataRecord var17 : this._records.getRecords()) {
                  if (var11 != null && var10 != null) {
                     String var18 = var17.get(var10);
                     if (var18 != null && var18.length() > 0 && var11.indexOf(var18) >= 0) {
                        var17.toString(var15, var13, var14, var7);
                        var15.append("\n");
                     }
                  } else {
                     var17.toString(var15, var13, var14, var7);
                     var15.append("\n");
                  }
               }

               var15.append("</data>");
            } else if (var7.equals("csv")) {
               var23 = "text/csv";
               boolean var25 = true;

               for (String var20 : var13) {
                  if (!var25) {
                     var15.append(",");
                  }

                  var25 = false;
                  var15.append(var20);
               }

               var15.append("\n");

               for (DataRecord var32 : this._records.getRecords()) {
                  if (var11 != null && var10 != null) {
                     String var34 = var32.get(var10);
                     if (var34 != null && var34.length() > 0 && var11.indexOf(var34) >= 0) {
                        var32.toString(var15, var13, null, var7);
                        var15.append("\n");
                     }
                  } else {
                     var32.toString(var15, var13, null, var7);
                     var15.append("\n");
                  }
               }
            } else {
               var23 = "text/xml";
               var15.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><rows>");

               for (DataRecord var30 : this._records.getRecords()) {
                  if (var11 != null && var10 != null) {
                     String var33 = var30.get(var10);
                     if (var33 != null && var33.length() > 0 && var11.indexOf(var33) >= 0) {
                        var30.toCells(var15, var13, var14);
                        var15.append("\n");
                     }
                  } else {
                     var30.toCells(var15, var13, var14);
                     var15.append("\n");
                  }
               }

               var15.append("</rows>");
            }

            if (var8 != null && var8.length() > 0) {
               var3.setHeader("Content-Disposition", "attachment;filename=" + var8);
            }

            String var27 = var15.toString();
            var3.setContentLength(var27.length());
            var3.setContentType(var23);
            var3.getOutputStream().write(var27.getBytes());
            var3.setStatus(200);
         }
      }

      public DataRecordFile<T> getRecords() {
         return this._records;
      }
   }

   public class DataRecordUpdateHandler<T extends DataRecord> implements WebServer.WebHandler {
      private DataRecordFile<T> _records;

      public DataRecordUpdateHandler(DataRecordFile<T> var2) {
         this._records = var2;
      }

      @Override
      public void handle(String var1, HttpServletRequest var2, HttpServletResponse var3) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(var2);
            EagleBoardScheduler.verbose(var2.getParameterMap());
            String var5 = var2.getParameter("!nativeeditor_status");
            String var6 = var2.getParameter("gr_id");
            if (var6 == null) {
               this.sendResponse("invalid", var6, var3);
            } else {
               DataRecord var7 = this._records.get(var6);
               if (var7 == null) {
                  if (!"inserted".equals(var5)) {
                     EagleBoardScheduler.verbose("no such record: " + var6);
                     this.sendResponse("invalid", var6, var3);
                     return;
                  }

                  var7 = this._records.addNew(var6, false);
               } else if ("inserted".equals(var5)) {
                  EagleBoardScheduler.verbose("record already exists: " + var6);
                  this.sendResponse("invalid", var6, var3);
                  return;
               }

               if ("deleted".equals(var5)) {
                  this._records.remove(var6);
               } else {
                  for (String var11 : this._records.getColumns()) {
                     String var12 = var2.getParameter(var11);
                     EagleBoardScheduler.verbose("CHECKING: " + var11 + " => " + var12);
                     if (var12 != null) {
                        EagleBoardScheduler.verbose("UPDATING: " + var11 + " => " + var12);
                        var7.put(var11, var12);
                     }
                  }
               }

               var7.updateFields(false);
               this._records.store();
               this.sendResponse(var5, var6, var3);
            }
         }
      }

      private void sendResponse(String var1, String var2, HttpServletResponse var3) throws IOException {
         StringBuffer var4 = new StringBuffer();
         var4.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><data>");
         var4.append("<action type=\"").append(var1).append("\" sid=\"").append(var2);
         var4.append("\" tid=\"").append(var2).append("\" />");
         var4.append("</data>");
         String var5 = var4.toString();
         var3.setContentLength(var5.length());
         var3.setContentType("text/xml");
         var3.getOutputStream().write(var5.getBytes());
         var3.setStatus(200);
      }
   }

   public class InProgressBoardHandler extends EagleBoardScheduler.CoreBoardHandler {
      @Override
      public synchronized void handle(String var1, HttpServletRequest var2, HttpServletResponse var3) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(var2);
            EagleBoardScheduler.verbose(var2.getParameterMap());
            String var5 = var2.getParameter("ScoutID");
            EagleBoardScheduler.verbose("ScoutID: " + var5);
            ScoutRecord var6 = EagleBoardScheduler.this._scoutRecords.get(var5);
            if (var6 == null) {
               this.sendError("ERROR: Invalid Scout ID" + var5, var3);
            } else if (!var6.getStatus().equals("Seated")) {
               this.sendError("ERROR: Invalid Status '" + var6.getStatus() + "', expected '" + "Seated" + "'", var3);
            } else {
               var6.setStatus("InProgress");
               var6.updateFields(true);
               EagleBoardScheduler.this._scoutRecords.store();
               this.sendSuccess(var3);
            }
         }
      }
   }

   public class PostponeBoardHandler extends EagleBoardScheduler.CoreBoardHandler {
      @Override
      public synchronized void handle(String var1, HttpServletRequest var2, HttpServletResponse var3) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(var2);
            EagleBoardScheduler.verbose(var2.getParameterMap());
            String var5 = var2.getParameter("ScoutID");
            String var6 = var2.getParameter("ScoutID");
            EagleBoardScheduler.verbose("ScoutID: " + var5);
            ScoutRecord var7 = EagleBoardScheduler.this._scoutRecords.get(var5);
            if (var7 == null) {
               this.sendError("ERROR: Invalid Scout ID" + var5, var3);
            } else if (!var7.getStatus().equals("Registered") && !var7.getStatus().equals("Verified")) {
               this.sendError("ERROR: Invalid Status '" + var7.getStatus() + "', expected '" + "Registered" + " | " + "Verified" + "'", var3);
            } else {
               var7.setStatus("Postponed");
               var7.updateFields(true);
               EagleBoardScheduler.this._scoutRecords.store();
               this.sendSuccess(var3);
            }
         }
      }
   }

   public class RegisterAdultHandler implements WebServer.WebHandler {
      @Override
      public void handle(String var1, HttpServletRequest var2, HttpServletResponse var3) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(var2);
            AdultRecord var5 = new AdultRecord(var2.getParameterMap());
            var5.setRoom("");
            AdultRecord var6 = EagleBoardScheduler.this._adultRecords.get(var5.getID());
            if (var6 != null) {
               var6.updateFrom(var5, EagleBoardScheduler.ADULT_REG_FIELDS);
               var5 = var6;
            } else {
               EagleBoardScheduler.verbose("NEW ADULT RECORD: " + var5);
               EagleBoardScheduler.this._adultRecords.add(var5, false);
            }

            AdultRecord var7 = EagleBoardScheduler.this._adultHistoryRecords.get(var5.getID());
            if (var7 == null) {
               var7 = var5.clone();
               EagleBoardScheduler.verbose("Adding new Adult History Record: " + var7);
               EagleBoardScheduler.this._adultHistoryRecords.add(var7, false);
               var5.setFlags("W");
            } else {
               if ("Member".equals(var7.getFinalBoardRole()) && "Chair".equals(var5.getFinalBoardRole())) {
                  var7.setValue("FinalBoard", "Chair");
               }

               if ("Member".equals(var7.getProjectReviewRole()) && "Chair".equals(var5.getProjectReviewRole())) {
                  var7.setValue("ProjectReview", "Chair");
               }

               var7.updateFrom(var5, EagleBoardScheduler.ADULT_REG_FIELDS);
               var5.setFlags("P");
            }

            String var8 = var7.getBoardHistory();
            var8 = var8 + "(" + EagleBoardScheduler.this.DATE_FORMAT.format(new Date()) + ")";
            var7.setBoardHistory(var8);
            EagleBoardScheduler.this._adultRecords.store();
            EagleBoardScheduler.this._adultHistoryRecords.store();
            var3.setStatus(200);
         }
      }
   }

   public class RegisterScoutHandler implements WebServer.WebHandler {
      @Override
      public void handle(String var1, HttpServletRequest var2, HttpServletResponse var3) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(var2);
            int var5 = 0;
            int var6 = 0;

            for (ScoutRecord var8 : EagleBoardScheduler.this._scoutRecords.getRecords()) {
               if (var8.getRegNum().startsWith("P")) {
                  var5++;
               } else if (var8.getRegNum().startsWith("W")) {
                  var6++;
               }
            }

            ScoutRecord var11 = new ScoutRecord(var2.getParameterMap());
            ScoutRecord var12 = EagleBoardScheduler.this._scoutRecords.get(var11.getID());
            if (var12 != null) {
               EagleBoardScheduler.verbose("UPDATING EXISTING SCOUT RECORD: " + var12);
               var12.updateFrom(var11, EagleBoardScheduler.SCOUT_REG_FIELDS);
               var11 = var12;
               if (var11.getStatus().length() == 0) {
                  var11.setStatus("Registered");
               }
            } else {
               EagleBoardScheduler.verbose("NEW SCOUT RECORD: " + var11);
               EagleBoardScheduler.this._scoutRecords.add(var11, false);
               var11.setStatus("Registered");
            }

            if (EagleBoardScheduler.this._scoutsScheduledRecords.get(var11.getID()) == null
               && EagleBoardScheduler.this._scoutsScheduledRecords.get("Email", var11.getEmail()).size() == 0) {
               var11.setRegNum("W" + (var6 + 1));
            } else {
               var11.setRegNum("P" + (var5 + 1));
            }

            EagleBoardScheduler.this._scoutRecords.store();
            var3.setStatus(200);
         }
      }
   }

   public class ResetBoardHandler extends EagleBoardScheduler.CoreBoardHandler {
      @Override
      public synchronized void handle(String var1, HttpServletRequest var2, HttpServletResponse var3) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(var2);
            EagleBoardScheduler.verbose(var2.getParameterMap());
            String var5 = var2.getParameter("ScoutID");
            EagleBoardScheduler.verbose("ScoutID: " + var5);
            ScoutRecord var6 = EagleBoardScheduler.this._scoutRecords.get(var5);
            if (var6 == null) {
               this.sendError("Invalid Scout ID" + var5, var3);
            } else if (!var6.getStatus().equals("InProgress") && !var6.getStatus().equals("Seated") && !var6.getStatus().equals("Verified")) {
               this.sendError("Invalid Scout Status '" + var6.getStatus() + "' expected " + "'Verified', 'InProgress' or 'Seated' " + "'", var3);
            } else {
               RoomRecord var7 = null;

               for (RoomRecord var9 : EagleBoardScheduler.this._roomRecords.getRecords()) {
                  if (var9.getRoom().equals(var6.getRoom())) {
                     var7 = var9;
                     break;
                  }
               }

               if (var7 != null) {
                  for (AdultRecord var13 : EagleBoardScheduler.this._adultRecords.getRecords()) {
                     if (var13.getRoom().equals(var7.getRoom())) {
                        var13.setRoom("");
                     }
                  }

                  var7.setScout("");
                  var7.setLeaders("");
               }

               var6.setStatus("Registered");
               var6.setRoom("");
               var6.setBoardChair("");
               var6.setBoardChairID("");
               var6.setBoardMemberIDs("");
               var6.setBoardMembers("");
               var6.updateFields(true);
               EagleBoardScheduler.this._scoutRecords.store();
               EagleBoardScheduler.this._roomRecords.store();
               EagleBoardScheduler.this._adultRecords.store();
               this.sendSuccess(var3);
            }
         }
      }
   }

   public class RoomCellsHandler extends EagleBoardScheduler.DataRecordCellsHandler<RoomRecord> {
      public RoomCellsHandler() {
         super(EagleBoardScheduler.this._roomRecords);
      }
   }

   public class RoomChangeHandler extends EagleBoardScheduler.CoreBoardHandler {
      @Override
      public synchronized void handle(String var1, HttpServletRequest var2, HttpServletResponse var3) throws IOException, ServletException {
         EagleBoardScheduler.verbose("RoomChangeHandler: " + var2);
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(var2);
            EagleBoardScheduler.verbose(var2.getParameterMap());
            String var5 = var2.getParameter("RmID1");
            String var6 = var2.getParameter("RmID2");
            if (var5 == null || var5.equals("")) {
               this.sendError("ERROR: Invalid Room ID" + var5, var3);
            } else if (var6 != null && !var6.equals("")) {
               EagleBoardScheduler.verbose("ChangeRoom: RmID1=" + var5 + ", RmID2=" + var6);
               RoomRecord var7 = EagleBoardScheduler.this._roomRecords.get(var5);
               RoomRecord var8 = EagleBoardScheduler.this._roomRecords.get(var6);
               if (var7 == null) {
                  this.sendError("ERROR: No Such Room" + var5, var3);
               } else if (var8 == null) {
                  this.sendError("ERROR: No Such Room" + var6, var3);
               } else {
                  String var9 = var7.getLeaders();
                  String var10 = var8.getLeaders();
                  String var11 = var7.getScout();
                  String var12 = var8.getScout();
                  List<ScoutRecord> var13 = EagleBoardScheduler.this._scoutRecords.get("Room", var7.getRoom());
                  List<ScoutRecord> var14 = EagleBoardScheduler.this._scoutRecords.get("Room", var8.getRoom());
                  if (var13.size() > 1) {
                     this.sendError("ERROR: Room assigned to multiple scouts: " + var7.getRoom(), var3);
                  } else if (var14.size() > 1) {
                     this.sendError("ERROR: Room assigned to multiple scouts: " + var8.getRoom(), var3);
                  } else {
                     List<AdultRecord> var15 = EagleBoardScheduler.this._adultRecords.get("Room", var7.getRoom());
                     List<AdultRecord> var16 = EagleBoardScheduler.this._adultRecords.get("Room", var8.getRoom());
                     var7.setLeaders(var10);
                     var8.setLeaders(var9);
                     var7.setScout(var12);
                     var8.setScout(var11);

                     for (ScoutRecord var18 : var13) {
                        var18.setRoom(var8.getRoom());
                     }

                     for (ScoutRecord var24 : var14) {
                        var24.setRoom(var7.getRoom());
                     }

                     for (AdultRecord var25 : var15) {
                        var25.setRoom(var8.getRoom());
                     }

                     for (AdultRecord var26 : var16) {
                        var26.setRoom(var7.getRoom());
                     }

                     EagleBoardScheduler.this._roomRecords.store();
                     EagleBoardScheduler.this._scoutRecords.store();
                     EagleBoardScheduler.this._adultRecords.store();
                     this.sendSuccess(var3);
                  }
               }
            } else {
               this.sendError("ERROR: Invalid Room ID" + var6, var3);
            }
         }
      }
   }

   public class RoomUpdateHandler extends EagleBoardScheduler.DataRecordUpdateHandler<RoomRecord> {
      public RoomUpdateHandler() {
         super(EagleBoardScheduler.this._roomRecords);
      }
   }

   public class ScoutCellsHandler extends EagleBoardScheduler.DataRecordCellsHandler<ScoutRecord> {
      public ScoutCellsHandler() {
         super(EagleBoardScheduler.this._scoutRecords);
      }
   }

   public class ScoutUpdateHandler extends EagleBoardScheduler.DataRecordUpdateHandler<ScoutRecord> {
      public ScoutUpdateHandler() {
         super(EagleBoardScheduler.this._scoutRecords);
      }
   }

   public class ScoutsScheduledCellsHandler extends EagleBoardScheduler.DataRecordCellsHandler<ScoutRecord> {
      public ScoutsScheduledCellsHandler() {
         super(EagleBoardScheduler.this._scoutsScheduledRecords);
      }
   }

   public class ScoutsScheduledUpdateHandler extends EagleBoardScheduler.DataRecordUpdateHandler<ScoutRecord> {
      public ScoutsScheduledUpdateHandler() {
         super(EagleBoardScheduler.this._scoutsScheduledRecords);
      }
   }

   public class SeatBoardHandler extends EagleBoardScheduler.CoreBoardHandler {
      @Override
      public synchronized void handle(String var1, HttpServletRequest var2, HttpServletResponse var3) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(var2);
            EagleBoardScheduler.verbose(var2.getParameterMap());
            String var5 = var2.getParameter("RoomID");
            String var6 = var2.getParameter("ScoutID");
            String var7 = var2.getParameter("ChairID");
            String var8 = var2.getParameter("MemberIDs");
            EagleBoardScheduler.verbose("RoomID: " + var5);
            EagleBoardScheduler.verbose("ScoutID: " + var6);
            EagleBoardScheduler.verbose("ChairID: " + var7);
            EagleBoardScheduler.verbose("MemberIDs: " + var8);
            RoomRecord var9 = EagleBoardScheduler.this._roomRecords.get(var5);
            if (var9 == null) {
               this.sendError("ERROR: Invalid Room ID" + var5, var3);
            } else if (var9.getScout().length() > 0) {
               this.sendError("ERROR: Room " + var9.getRoom() + "is already assigned to " + var9.getScout(), var3);
            } else {
               ScoutRecord var10 = EagleBoardScheduler.this._scoutRecords.get(var6);
               if (var10 == null) {
                  this.sendError("ERROR: Invalid Scout ID" + var6, var3);
                  // Verify was removed, so a Registered scout is seated directly.
                  // "Verified" is still accepted for legacy records carried over
                  // from a run made before that change.
               } else if (!var10.getStatus().equals("Registered") && !var10.getStatus().equals("Verified")) {
                  this.sendError("ERROR: Invalid Status '" + var10.getStatus() + "', expected '" + "Registered" + "'", var3);
               } else if (!"".equals(var10.getRoom()) && !var10.getRoom().equals(var9.getRoom())) {
                  this.sendError("ERROR: Scout Already Assigned Room: " + var10.getRoom(), var3);
               } else if (var8 == null || var8.trim().length() == 0) {
                  // No board members: reject cleanly instead of crashing on
                  // StringTokenizer(null) or silently seating an empty board.
                  this.sendError("ERROR: No board members selected", var3);
               } else {
                  AdultRecord var11 = EagleBoardScheduler.this._adultRecords.get(var7);
                  ArrayList<AdultRecord> var12 = new ArrayList<>();
                  StringBuffer var13 = new StringBuffer();
                  StringBuffer var14 = new StringBuffer();
                  StringTokenizer var15 = new StringTokenizer(var8, ",", false);

                  while (var15.hasMoreTokens()) {
                     String var16 = var15.nextToken().trim();
                     AdultRecord var17 = EagleBoardScheduler.this._adultRecords.get(var16);
                     if (var17 == null) {
                        this.sendError("ERROR: Invalid Member ID " + var16, var3);
                        return;
                     }

                     if (var17.getRoom().length() > 0) {
                        this.sendError("ERROR: Member " + var17.getFullName() + " already assigned to a board in room " + var17.getRoom(), var3);
                        return;
                     }

                     if (var13.length() > 0) {
                        var13.append(",");
                     }

                     var13.append(var17.getShortName());
                     if (var14.length() > 0) {
                        var14.append(",");
                     }

                     var14.append(var17.getFullName());
                     var12.add(var17);
                  }

                  String var22 = var13.toString();
                  String var23 = var14.toString();
                  var9.setScout(var10.getFullName());
                  var9.setLeaders(var22);
                  var10.setRoom(var9.getRoom());
                  // Seat and Start are merged: seating a board makes it active
                  // ("InProgress") in one step. The separate Seated state and
                  // the Start button were redundant and have been removed.
                  var10.setStatus("InProgress");
                  var10.setBoardMembers(var23);
                  var10.setBoardMemberIDs(var8);
                  if (var11 != null) {
                     var10.setBoardChair(var11.getFullName());
                     var10.setBoardChairID(var11.getID());
                  }

                  for (AdultRecord var19 : var12) {
                     var19.setRoom(var9.getRoom());
                  }

                  var10.updateFields(true);
                  EagleBoardScheduler.this._scoutRecords.store();
                  EagleBoardScheduler.this._roomRecords.store();
                  EagleBoardScheduler.this._adultRecords.store();
                  this.sendSuccess(var3);
               }
            }
         }
      }
   }

   public class UpdateConfigHandler implements WebServer.WebHandler {
      @Override
      public void handle(String var1, HttpServletRequest var2, HttpServletResponse var3) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(var2);
            boolean var5 = false;
            boolean var6 = false;
            ConfigRecord var7 = new ConfigRecord(var2.getParameterMap());
            ConfigRecord var8 = EagleBoardScheduler.this._configRecords.get(var7.getID());
            if (var8 != null) {
               EagleBoardScheduler.verbose("UPDATING EXISTING CONFIG RECORD: " + var8);
               var8.updateFrom(var7, ConfigRecord.COLUMNS);
            } else {
               EagleBoardScheduler.verbose("NEW CONFIG RECORD: " + var7);
               EagleBoardScheduler.this._configRecords.add(var7, false);
            }

            EagleBoardScheduler.this._configRecords.store();
            var3.setStatus(200);
         }
      }
   }

}
