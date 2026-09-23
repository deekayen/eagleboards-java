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

   public static void main(String[] args) throws Exception {
      String shortFlags = "vw?";
      String[] longFlags = new String[]{"verbose", "help", "debug", "windows"};
      String shortOptions = "dapc";
      String[] longOptions = new String[]{"dir", "prereg", "adults", "config", "port", "sugkey", "sugid", "bind"};
      Commandline cmdline = null;

      try {
         cmdline = new Commandline(args, shortFlags, shortOptions, longFlags, longOptions);
      } catch (Throwable parseFailure) {
         System.out.println("\n\n   ERROR:  invalid command line argument(s): " + parseFailure.getMessage() + "\n\n");
         System.exit(1);
      }

      if (cmdline.hasFlag("debug")) {
         FileHandler logFile = new FileHandler("eagle-board-scheduler.log");
         logFile.setFormatter(new SimpleFormatter());
         logFile.setLevel(Level.ALL);
         _debugLogger = Logger.getLogger("shkc");
         _debugLogger.setLevel(Level.ALL);
         _debugLogger.addHandler(logFile);
         System.out.println("DEBUGGING ENABLED");
      }

      if (cmdline.hasFlag("?:help")) {
         usage();
         System.exit(1);
      }

      if (cmdline.hasFlag("v:verbose")) {
         _verbose = true;
      } else {
         _verbose = false;
      }

      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
      String today = dateFormat.format(new Date());
      String dataDir = cmdline.getOption("d:dir", today);
      String preregFile = cmdline.getOption("p:prereg");
      String configFile = cmdline.getOption("c:config");
      String adultsFile = cmdline.getOption("a:adults");
      int port = cmdline.getIntOption("port", 8080);
      String sugKey = cmdline.getOption("sugkey", null);
      String sugId = cmdline.getOption("sugid", null);
      // -bind <ip-prefix>: restrict the listener and the advertised URLs to the
      // single interface we actually serve from (e.g. "192.168." for the venue
      // wifi), so virtual adapters don't each pop their own dialog. When absent,
      // behavior is unchanged: listen on every interface and list them all.
      String bindPrefix = cmdline.getOption("bind", null);
      String boundAddress = null;
      if (args.length != 0 && cmdline.hasFlag("w:windows")) {
      }

      System.out.println("\n\nAvailable Network Interfaces");
      Enumeration interfaces = NetworkInterface.getNetworkInterfaces();

      while (interfaces.hasMoreElements()) {
         StringBuffer urls = new StringBuffer();
         NetworkInterface networkInterface = (NetworkInterface)interfaces.nextElement();
         int addressCount = 0;
         Enumeration addresses = networkInterface.getInetAddresses();

         while (addresses.hasMoreElements()) {
            InetAddress address = (InetAddress)addresses.nextElement();
            if (address instanceof Inet4Address
               && !address.getHostAddress().startsWith("127")
               && (bindPrefix == null || address.getHostAddress().startsWith(bindPrefix))) {
               urls.append("\n                 http://" + address.getHostAddress() + ":" + port);
               if (boundAddress == null) {
                  boundAddress = address.getHostAddress();
               }

               addressCount++;
            }
         }

         if (addressCount > 0) {
            if (args.length == 0 || cmdline.hasFlag("w:windows")) {
               PopupDialog popup = new PopupDialog("        Connect to the following URLs\n" + urls.toString() + "\n");
               popup.setVisible(true);
            }

            System.out.println(urls.toString());
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

      System.out.println("\n\n URL: http://<ip-address>:" + port + "\n\n");

      try {
         EagleBoardScheduler scheduler = new EagleBoardScheduler(port, dataDir, preregFile, adultsFile, configFile, sugKey, sugId);
         scheduler.run();
      } catch (Exception startupFailure) {
         startupFailure.printStackTrace();
         System.err.println("\n\n  ERROR: " + startupFailure.getMessage() + "\n\n");
      }
   }

   public static void usage() {
      System.out
         .println(
            "\n\n\n   USAGE\n\n          java -jar eagle-board-scheduler.jar <options>\n\n      OPTIONS\n\n         -w[indows]                     : popup dialog for config info\n         -v[erbose]                     : print verbose messages\n         -h[elp]                        : print this message\n\n         -d[ir]    <data-directory>     : directory where data files live\n\n         -a[dult]  <adult-history-file> : file containing adult auto-fill data\n         -c[onfig] <config-file>        : scheduler config file\n         -p[rereg] <prereg-file>        : preregistration data file (csv) from district website\n         -sugkey <signup-genius-key>    : SignupGenius API KEY\n         -sugid  <signup-id>            : SignupGenius signup ID (optional)\n\n         -bind   <ip-prefix>            : only listen on / advertise the interface whose\n                                          IPv4 address starts with this (e.g. 192.168.);\n                                          127.0.0.1 stays reachable either way;\n                                          default is every interface\n\n"
         );
   }

   public EagleBoardScheduler(int port, String dataDir, String preregFile, String adultHistoryPath, String configFileName, String sugKey, String sugId) throws Exception {
      this._dataRoot = new File(dataDir);
      if (!this._dataRoot.exists() && !this._dataRoot.mkdir()) {
         throw new IOException("cannot create data directory: " + this._dataRoot.getAbsolutePath());
      }

      if (adultHistoryPath != null) {
         this._adultHistoryFile = new File(adultHistoryPath);
         if (!this._adultHistoryFile.exists()) {
            throw new Exception("error: adult history file '" + adultHistoryPath + "' does not exist.");
         }
      } else {
         this._adultHistoryFile = new File(this._dataRoot, "adult_history.csv");
      }

      if (configFileName != null) {
         this._configFile = new File(configFileName);
         if (!this._configFile.exists()) {
            throw new Exception("error: config  file '" + configFileName + "' does not exist.");
         }
      } else {
         this._configFile = new File(this._dataRoot, "config.properties");
      }

      if (configFileName == null) {
         configFileName = "config.properties";
      }

      this._server = new WebServer("WEBROOT");
      this._server.setPort(port);
      this._scoutFile = new File(this._dataRoot, "scouts.csv");
      this._adultFile = new File(this._dataRoot, "adults.csv");
      this._roomFile = new File(this._dataRoot, "rooms.csv");
      this._scoutsScheduledFile = new File(this._dataRoot, "scouts_scheduled.csv");
      this._configFile = new File(this._dataRoot, configFileName);
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
         ConfigRecord defaultConfig = this._configRecords.addNew("DEFAULT", true);
         defaultConfig.setValue("Name", "DEFAULT");
         defaultConfig.setValue("ID", "DEFAULT");
         this._configRecords.store();
      }

      if (preregFile != null) {
         File preregFileHandle = new File(preregFile);
         if (!preregFileHandle.exists()) {
            throw new Exception("error: no preregistration file '" + preregFile + "' found.");
         }

         System.out.println("\n\n   loading SCOUT pre-registrations: " + preregFile + "\n\n");
         NegaPreRegScoutRecordConverter scoutConverter = new NegaPreRegScoutRecordConverter(this._scoutsScheduledRecords);
         this._scoutsScheduledRecords.clearAll();
         scoutConverter.convert(preregFileHandle);
         this._scoutsScheduledRecords.store();
         System.out.println("\n\n   loading ADULT pre-registrations: " + preregFile + "\n\n");
         NegaPreRegAdultRecordConverter adultConverter = new NegaPreRegAdultRecordConverter(this._adultHistoryRecords);
         adultConverter.convert(preregFileHandle);
         this._adultHistoryRecords.store();
      } else if (sugKey != null && sugKey.length() > 10) {
         try {
            SignUpGeniusPlugin signUpGenius = new SignUpGeniusPlugin(sugKey, sugId, this._adultHistoryRecords, this._scoutsScheduledRecords);
            signUpGenius.populatePreRegistrations();
         } catch (Exception signUpFailure) {
            System.out.println("\n\n   SignupGenius Error: " + signUpFailure.getMessage() + "\n\n");
         }
      } else {
         System.out.println("\n   no prereg-file or SignupGenius DB loaded \n\n");
      }

      this._server.addHandler("/register-youth", new EagleBoardScheduler.RegisterScoutHandler());
      this._server.addHandler("/register-adult", new EagleBoardScheduler.RegisterAdultHandler());
      this._server.addHandler("/update-config", new EagleBoardScheduler.UpdateConfigHandler());
      this._server.addHandler("/youth-cells", new EagleBoardScheduler.ScoutCellsHandler());
      this._server.addHandler("/youth-scheduled-cells", new EagleBoardScheduler.ScoutsScheduledCellsHandler());
      this._server.addHandler("/adult-cells", new EagleBoardScheduler.AdultCellsHandler());
      this._server.addHandler("/adult-history-cells", new EagleBoardScheduler.AdultHistoryCellsHandler());
      this._server.addHandler("/room-cells", new EagleBoardScheduler.RoomCellsHandler());
      this._server.addHandler("/youth-update", new EagleBoardScheduler.ScoutUpdateHandler());
      this._server.addHandler("/youth-scheduled-update", new EagleBoardScheduler.ScoutsScheduledUpdateHandler());
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
      this._server.addHandler("/youth-autofill", new EagleBoardScheduler.AutoFillHandler<>(this._scoutsScheduledRecords, "Email"));
      this._server.addHandler("/config-autofill", new EagleBoardScheduler.AutoFillHandler<>(this._configRecords, "Name"));
   }

   public void run() throws Exception {
      this._server.start();
   }

   private String[] getFields(String csvList, String[] defaults) {
      if (csvList != null && csvList.length() != 0) {
         StringTokenizer tokens = new StringTokenizer(csvList, ",+ []", false);
         String[] fields = new String[tokens.countTokens()];

         for (int i = 0; i < fields.length; i++) {
            fields[i] = tokens.nextToken();
         }

         return fields;
      } else {
         return defaults;
      }
   }

   public static void verbose(Object message) {
      if (_verbose) {
         System.out.println(message);
      }
   }

   public static void error(String message) {
      System.out.println("\n\nERROR: " + message + "\n");
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

      public AutoFillHandler(DataRecordFile<T> records, String lookupField) {
         this._records = records;
         this._lookupField = lookupField;
      }

      @Override
      public synchronized void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         EagleBoardScheduler.verbose(request);
         EagleBoardScheduler.verbose(request.getParameterMap());
         String op = request.getParameter("op");
         String format = request.getParameter("fmt");
         String lookupValue = request.getParameter(this._lookupField);
         String contentType = "text/json";
         StringBuffer out = new StringBuffer();
         if ("list".equals(op) || lookupValue == null) {
            contentType = "text/json";
            out.append("{ options: [\n");
            boolean first = true;

            for (DataRecord record : this._records.getRecords()) {
               if (record.get(this._lookupField).length() > 4) {
                  if (!first) {
                     out.append(",\n");
                  }

                  first = false;
                  out.append("   { value: \"").append(record.get(this._lookupField));
                  out.append("\", text:\"").append(record.get(this._lookupField)).append("\"}");
               }
            }

            out.append("\n]\n}\n");
         } else if (lookupValue != null) {
            if ("json".equals(format)) {
               contentType = "text/json";
               List jsonMatches = this._records.get(this._lookupField, lookupValue);
               if (jsonMatches.size() > 0) {
                  ((DataRecord)jsonMatches.get(0)).toJSON(out);
               }
            } else {
               contentType = "text/xml";
               out.append("<data>");
               List xmlMatches = this._records.get(this._lookupField, lookupValue);
               if (xmlMatches.size() > 0) {
                  DataRecord xmlRecord = (DataRecord)xmlMatches.get(0);
                  boolean firstColumn = true;

                  for (String column : this._records.getColumns()) {
                     if (!firstColumn) {
                        out.append("\n");
                     }

                     firstColumn = false;
                     out.append("<").append(column).append(">");
                     out.append(xmlRecord.getValue(column));
                     out.append("</").append(column).append(">");
                  }
               }

               out.append("</data>");
            }
         }

         response.setContentLength(out.length());
         response.setContentType(contentType);
         response.getOutputStream().write(out.toString().getBytes());
         response.setStatus(200);
      }
   }

   public class CompleteBoardHandler extends EagleBoardScheduler.CoreBoardHandler {
      @Override
      public synchronized void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(request);
            EagleBoardScheduler.verbose(request.getParameterMap());
            String scoutId = request.getParameter("ScoutID");
            String result = request.getParameter("Result");
            String notes = request.getParameter("Notes");
            String cost = request.getParameter("Cost");
            String bsaHours = request.getParameter("BSAHours");
            String otherHours = request.getParameter("OtherHours");
            EagleBoardScheduler.verbose("ScoutID: " + scoutId);
            EagleBoardScheduler.verbose("Result: " + result);
            EagleBoardScheduler.verbose("Notes: " + notes);
            EagleBoardScheduler.verbose("ProjectCost: " + cost);
            EagleBoardScheduler.verbose("BSA Hours: " + bsaHours);
            EagleBoardScheduler.verbose("Other Hours: " + otherHours);
            // Exactly the three results the Complete dialog offers. This used
            // to accept any string of five or more characters, so a typo or a
            // hand-built request went into the district's record as a result.
            if ("Approved".equals(result) || "Adjourned".equals(result) || "NotApproved".equals(result)) {
               if (notes == null) {
                  notes = "";
               }

               if (cost != null) {
                  notes = notes + "(Cost: " + cost + ")";
               }

               if (bsaHours != null) {
                  notes = notes + "(BSA: " + bsaHours + " hrs)";
               }

               if (otherHours != null) {
                  notes = notes + "(Other: " + otherHours + " hrs)";
               }

               ScoutRecord scout = EagleBoardScheduler.this._scoutRecords.get(scoutId);
               if (scout == null) {
                  this.sendError("Invalid Scout ID" + scoutId, response);
               // "Seated" is deliberately no longer accepted. It used to be,
               // harmlessly, because seating went straight to InProgress and
               // no record was ever left sitting in Seated. Now that seating
               // only convenes the board, a Seated scout is one still waiting
               // outside the room, and completing there would record a result
               // for a review that never happened. The error message already
               // said "expected 'InProgress'" -- the check now matches it.
               } else if (!scout.getStatus().equals("InProgress")) {
                  this.sendError("Invalid Scout Status '" + scout.getStatus() + "' expected '" + "InProgress" + "'", response);
               } else {
                  RoomRecord room = null;

                  for (RoomRecord candidateRoom : EagleBoardScheduler.this._roomRecords.getRecords()) {
                     if (candidateRoom.getRoom().equals(scout.getRoom())) {
                        room = candidateRoom;
                        break;
                     }
                  }

                  // A room renamed under the board no longer matches by name, but its
                  // record still names the scout. Without this it stayed "occupied"
                  // by a board that had finished, and nothing could be seated there.
                  if (room == null) {
                     for (RoomRecord candidateRoom : EagleBoardScheduler.this._roomRecords.getRecords()) {
                        if (candidateRoom.getScout().length() > 0 && candidateRoom.getScout().equals(scout.getFullName())) {
                           room = candidateRoom;
                           break;
                        }
                     }
                  }

                  // The adults are released by the room name the SCOUT holds,
                  // not through the room record. If the room was renamed or
                  // deleted on the Admin page while the board sat in it, the
                  // record is gone, but the review still happened: refusing
                  // here lost the result, and the Reset that followed left
                  // every member committed to a room that no longer existed.
                  // "" and "N/A" are never a board's room ("N/A" marks the
                  // adults who have gone home), so they release nobody.
                  String boardRoom = scout.getRoom();

                  if (boardRoom.length() > 0 && !"N/A".equals(boardRoom)) {
                     for (AdultRecord adult : EagleBoardScheduler.this._adultRecords.getRecords()) {
                        if (adult.getRoom().equals(boardRoom)) {
                           adult.setRoom("");
                        }
                     }
                  }

                  scout.setStatus("Completed");
                  scout.setRoom("N/A");
                  scout.setNotes(notes);
                  scout.setResult(result);
                  if (room != null) {
                     room.setScout("");
                     room.setLeaders("");
                  }

                  scout.updateFields(true);
                  EagleBoardScheduler.this._scoutRecords.store();
                  EagleBoardScheduler.this._roomRecords.store();
                  EagleBoardScheduler.this._adultRecords.store();
                  this.sendSuccess(response);
               }
            } else {
               this.sendError("Invalid Result '" + result + "', expected Approved, Adjourned or NotApproved", response);
            }
         }
      }
   }

   public abstract class CoreBoardHandler implements WebServer.WebHandler {
      protected void sendError(String message, HttpServletResponse response) throws IOException {
         StringBuffer out = new StringBuffer();
         out.append(message);
         String body = out.toString();
         response.setContentLength(body.length());
         response.setContentType("text/plain");
         response.getOutputStream().write(body.getBytes());
         response.setStatus(304);
      }

      public void sendSuccess(HttpServletResponse response) throws IOException {
         String body = "OK.";
         response.setContentLength(body.length());
         response.setContentType("text/plain");
         response.getOutputStream().write(body.getBytes());
         response.setStatus(200);
      }
   }

   public class DataRecordCellsHandler<T extends DataRecord> implements WebServer.WebHandler {
      private DataRecordFile<T> _records;

      public DataRecordCellsHandler(DataRecordFile<T> records) {
         this._records = records;
      }

      @Override
      public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(request);
            EagleBoardScheduler.verbose(request.getParameterMap());
            String colsParam = request.getParameter("cols");
            String dataParam = request.getParameter("data");
            String format = request.getParameter("fmt");
            String fileName = request.getParameter("filename");
            if (format == null) {
               format = "rows";
            }

            String filterParam = request.getParameter("filter");
            String filterColumn = null;
            String filterValues = null;
            if (filterParam != null) {
               StringTokenizer filterTokens = new StringTokenizer(filterParam, "~#", false);
               if (filterTokens.countTokens() == 2) {
                  filterColumn = filterTokens.nextToken().trim();
                  filterValues = filterTokens.nextToken().trim();
               }
            }

            EagleBoardScheduler.verbose("COLS: " + colsParam);
            EagleBoardScheduler.verbose("DATA: " + dataParam);
            EagleBoardScheduler.verbose("FILTER: " + filterColumn + " ~ " + filterValues);
            String contentType = "text/xml";
            String[] columns = EagleBoardScheduler.this.getFields(colsParam, this._records.getColumns());
            String[] extraColumns = EagleBoardScheduler.this.getFields(dataParam, null);
            StringBuffer out = new StringBuffer();
            if (format.equals("data")) {
               contentType = "text/xml";
               out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><data>");

               for (DataRecord record : this._records.getRecords()) {
                  if (filterValues != null && filterColumn != null) {
                     String cellValue = record.get(filterColumn);
                     if (cellValue != null && cellValue.length() > 0 && filterValues.indexOf(cellValue) >= 0) {
                        record.toString(out, columns, extraColumns, format);
                        out.append("\n");
                     }
                  } else {
                     record.toString(out, columns, extraColumns, format);
                     out.append("\n");
                  }
               }

               out.append("</data>");
            } else if (format.equals("csv")) {
               contentType = "text/csv";
               boolean firstColumn = true;

               for (String column : columns) {
                  if (!firstColumn) {
                     out.append(",");
                  }

                  firstColumn = false;
                  out.append(column);
               }

               out.append("\n");

               for (DataRecord csvRecord : this._records.getRecords()) {
                  if (filterValues != null && filterColumn != null) {
                     String csvCellValue = csvRecord.get(filterColumn);
                     if (csvCellValue != null && csvCellValue.length() > 0 && filterValues.indexOf(csvCellValue) >= 0) {
                        csvRecord.toString(out, columns, null, format);
                        out.append("\n");
                     }
                  } else {
                     csvRecord.toString(out, columns, null, format);
                     out.append("\n");
                  }
               }
            } else {
               contentType = "text/xml";
               out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><rows>");

               for (DataRecord rowRecord : this._records.getRecords()) {
                  if (filterValues != null && filterColumn != null) {
                     String rowCellValue = rowRecord.get(filterColumn);
                     if (rowCellValue != null && rowCellValue.length() > 0 && filterValues.indexOf(rowCellValue) >= 0) {
                        rowRecord.toCells(out, columns, extraColumns);
                        out.append("\n");
                     }
                  } else {
                     rowRecord.toCells(out, columns, extraColumns);
                     out.append("\n");
                  }
               }

               out.append("</rows>");
            }

            if (fileName != null && fileName.length() > 0) {
               response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
            }

            String body = out.toString();
            response.setContentLength(body.length());
            response.setContentType(contentType);
            response.getOutputStream().write(body.getBytes());
            response.setStatus(200);
         }
      }

      public DataRecordFile<T> getRecords() {
         return this._records;
      }
   }

   public class DataRecordUpdateHandler<T extends DataRecord> implements WebServer.WebHandler {
      private DataRecordFile<T> _records;

      public DataRecordUpdateHandler(DataRecordFile<T> records) {
         this._records = records;
      }

      @Override
      public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(request);
            EagleBoardScheduler.verbose(request.getParameterMap());
            String status = request.getParameter("!nativeeditor_status");
            String rowId = request.getParameter("gr_id");
            if (rowId == null) {
               this.sendResponse("invalid", rowId, response);
            } else {
               DataRecord record = this._records.get(rowId);
               if (record == null) {
                  if (!"inserted".equals(status)) {
                     EagleBoardScheduler.verbose("no such record: " + rowId);
                     this.sendResponse("invalid", rowId, response);
                     return;
                  }

                  record = this._records.addNew(rowId, false);
               } else if ("inserted".equals(status)) {
                  EagleBoardScheduler.verbose("record already exists: " + rowId);
                  this.sendResponse("invalid", rowId, response);
                  return;
               }

               if ("deleted".equals(status)) {
                  this._records.remove(rowId);
               } else {
                  for (String column : this._records.getColumns()) {
                     String value = request.getParameter(column);
                     EagleBoardScheduler.verbose("CHECKING: " + column + " => " + value);
                     if (value != null) {
                        EagleBoardScheduler.verbose("UPDATING: " + column + " => " + value);
                        record.put(column, value);
                     }
                  }
               }

               record.updateFields(false);
               this._records.store();
               this.sendResponse(status, rowId, response);
            }
         }
      }

      private void sendResponse(String status, String rowId, HttpServletResponse response) throws IOException {
         StringBuffer out = new StringBuffer();
         out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><data>");
         out.append("<action type=\"").append(status).append("\" sid=\"").append(rowId);
         out.append("\" tid=\"").append(rowId).append("\" />");
         out.append("</data>");
         String body = out.toString();
         response.setContentLength(body.length());
         response.setContentType("text/xml");
         response.getOutputStream().write(body.getBytes());
         response.setStatus(200);
      }
   }

   public class InProgressBoardHandler extends EagleBoardScheduler.CoreBoardHandler {
      @Override
      public synchronized void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(request);
            EagleBoardScheduler.verbose(request.getParameterMap());
            String scoutId = request.getParameter("ScoutID");
            EagleBoardScheduler.verbose("ScoutID: " + scoutId);
            ScoutRecord scout = EagleBoardScheduler.this._scoutRecords.get(scoutId);
            if (scout == null) {
               this.sendError("ERROR: Invalid Scout ID" + scoutId, response);
            } else if (!scout.getStatus().equals("Seated")) {
               this.sendError("ERROR: Invalid Status '" + scout.getStatus() + "', expected '" + "Seated" + "'", response);
            } else {
               scout.setStatus("InProgress");
               scout.updateFields(true);
               EagleBoardScheduler.this._scoutRecords.store();
               this.sendSuccess(response);
            }
         }
      }
   }

   public class PostponeBoardHandler extends EagleBoardScheduler.CoreBoardHandler {
      @Override
      public synchronized void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(request);
            EagleBoardScheduler.verbose(request.getParameterMap());
            String scoutId = request.getParameter("ScoutID");
            EagleBoardScheduler.verbose("ScoutID: " + scoutId);
            ScoutRecord scout = EagleBoardScheduler.this._scoutRecords.get(scoutId);
            if (scout == null) {
               this.sendError("ERROR: Invalid Scout ID" + scoutId, response);
            } else if (!scout.getStatus().equals("Registered") && !scout.getStatus().equals("Verified")) {
               this.sendError("ERROR: Invalid Status '" + scout.getStatus() + "', expected '" + "Registered" + " | " + "Verified" + "'", response);
            } else {
               scout.setStatus("Postponed");
               scout.updateFields(true);
               EagleBoardScheduler.this._scoutRecords.store();
               this.sendSuccess(response);
            }
         }
      }
   }

   public class RegisterAdultHandler implements WebServer.WebHandler {
      @Override
      public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(request);
            AdultRecord adult = new AdultRecord(request.getParameterMap());
            adult.setRoom("");
            AdultRecord existing = EagleBoardScheduler.this._adultRecords.get(adult.getID());
            if (existing != null) {
               existing.updateFrom(adult, EagleBoardScheduler.ADULT_REG_FIELDS);
               adult = existing;
            } else {
               EagleBoardScheduler.verbose("NEW ADULT RECORD: " + adult);
               EagleBoardScheduler.this._adultRecords.add(adult, false);
            }

            AdultRecord historyRecord = EagleBoardScheduler.this._adultHistoryRecords.get(adult.getID());
            if (historyRecord == null) {
               historyRecord = adult.clone();
               EagleBoardScheduler.verbose("Adding new Adult History Record: " + historyRecord);
               EagleBoardScheduler.this._adultHistoryRecords.add(historyRecord, false);
               adult.setFlags("W");
            } else {
               if ("Member".equals(historyRecord.getFinalBoardRole()) && "Chair".equals(adult.getFinalBoardRole())) {
                  historyRecord.setValue("FinalBoard", "Chair");
               }

               if ("Member".equals(historyRecord.getProjectReviewRole()) && "Chair".equals(adult.getProjectReviewRole())) {
                  historyRecord.setValue("ProjectReview", "Chair");
               }

               historyRecord.updateFrom(adult, EagleBoardScheduler.ADULT_REG_FIELDS);
               adult.setFlags("P");
            }

            String history = historyRecord.getBoardHistory();
            history = history + "(" + EagleBoardScheduler.this.DATE_FORMAT.format(new Date()) + ")";
            historyRecord.setBoardHistory(history);
            EagleBoardScheduler.this._adultRecords.store();
            EagleBoardScheduler.this._adultHistoryRecords.store();
            response.setStatus(200);
         }
      }
   }

   public class RegisterScoutHandler implements WebServer.WebHandler {
      @Override
      public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(request);
            int preregCount = 0;
            int walkinCount = 0;

            for (ScoutRecord countedScout : EagleBoardScheduler.this._scoutRecords.getRecords()) {
               if (countedScout.getRegNum().startsWith("P")) {
                  preregCount++;
               } else if (countedScout.getRegNum().startsWith("W")) {
                  walkinCount++;
               }
            }

            ScoutRecord scout = new ScoutRecord(request.getParameterMap());
            ScoutRecord existingScout = EagleBoardScheduler.this._scoutRecords.get(scout.getID());
            if (existingScout != null) {
               EagleBoardScheduler.verbose("UPDATING EXISTING SCOUT RECORD: " + existingScout);
               existingScout.updateFrom(scout, EagleBoardScheduler.SCOUT_REG_FIELDS);
               scout = existingScout;
               if (scout.getStatus().length() == 0) {
                  scout.setStatus("Registered");
               }
            } else {
               EagleBoardScheduler.verbose("NEW SCOUT RECORD: " + scout);
               EagleBoardScheduler.this._scoutRecords.add(scout, false);
               scout.setStatus("Registered");
            }

            if (EagleBoardScheduler.this._scoutsScheduledRecords.get(scout.getID()) == null
               && EagleBoardScheduler.this._scoutsScheduledRecords.get("Email", scout.getEmail()).size() == 0) {
               scout.setRegNum("W" + (walkinCount + 1));
            } else {
               scout.setRegNum("P" + (preregCount + 1));
            }

            EagleBoardScheduler.this._scoutRecords.store();
            response.setStatus(200);
         }
      }
   }

   public class ResetBoardHandler extends EagleBoardScheduler.CoreBoardHandler {
      @Override
      public synchronized void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(request);
            EagleBoardScheduler.verbose(request.getParameterMap());
            String scoutId = request.getParameter("ScoutID");
            EagleBoardScheduler.verbose("ScoutID: " + scoutId);
            ScoutRecord scout = EagleBoardScheduler.this._scoutRecords.get(scoutId);
            if (scout == null) {
               this.sendError("Invalid Scout ID" + scoutId, response);
            } else if (!scout.getStatus().equals("InProgress") && !scout.getStatus().equals("Seated") && !scout.getStatus().equals("Verified")) {
               this.sendError("Invalid Scout Status '" + scout.getStatus() + "' expected " + "'Verified', 'InProgress' or 'Seated' " + "'", response);
            } else {
               RoomRecord room = null;

               for (RoomRecord candidateRoom : EagleBoardScheduler.this._roomRecords.getRecords()) {
                  if (candidateRoom.getRoom().equals(scout.getRoom())) {
                     room = candidateRoom;
                     break;
                  }
               }

               // A room renamed under the board no longer matches by name, but its
               // record still names the scout. Without this it stayed "occupied"
               // by a board that had finished, and nothing could be seated there.
               if (room == null) {
                  for (RoomRecord candidateRoom : EagleBoardScheduler.this._roomRecords.getRecords()) {
                     if (candidateRoom.getScout().length() > 0 && candidateRoom.getScout().equals(scout.getFullName())) {
                        room = candidateRoom;
                        break;
                     }
                  }
               }

               // Released by the scout's room name, as in CompleteBoardHandler:
               // a room renamed or deleted under a board must not strand its
               // members, who would otherwise stay committed to it all night.
               String boardRoom = scout.getRoom();

               if (boardRoom.length() > 0 && !"N/A".equals(boardRoom)) {
                  for (AdultRecord adult : EagleBoardScheduler.this._adultRecords.getRecords()) {
                     if (adult.getRoom().equals(boardRoom)) {
                        adult.setRoom("");
                     }
                  }
               }

               if (room != null) {
                  room.setScout("");
                  room.setLeaders("");
               }

               scout.setStatus("Registered");
               scout.setRoom("");
               scout.setBoardChair("");
               scout.setBoardChairID("");
               scout.setBoardMemberIDs("");
               scout.setBoardMembers("");
               scout.updateFields(true);
               EagleBoardScheduler.this._scoutRecords.store();
               EagleBoardScheduler.this._roomRecords.store();
               EagleBoardScheduler.this._adultRecords.store();
               this.sendSuccess(response);
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
      public synchronized void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         EagleBoardScheduler.verbose("RoomChangeHandler: " + request);
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(request);
            EagleBoardScheduler.verbose(request.getParameterMap());
            String roomId1 = request.getParameter("RmID1");
            String roomId2 = request.getParameter("RmID2");
            if (roomId1 == null || roomId1.equals("")) {
               this.sendError("ERROR: Invalid Room ID" + roomId1, response);
            } else if (roomId2 != null && !roomId2.equals("")) {
               EagleBoardScheduler.verbose("ChangeRoom: RmID1=" + roomId1 + ", RmID2=" + roomId2);
               RoomRecord room1 = EagleBoardScheduler.this._roomRecords.get(roomId1);
               RoomRecord room2 = EagleBoardScheduler.this._roomRecords.get(roomId2);
               if (room1 == null) {
                  this.sendError("ERROR: No Such Room" + roomId1, response);
               } else if (room2 == null) {
                  this.sendError("ERROR: No Such Room" + roomId2, response);
               } else {
                  String leaders1 = room1.getLeaders();
                  String leaders2 = room2.getLeaders();
                  String scout1 = room1.getScout();
                  String scout2 = room2.getScout();
                  List<ScoutRecord> scoutsInRoom1 = EagleBoardScheduler.this._scoutRecords.get("Room", room1.getRoom());
                  List<ScoutRecord> scoutsInRoom2 = EagleBoardScheduler.this._scoutRecords.get("Room", room2.getRoom());
                  if (scoutsInRoom1.size() > 1) {
                     this.sendError("ERROR: Room assigned to multiple scouts: " + room1.getRoom(), response);
                  } else if (scoutsInRoom2.size() > 1) {
                     this.sendError("ERROR: Room assigned to multiple scouts: " + room2.getRoom(), response);
                  } else {
                     List<AdultRecord> adultsInRoom1 = EagleBoardScheduler.this._adultRecords.get("Room", room1.getRoom());
                     List<AdultRecord> adultsInRoom2 = EagleBoardScheduler.this._adultRecords.get("Room", room2.getRoom());
                     room1.setLeaders(leaders2);
                     room2.setLeaders(leaders1);
                     room1.setScout(scout2);
                     room2.setScout(scout1);

                     for (ScoutRecord scoutMovingTo2 : scoutsInRoom1) {
                        scoutMovingTo2.setRoom(room2.getRoom());
                     }

                     for (ScoutRecord scoutMovingTo1 : scoutsInRoom2) {
                        scoutMovingTo1.setRoom(room1.getRoom());
                     }

                     for (AdultRecord adultMovingTo2 : adultsInRoom1) {
                        adultMovingTo2.setRoom(room2.getRoom());
                     }

                     for (AdultRecord adultMovingTo1 : adultsInRoom2) {
                        adultMovingTo1.setRoom(room1.getRoom());
                     }

                     EagleBoardScheduler.this._roomRecords.store();
                     EagleBoardScheduler.this._scoutRecords.store();
                     EagleBoardScheduler.this._adultRecords.store();
                     this.sendSuccess(response);
                  }
               }
            } else {
               this.sendError("ERROR: Invalid Room ID" + roomId2, response);
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
      public synchronized void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(request);
            EagleBoardScheduler.verbose(request.getParameterMap());
            String roomId = request.getParameter("RoomID");
            String scoutId = request.getParameter("ScoutID");
            String chairId = request.getParameter("ChairID");
            String memberIds = request.getParameter("MemberIDs");
            EagleBoardScheduler.verbose("RoomID: " + roomId);
            EagleBoardScheduler.verbose("ScoutID: " + scoutId);
            EagleBoardScheduler.verbose("ChairID: " + chairId);
            EagleBoardScheduler.verbose("MemberIDs: " + memberIds);
            RoomRecord room = EagleBoardScheduler.this._roomRecords.get(roomId);
            if (room == null) {
               this.sendError("ERROR: Invalid Room ID" + roomId, response);
            } else if (room.getScout().length() > 0) {
               this.sendError("ERROR: Room " + room.getRoom() + "is already assigned to " + room.getScout(), response);
            } else {
               ScoutRecord scout = EagleBoardScheduler.this._scoutRecords.get(scoutId);
               if (scout == null) {
                  this.sendError("ERROR: Invalid Scout ID" + scoutId, response);
                  // Verify was removed, so a Registered scout is seated directly.
                  // "Verified" is still accepted for legacy records carried over
                  // from a run made before that change.
               } else if (!scout.getStatus().equals("Registered") && !scout.getStatus().equals("Verified")) {
                  this.sendError("ERROR: Invalid Status '" + scout.getStatus() + "', expected '" + "Registered" + "'", response);
               // "N/A" is what Complete leaves in a scout's Room. A Registered
               // scout holding it is one whose result was recorded against them
               // by mistake and set back to Registered on the Admin page; they
               // have no room, and must be seatable for their real board.
               } else if (!"".equals(scout.getRoom()) && !"N/A".equals(scout.getRoom()) && !scout.getRoom().equals(room.getRoom())) {
                  this.sendError("ERROR: Scout Already Assigned Room: " + scout.getRoom(), response);
               } else if (memberIds == null || memberIds.trim().length() == 0) {
                  // No board members: reject cleanly instead of crashing on
                  // StringTokenizer(null) or silently seating an empty board.
                  this.sendError("ERROR: No board members selected", response);
               } else {
                  AdultRecord chair = EagleBoardScheduler.this._adultRecords.get(chairId);
                  ArrayList<AdultRecord> members = new ArrayList<>();
                  StringBuffer memberNames = new StringBuffer();
                  StringTokenizer memberIdTokens = new StringTokenizer(memberIds, ",", false);

                  while (memberIdTokens.hasMoreTokens()) {
                     String memberId = memberIdTokens.nextToken().trim();
                     AdultRecord member = EagleBoardScheduler.this._adultRecords.get(memberId);
                     if (member == null) {
                        this.sendError("ERROR: Invalid Member ID " + memberId, response);
                        return;
                     }

                     // "N/A" is the Disable button's marker for an adult who
                     // has gone home, not a room anyone can be sent to, so it
                     // gets its own wording rather than "in room N/A".
                     if ("N/A".equals(member.getRoom())) {
                        this.sendError("ERROR: Member " + member.getFullName() + " has been disabled for tonight", response);
                        return;
                     }

                     if (member.getRoom().length() > 0) {
                        this.sendError("ERROR: Member " + member.getFullName() + " already assigned to a board in room " + member.getRoom(), response);
                        return;
                     }

                     // The size rules below count entries, so the same adult
                     // listed twice would let two people pass as a board of
                     // three. The UI's checkboxes cannot produce this; a
                     // hand-built or replayed request can.
                     if (members.contains(member)) {
                        this.sendError("ERROR: Member " + member.getFullName() + " is listed more than once", response);
                        return;
                     }

                     if (memberNames.length() > 0) {
                        memberNames.append(",");
                     }

                     // Full name, not getShortName()'s "F. Last": the room
                     // card is how someone looks up which room an adult is
                     // in, and initial-only made that lookup by first name
                     // impossible without already knowing their last name.
                     memberNames.append(member.getFullName());
                     members.add(member);
                  }

                  // Composition rules are enforced here as well as in
                  // process_seat.js. The browser is the normal way in, not the
                  // only one, and a board seated past the UI is a board that
                  // never met the rule -- which is only discovered later, from
                  // the record of a review that should not have happened.
                  //
                  // Guide to Advancement 8.0.0.3: a board of review has no
                  // fewer than three and no more than six members. A project
                  // proposal review is not a board of review (GTA 9.0.2.4) and
                  // this district runs it with two, under the same ceiling.
                  // Keep in step with checkBoardSize()/checkProjectSize().
                  boolean isProjectBoard = "Project".equals(scout.getBoardType());
                  int minMembers = isProjectBoard ? 2 : 3;

                  if (members.size() < minMembers) {
                     this.sendError("ERROR: Only " + members.size() + " board member(s) selected; "
                        + minMembers + " required for " + scout.getBoardType() + " boards", response);
                     return;
                  }

                  if (members.size() > 6) {
                     this.sendError("ERROR: " + members.size()
                        + " board members selected; no more than 6 permitted (Guide to Advancement 8.0.0.3)", response);
                     return;
                  }

                  // The Chair designation is binding. Promoting someone from
                  // Member to Chair is a deliberate edit on the Admin page; it
                  // must never happen as a side effect of seating a board
                  // because the qualified chairs were all busy.
                  if (chair == null) {
                     this.sendError("ERROR: Invalid Chair ID " + chairId, response);
                     return;
                  }

                  boolean chairIsMember = false;

                  for (AdultRecord candidate : members) {
                     if (candidate.getID().equals(chair.getID())) {
                        chairIsMember = true;
                        break;
                     }
                  }

                  if (!chairIsMember) {
                     this.sendError("ERROR: Chair " + chair.getFullName() + " is not one of the board members", response);
                     return;
                  }

                  String chairRole = isProjectBoard ? chair.getProjectReviewRole() : chair.getFinalBoardRole();

                  if (!"Chair".equals(chairRole)) {
                     this.sendError("ERROR: " + chair.getFullName() + " is not qualified to chair a "
                        + scout.getBoardType() + " board (role: "
                        + (chairRole == null || chairRole.length() == 0 ? "none" : chairRole) + ")", response);
                     return;
                  }

                  String memberNameList = memberNames.toString();
                  room.setScout(scout.getFullName());
                  room.setLeaders(memberNameList);
                  scout.setRoom(room.getRoom());
                  // Seating convenes the board only: the members get the room
                  // to go over the application, references and project workbook
                  // BEFORE the scout is called in. "Start Review" (the
                  // /inprogress-board endpoint) is what admits the scout and
                  // makes the board active. Seat and Start were merged once, on
                  // the view that the second step was redundant; separating
                  // them again is what lets the two phases be timed apart --
                  // the preview is capped, the interview has its own window.
                  scout.setStatus("Seated");
                  scout.setBoardMembers(memberNameList);
                  scout.setBoardMemberIDs(memberIds);
                  if (chair != null) {
                     scout.setBoardChair(chair.getFullName());
                     scout.setBoardChairID(chair.getID());
                  }

                  for (AdultRecord seatedMember : members) {
                     seatedMember.setRoom(room.getRoom());
                  }

                  scout.updateFields(true);
                  EagleBoardScheduler.this._scoutRecords.store();
                  EagleBoardScheduler.this._roomRecords.store();
                  EagleBoardScheduler.this._adultRecords.store();
                  this.sendSuccess(response);
               }
            }
         }
      }
   }

   public class UpdateConfigHandler implements WebServer.WebHandler {
      @Override
      public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(request);

            ConfigRecord config = new ConfigRecord(request.getParameterMap());
            ConfigRecord existingConfig = EagleBoardScheduler.this._configRecords.get(config.getID());
            if (existingConfig != null) {
               EagleBoardScheduler.verbose("UPDATING EXISTING CONFIG RECORD: " + existingConfig);
               existingConfig.updateFrom(config, ConfigRecord.COLUMNS);
            } else {
               EagleBoardScheduler.verbose("NEW CONFIG RECORD: " + config);
               EagleBoardScheduler.this._configRecords.add(config, false);
            }

            EagleBoardScheduler.this._configRecords.store();
            response.setStatus(200);
         }
      }
   }

}
