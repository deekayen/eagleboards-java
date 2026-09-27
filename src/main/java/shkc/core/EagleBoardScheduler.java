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
import java.util.Map;
import java.util.StringTokenizer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class EagleBoardScheduler {
   // No DOB (SPEC.md D-7): signing in again never writes a birthdate, and
   // leaves one already on file untouched (O-5). No Phone either (D-8): a
   // youth's number already on file is left alone the same way.
   private static final String[] SCOUT_REG_FIELDS = new String[]{"First", "Last", "Unit", "UnitType", "Email", "Leader"};
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

   // Undo (O-2, SPEC.md): single level, guarded by LOCK like everything
   // else. Each reversible handler builds an UndoBuilder, records the
   // OLD value of every field it's about to change, and commits it (with
   // the NEW value it just wrote) right before sending success -- this
   // replaces whatever the previous action left, so there is only ever
   // one board's worth of undo. RestoreBoardHandler re-checks each
   // field's current value against what this action wrote before
   // touching anything: if something else changed it since (another
   // request, or a hand-edit on the Admin page), that field -- and the
   // whole restore -- is refused rather than guessed at.
   private String _undoDescription = null;
   private List<UndoField> _undoFields = null;

   private static class UndoField {
      final String recordType;
      final String id;
      final String column;
      final String oldValue;
      final String newValue;

      UndoField(String recordType, String id, String column, String oldValue, String newValue) {
         this.recordType = recordType;
         this.id = id;
         this.column = column;
         this.oldValue = oldValue;
         this.newValue = newValue;
      }
   }

   // Built by a handler as it mutates records, then handed to setUndo()
   // once every field's NEW value has actually been written.
   private class UndoBuilder {
      private List<UndoField> _fields = new ArrayList<>();

      // Call AFTER record's setter has already run, so newValue is the
      // value the handler just wrote (not the one it's about to write).
      UndoBuilder field(String recordType, DataRecord record, String column, String oldValue) {
         this._fields.add(new UndoField(recordType, record.getID(), column, oldValue, record.getValue(column)));
         return this;
      }

      boolean isEmpty() {
         return this._fields.isEmpty();
      }

      void commit(String description) {
         EagleBoardScheduler.this._undoDescription = description;
         EagleBoardScheduler.this._undoFields = this._fields;
      }
   }

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
      this._server.addHandler("/restore-board", new EagleBoardScheduler.RestoreBoardHandler());
      this._server.addHandler("/change-board-members", new EagleBoardScheduler.ChangeBoardMembersHandler());
      this._server.addHandler("/rename-room", new EagleBoardScheduler.RenameRoomHandler());
      // The shared check-in pages' calls (eagleboards-shared/checkin).
      new CheckInApi(this.LOCK, this._scoutRecords, this._scoutsScheduledRecords, this._adultRecords,
         this._adultHistoryRecords, this._configRecords).register(this._server);
      this._server.addHandler("/checkin-address", new CheckInAddress.AddressHandler());
      this._server.addHandler("/checkin-qr", new CheckInAddress.QrHandler());
      // Every POST above may change what a page shows; /events tells the
      // pages that are listening (SPEC.md D-15, nothing polls).
      ChangeFeed changeFeed = new ChangeFeed();
      this._server.setChangeFeed(changeFeed);
      this._server.addHandler("/events", changeFeed);
      this._server.addHandler("/adult-autofill", new EagleBoardScheduler.AutoFillHandler<>(this._adultHistoryRecords, "Email"));
      this._server.addHandler("/youth-autofill", new EagleBoardScheduler.AutoFillHandler<>(this._scoutsScheduledRecords, "Email"));
      this._server.addHandler("/config-autofill", new EagleBoardScheduler.AutoFillHandler<>(this._configRecords, "Name"));
   }

   public void run() throws Exception {
      this._server.start();
   }

   // scouts.csv and scouts_scheduled.csv, whose phone numbers are never
   // served (SPEC.md D-8). The read handlers serve the adult files too, and
   // an adult's number still goes out.
   private boolean isYouthFile(DataRecordFile<?> records) {
      return records == this._scoutRecords || records == this._scoutsScheduledRecords;
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
         super(EagleBoardScheduler.this._adultRecords, "Adult");
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
         // Never the birthdate (SPEC.md D-7), nor a youth's phone number (D-8).
         String[] servedColumns = CheckInApi.withoutBirthdate(this._records.getColumns());
         if (EagleBoardScheduler.this.isYouthFile(this._records)) {
            servedColumns = CheckInApi.withoutYouthPhone(servedColumns);
         }
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
                  ((DataRecord)jsonMatches.get(0)).toJSON(out, servedColumns);
               }
            } else {
               contentType = "text/xml";
               out.append("<data>");
               List xmlMatches = this._records.get(this._lookupField, lookupValue);
               if (xmlMatches.size() > 0) {
                  DataRecord xmlRecord = (DataRecord)xmlMatches.get(0);
                  boolean firstColumn = true;

                  for (String column : servedColumns) {
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

   // /change-board-members: swap who sits on a board that is already seated
   // or in review -- someone has to leave, or the chair changes hands. The
   // same composition rules as seating apply, except that the adults already
   // in the room may stay. Those who leave are freed; those who join are put
   // in the room. The timer keeps running (LastUpdateTime is not touched):
   // it is the same board, and the Windows and Mac versions agree.
   public class ChangeBoardMembersHandler extends EagleBoardScheduler.CoreBoardHandler {
      @Override
      public synchronized void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(request);
            EagleBoardScheduler.verbose(request.getParameterMap());
            String scoutId = request.getParameter("ScoutID");
            String chairId = request.getParameter("ChairID");
            String memberIds = request.getParameter("MemberIDs");
            ScoutRecord scout = EagleBoardScheduler.this._scoutRecords.get(scoutId);
            if (scout == null) {
               this.sendError("ERROR: Invalid Scout ID" + scoutId, response);
               return;
            }

            if (!"Seated".equals(scout.getStatus()) && !"InProgress".equals(scout.getStatus())) {
               this.sendError("ERROR: " + scout.getFullName() + " has no board seated or in review to change", response);
               return;
            }

            // By name, or -- for a room renamed under the board -- the card
            // that still names the scout (see CompleteBoardHandler).
            RoomRecord room = null;
            for (RoomRecord candidateRoom : EagleBoardScheduler.this._roomRecords.getRecords()) {
               if (candidateRoom.getRoom().equals(scout.getRoom())) {
                  room = candidateRoom;
                  break;
               }
            }
            if (room == null) {
               for (RoomRecord candidateRoom : EagleBoardScheduler.this._roomRecords.getRecords()) {
                  if (candidateRoom.getScout().length() > 0 && candidateRoom.getScout().equals(scout.getFullName())) {
                     room = candidateRoom;
                     break;
                  }
               }
            }
            if (room == null) {
               this.sendError("ERROR: The room for " + scout.getFullName() + "'s board is gone; Reset the board and seat it again", response);
               return;
            }

            String boardRoom = scout.getRoom();
            BoardComposition board = EagleBoardScheduler.this.checkComposition(scout, chairId, memberIds, boardRoom);
            if (board.error != null) {
               this.sendError(board.error, response);
               return;
            }

            UndoBuilder undo = new UndoBuilder();
            for (AdultRecord adult : EagleBoardScheduler.this._adultRecords.getRecords()) {
               if (adult.getRoom().equals(boardRoom) && !board.members.contains(adult)) {
                  String oldAdultRoom = adult.getRoom();
                  adult.setRoom("");
                  undo.field("Adult", adult, "Room", oldAdultRoom);
               }
            }

            StringBuffer memberIdList = new StringBuffer();
            for (AdultRecord member : board.members) {
               if (!boardRoom.equals(member.getRoom())) {
                  String oldMemberRoom = member.getRoom();
                  member.setRoom(boardRoom);
                  undo.field("Adult", member, "Room", oldMemberRoom);
               }
               if (memberIdList.length() > 0) {
                  memberIdList.append(",");
               }
               memberIdList.append(member.getID());
            }

            String oldRoomLeaders = room.getLeaders();
            String oldBoardMembers = scout.getValue("BoardMembers");
            String oldBoardMemberIDs = scout.getValue("BoardMembersIDs");
            String oldBoardChair = scout.getValue("BoardChair");
            String oldBoardChairID = scout.getValue("BoardChairID");
            room.setLeaders(board.memberNames);
            scout.setBoardMembers(board.memberNames);
            scout.setBoardMemberIDs(memberIdList.toString());
            scout.setBoardChair(board.chair.getFullName());
            scout.setBoardChairID(board.chair.getID());
            undo.field("Room", room, "Leaders", oldRoomLeaders);
            undo.field("Scout", scout, "BoardMembers", oldBoardMembers);
            undo.field("Scout", scout, "BoardMembersIDs", oldBoardMemberIDs);
            undo.field("Scout", scout, "BoardChair", oldBoardChair);
            undo.field("Scout", scout, "BoardChairID", oldBoardChairID);

            // updateFields(false): refresh the derived columns without
            // stamping LastUpdateTime, which would restart the room timer.
            scout.updateFields(false);
            EagleBoardScheduler.this._scoutRecords.store();
            EagleBoardScheduler.this._roomRecords.store();
            EagleBoardScheduler.this._adultRecords.store();
            undo.commit("Change board members for " + scout.getFullName());
            this.sendSuccess(response);
         }
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
                  UndoBuilder undo = new UndoBuilder();

                  if (boardRoom.length() > 0 && !"N/A".equals(boardRoom)) {
                     for (AdultRecord adult : EagleBoardScheduler.this._adultRecords.getRecords()) {
                        if (adult.getRoom().equals(boardRoom)) {
                           String oldAdultRoom = adult.getRoom();
                           adult.setRoom("");
                           undo.field("Adult", adult, "Room", oldAdultRoom);
                        }
                     }
                  }

                  String oldScoutStatus = scout.getStatus();
                  String oldScoutRoom = scout.getRoom();
                  String oldScoutNotes = scout.getValue("Notes");
                  String oldScoutResult = scout.getValue("Result");
                  scout.setStatus("Completed");
                  scout.setRoom("N/A");
                  scout.setNotes(notes);
                  scout.setResult(result);
                  undo.field("Scout", scout, "Status", oldScoutStatus);
                  undo.field("Scout", scout, "Room", oldScoutRoom);
                  undo.field("Scout", scout, "Notes", oldScoutNotes);
                  undo.field("Scout", scout, "Result", oldScoutResult);
                  if (room != null) {
                     String oldRoomScout = room.getScout();
                     String oldRoomLeaders = room.getLeaders();
                     room.setScout("");
                     room.setLeaders("");
                     undo.field("Room", room, "Scout", oldRoomScout);
                     undo.field("Room", room, "Leaders", oldRoomLeaders);
                  }

                  scout.updateFields(true);
                  EagleBoardScheduler.this._scoutRecords.store();
                  EagleBoardScheduler.this._roomRecords.store();
                  EagleBoardScheduler.this._adultRecords.store();
                  undo.commit("Complete board for " + scout.getFullName());
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
            // SPEC.md D-7 / O-5: a birthdate already on file stays there but is
            // never served. The values are read through these copies, where DOB
            // names a column that holds nothing; `columns` keeps the real names
            // for the CSV header, so every column still lines up.
            String[] valueColumns = CheckInApi.withholdBirthdate(columns);
            String[] valueExtraColumns = CheckInApi.withholdBirthdate(extraColumns);
            // SPEC.md D-8: a youth's phone number is kept and withheld the same
            // way. This handler serves the adult files too, and theirs go out.
            boolean youthFile = EagleBoardScheduler.this.isYouthFile(this._records);
            if (youthFile) {
               valueColumns = CheckInApi.withholdYouthPhone(valueColumns);
               valueExtraColumns = CheckInApi.withholdYouthPhone(valueExtraColumns);
            }
            // Nor may a filter on a withheld column tell which rows hold what.
            if (filterColumn != null) {
               String[] filterValueColumn = CheckInApi.withholdBirthdate(new String[]{filterColumn});
               filterColumn = (youthFile ? CheckInApi.withholdYouthPhone(filterValueColumn) : filterValueColumn)[0];
            }
            StringBuffer out = new StringBuffer();
            if (format.equals("data")) {
               contentType = "text/xml";
               out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><data>");

               for (DataRecord record : this._records.getRecords()) {
                  if (filterValues != null && filterColumn != null) {
                     String cellValue = record.get(filterColumn);
                     if (cellValue != null && cellValue.length() > 0 && filterValues.indexOf(cellValue) >= 0) {
                        record.toString(out, valueColumns, valueExtraColumns, format);
                        out.append("\n");
                     }
                  } else {
                     record.toString(out, valueColumns, valueExtraColumns, format);
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
                        csvRecord.toString(out, valueColumns, null, format);
                        out.append("\n");
                     }
                  } else {
                     csvRecord.toString(out, valueColumns, null, format);
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
                        rowRecord.toCells(out, valueColumns, valueExtraColumns);
                        out.append("\n");
                     }
                  } else {
                     rowRecord.toCells(out, valueColumns, valueExtraColumns);
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
      // Non-null only for the handlers O-2 covers (adult field edits --
      // Disable/Enable/Link -- and room rename); null everywhere else
      // (scout/room-admin edits, adult/scout-scheduled history), which
      // just don't offer Undo. See UndoBuilder.
      private String _undoRecordType;

      public DataRecordUpdateHandler(DataRecordFile<T> records) {
         this(records, null);
      }

      public DataRecordUpdateHandler(DataRecordFile<T> records, String undoRecordType) {
         this._records = records;
         this._undoRecordType = undoRecordType;
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

               UndoBuilder undo = new UndoBuilder();
               if ("deleted".equals(status)) {
                  this._records.remove(rowId);
               } else {
                  boolean isEdit = this._undoRecordType != null && !"inserted".equals(status);
                  for (String column : this._records.getColumns()) {
                     String value = request.getParameter(column);
                     EagleBoardScheduler.verbose("CHECKING: " + column + " => " + value);
                     if (value != null) {
                        String oldValue = record.getValue(column);
                        EagleBoardScheduler.verbose("UPDATING: " + column + " => " + value);
                        record.put(column, value);
                        // "Sel" is the operator's in-progress checkbox pick,
                        // saved on every click (including auto-select's own
                        // programmatic checks) so it survives a refresh --
                        // not a reversible action in the O-2 sense, and an
                        // auto-select's Sel writes can land after the seat
                        // they preceded, which would otherwise clobber that
                        // seat's own undo snapshot.
                        if (isEdit && !"Sel".equals(column)) {
                           undo.field(this._undoRecordType, record, column, oldValue);
                        }
                     }
                  }
               }

               record.updateFields(false);
               this._records.store();
               if (this._undoRecordType != null && !"inserted".equals(status) && !"deleted".equals(status) && !undo.isEmpty()) {
                  undo.commit(this._undoRecordType + " " + rowId + " updated");
               }
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
               String oldScoutStatus = scout.getStatus();
               scout.setStatus("InProgress");
               UndoBuilder undo = new UndoBuilder();
               undo.field("Scout", scout, "Status", oldScoutStatus);
               scout.updateFields(true);
               EagleBoardScheduler.this._scoutRecords.store();
               undo.commit("Start review for " + scout.getFullName());
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
            adult.setValue("WoodBadge", "Y".equals(adult.getValue("WoodBadge")) ? "Y" : "");
            adult.setValue("Supporting", adult.getValue("Supporting").trim());
            AdultRecord existing = EagleBoardScheduler.this._adultRecords.get(adult.getID());
            if (existing != null) {
               existing.updateFrom(adult, EagleBoardScheduler.ADULT_REG_FIELDS);
               // Tonight-only answers: the latest sign-in says what is true now.
               existing.setValue("WoodBadge", adult.getValue("WoodBadge"));
               existing.setValue("Supporting", adult.getValue("Supporting"));
               adult = existing;
            } else {
               EagleBoardScheduler.verbose("NEW ADULT RECORD: " + adult);
               EagleBoardScheduler.this._adultRecords.add(adult, false);
            }

            AdultRecord historyRecord = EagleBoardScheduler.this._adultHistoryRecords.get(adult.getID());
            if (historyRecord == null) {
               historyRecord = adult.clone();
               // The history pre-fills next month's form. Whom someone came to
               // support, and whether it counted toward a Wood Badge ticket,
               // are answers for tonight only, so they are not kept.
               historyRecord.setValue("WoodBadge", "");
               historyRecord.setValue("Supporting", "");
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
            // SPEC.md D-7: no birthdate is asked for or kept. An older cached
            // sign-in page may still send one; it is discarded here.
            scout.setValue("DOB", "");
            // SPEC.md D-8: nor a youth's phone number, discarded the same way.
            scout.setValue("Phone", "");
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

   // O-2 (SPEC.md): undoes the last reversible action -- Seat/Start
   // Review/Complete, a room change, or an adult Disable/Enable/Link.
   // Reset and Postpone never set _undoFields (they stay confirm-only),
   // so there's nothing to check for those. Generic "revert last
   // snapshot": refuses per-field if anything else has changed a value
   // since, rather than guessing, and refuses the whole restore if any
   // field fails so a board is never left half-undone.
   public class RestoreBoardHandler extends EagleBoardScheduler.CoreBoardHandler {
      @Override
      public synchronized void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            List<UndoField> fields = EagleBoardScheduler.this._undoFields;
            if (fields == null || fields.isEmpty()) {
               this.sendError("Nothing to undo.", response);
               return;
            }

            Map<String, DataRecord> touched = new HashMap<>();
            for (UndoField f : fields) {
               DataRecordFile<? extends DataRecord> file = EagleBoardScheduler.this.recordsFor(f.recordType);
               DataRecord record = file == null ? null : file.get(f.id);
               if (record == null) {
                  this.sendError("Board changed since -- cannot undo automatically.", response);
                  return;
               }

               // Something else (another request, or a hand-edit on the
               // Admin page) changed this field after this action wrote it:
               // restoring the OLD value now would silently clobber that
               // change, so refuse the whole restore instead.
               String current = record.getValue(f.column);
               if (!java.util.Objects.equals(current, f.newValue)) {
                  this.sendError("Board changed since -- cannot undo automatically.", response);
                  return;
               }

               touched.put(f.recordType + ":" + f.id, record);
            }

            for (UndoField f : fields) {
               DataRecord record = touched.get(f.recordType + ":" + f.id);
               record.setValue(f.column, f.oldValue);
            }

            boolean touchedScout = false;
            boolean touchedRoom = false;
            boolean touchedAdult = false;
            for (String key : touched.keySet()) {
               touchedScout = touchedScout || key.startsWith("Scout:");
               touchedRoom = touchedRoom || key.startsWith("Room:");
               touchedAdult = touchedAdult || key.startsWith("Adult:");
            }
            if (touchedScout) {
               EagleBoardScheduler.this._scoutRecords.store();
            }
            if (touchedRoom) {
               EagleBoardScheduler.this._roomRecords.store();
            }
            if (touchedAdult) {
               EagleBoardScheduler.this._adultRecords.store();
            }

            EagleBoardScheduler.this._undoFields = null;
            EagleBoardScheduler.this._undoDescription = null;
            this.sendSuccess(response);
         }
      }
   }

   private DataRecordFile<? extends DataRecord> recordsFor(String recordType) {
      if ("Scout".equals(recordType)) {
         return this._scoutRecords;
      } else if ("Room".equals(recordType)) {
         return this._roomRecords;
      } else if ("Adult".equals(recordType)) {
         return this._adultRecords;
      }
      return null;
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
                     String room1Name = room1.getRoom();
                     String room2Name = room2.getRoom();
                     room1.setLeaders(leaders2);
                     room2.setLeaders(leaders1);
                     room1.setScout(scout2);
                     room2.setScout(scout1);

                     UndoBuilder undo = new UndoBuilder();
                     undo.field("Room", room1, "Leaders", leaders1);
                     undo.field("Room", room2, "Leaders", leaders2);
                     undo.field("Room", room1, "Scout", scout1);
                     undo.field("Room", room2, "Scout", scout2);

                     for (ScoutRecord scoutMovingTo2 : scoutsInRoom1) {
                        scoutMovingTo2.setRoom(room2Name);
                        undo.field("Scout", scoutMovingTo2, "Room", room1Name);
                     }

                     for (ScoutRecord scoutMovingTo1 : scoutsInRoom2) {
                        scoutMovingTo1.setRoom(room1Name);
                        undo.field("Scout", scoutMovingTo1, "Room", room2Name);
                     }

                     for (AdultRecord adultMovingTo2 : adultsInRoom1) {
                        adultMovingTo2.setRoom(room2Name);
                        undo.field("Adult", adultMovingTo2, "Room", room1Name);
                     }

                     for (AdultRecord adultMovingTo1 : adultsInRoom2) {
                        adultMovingTo1.setRoom(room1Name);
                        undo.field("Adult", adultMovingTo1, "Room", room2Name);
                     }

                     EagleBoardScheduler.this._roomRecords.store();
                     EagleBoardScheduler.this._scoutRecords.store();
                     EagleBoardScheduler.this._adultRecords.store();
                     undo.commit("Change room " + room1Name + " <-> " + room2Name);
                     this.sendSuccess(response);
                  }
               }
            } else {
               this.sendError("ERROR: Invalid Room ID" + roomId2, response);
            }
         }
      }
   }

   // /rename-room: rename a room from its card on the Event page, not only in
   // the Admin tables. The room keeps its ID; the youth and adults in it move
   // to the new name with it, so a board in progress is not stranded looking
   // for a room that no longer matches -- which is what an Admin-table edit of
   // the Room column does (section 13 of the event test). The Windows and
   // Mac versions rename the same way.
   public class RenameRoomHandler extends EagleBoardScheduler.CoreBoardHandler {
      @Override
      public synchronized void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         synchronized (EagleBoardScheduler.this.LOCK) {
            EagleBoardScheduler.verbose(request.getParameterMap());
            String roomId = request.getParameter("RoomID");
            String newName = request.getParameter("Room");
            RoomRecord room = EagleBoardScheduler.this._roomRecords.get(roomId);
            if (room == null) {
               this.sendError("ERROR: No Such Room " + roomId, response);
               return;
            }

            newName = newName == null ? "" : newName.trim();
            if (newName.length() == 0) {
               this.sendError("ERROR: Give the room a name or number", response);
               return;
            }

            // "N/A" is the Disable marker for an adult who has gone home (and what
            // Complete leaves in a youth's Room): a room by that name would
            // make everyone in it look gone. A comma would be written to the CSV
            // as "~" and read back as a different name. The Mac and Windows
            // versions refuse both too.
            if ("N/A".equalsIgnoreCase(newName)) {
               this.sendError("ERROR: N/A marks adults who have gone home; choose another name", response);
               return;
            }
            if (newName.contains(",")) {
               this.sendError("ERROR: A room name cannot contain a comma", response);
               return;
            }

            String oldName = room.getRoom();
            if (newName.equals(oldName)) {
               this.sendSuccess(response);
               return;
            }

            for (RoomRecord other : EagleBoardScheduler.this._roomRecords.getRecords()) {
               if (other != room && newName.equals(other.getRoom())) {
                  this.sendError("ERROR: Room " + newName + " already exists", response);
                  return;
               }
            }

            UndoBuilder undo = new UndoBuilder();
            room.setValue("Room", newName);
            undo.field("Room", room, "Room", oldName);
            for (ScoutRecord scout : EagleBoardScheduler.this._scoutRecords.get("Room", oldName)) {
               scout.setRoom(newName);
               undo.field("Scout", scout, "Room", oldName);
            }
            for (AdultRecord adult : EagleBoardScheduler.this._adultRecords.get("Room", oldName)) {
               adult.setRoom(newName);
               undo.field("Adult", adult, "Room", oldName);
            }

            EagleBoardScheduler.this._roomRecords.store();
            EagleBoardScheduler.this._scoutRecords.store();
            EagleBoardScheduler.this._adultRecords.store();
            undo.commit("Rename room " + oldName + " to " + newName);
            this.sendSuccess(response);
         }
      }
   }

   public class RoomUpdateHandler extends EagleBoardScheduler.DataRecordUpdateHandler<RoomRecord> {
      public RoomUpdateHandler() {
         super(EagleBoardScheduler.this._roomRecords, "Room");
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

   // The board a /seat-board or /change-board-members request asks for,
   // checked against the composition rules. error is set when the request is
   // refused; otherwise members, chair and memberNames describe the board.
   private class BoardComposition {
      String error = null;
      ArrayList<AdultRecord> members = new ArrayList<>();
      AdultRecord chair = null;
      String memberNames = null;

      BoardComposition refuse(String message) {
         this.error = message;
         return this;
      }
   }

   // boardRoom: the room this board already sits in, when changing the
   // members of a seated board -- adults already in it may stay. null when
   // seating a new board, where every member must be free.
   private BoardComposition checkComposition(ScoutRecord scout, String chairId, String memberIds, String boardRoom) {
      BoardComposition composition = new BoardComposition();
      if (memberIds == null || memberIds.trim().length() == 0) {
         // No board members: reject cleanly instead of crashing on
         // StringTokenizer(null) or silently seating an empty board.
         return composition.refuse("ERROR: No board members selected");
      }

      AdultRecord chair = EagleBoardScheduler.this._adultRecords.get(chairId);
      ArrayList<AdultRecord> members = new ArrayList<>();
      StringBuffer memberNames = new StringBuffer();
      StringTokenizer memberIdTokens = new StringTokenizer(memberIds, ",", false);

      while (memberIdTokens.hasMoreTokens()) {
         String memberId = memberIdTokens.nextToken().trim();
         AdultRecord member = EagleBoardScheduler.this._adultRecords.get(memberId);
         if (member == null) {
            return composition.refuse("ERROR: Invalid Member ID " + memberId);
         }

         // "N/A" is the Disable button's marker for an adult who
         // has gone home, not a room anyone can be sent to, so it
         // gets its own wording rather than "in room N/A".
         if ("N/A".equals(member.getRoom())) {
            return composition.refuse("ERROR: Member " + member.getFullName() + " has been disabled for tonight");
         }

         if (member.getRoom().length() > 0 && !member.getRoom().equals(boardRoom)) {
            return composition.refuse("ERROR: Member " + member.getFullName() + " already assigned to a board in room " + member.getRoom());
         }

         // "No thanks" to this kind of board at sign-in is stored as
         // Unavailable for it. Auto-select and the grid skip them, but
         // a hand-picked or hand-built board must not seat them either.
         String memberRole = "Project".equals(scout.getBoardType()) ? member.getProjectReviewRole() : member.getFinalBoardRole();
         if ("Unavailable".equals(memberRole)) {
            return composition.refuse("ERROR: Member " + member.getFullName() + " is Unavailable for " + scout.getBoardType() + " boards");
         }

         // The size rules below count entries, so the same adult
         // listed twice would let two people pass as a board of
         // three. The UI's checkboxes cannot produce this; a
         // hand-built or replayed request can.
         if (members.contains(member)) {
            return composition.refuse("ERROR: Member " + member.getFullName() + " is listed more than once");
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
         return composition.refuse("ERROR: Only " + members.size() + " board member(s) selected; "
            + minMembers + " required for " + scout.getBoardType() + " boards");
      }

      if (members.size() > 6) {
         return composition.refuse("ERROR: " + members.size()
            + " board members selected; no more than 6 permitted (Guide to Advancement 8.0.0.3)");
      }

      // The Chair designation is binding. Promoting someone from
      // Member to Chair is a deliberate edit on the Admin page; it
      // must never happen as a side effect of seating a board
      // because the qualified chairs were all busy.
      if (chair == null) {
         return composition.refuse("ERROR: Invalid Chair ID " + chairId);
      }

      boolean chairIsMember = false;

      for (AdultRecord candidate : members) {
         if (candidate.getID().equals(chair.getID())) {
            chairIsMember = true;
            break;
         }
      }

      if (!chairIsMember) {
         return composition.refuse("ERROR: Chair " + chair.getFullName() + " is not one of the board members");
      }

      String chairRole = isProjectBoard ? chair.getProjectReviewRole() : chair.getFinalBoardRole();

      if (!"Chair".equals(chairRole)) {
         return composition.refuse("ERROR: " + chair.getFullName() + " is not qualified to chair a "
            + scout.getBoardType() + " board (role: "
            + (chairRole == null || chairRole.length() == 0 ? "none" : chairRole) + ")");
      }

      composition.members = members;
      composition.chair = chair;
      composition.memberNames = memberNames.toString();
      return composition;
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
               } else {
                  BoardComposition board = EagleBoardScheduler.this.checkComposition(scout, chairId, memberIds, null);
                  if (board.error != null) {
                     this.sendError(board.error, response);
                     return;
                  }

                  AdultRecord chair = board.chair;
                  ArrayList<AdultRecord> members = board.members;
                  String memberNameList = board.memberNames;
                  String oldRoomScout = room.getScout();
                  String oldRoomLeaders = room.getLeaders();
                  String oldScoutRoom = scout.getRoom();
                  String oldScoutStatus = scout.getStatus();
                  String oldBoardMembers = scout.getValue("BoardMembers");
                  String oldBoardMemberIDs = scout.getValue("BoardMembersIDs");
                  String oldBoardChair = scout.getValue("BoardChair");
                  String oldBoardChairID = scout.getValue("BoardChairID");

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

                  UndoBuilder undo = new UndoBuilder();
                  undo.field("Room", room, "Scout", oldRoomScout);
                  undo.field("Room", room, "Leaders", oldRoomLeaders);
                  undo.field("Scout", scout, "Room", oldScoutRoom);
                  undo.field("Scout", scout, "Status", oldScoutStatus);
                  undo.field("Scout", scout, "BoardMembers", oldBoardMembers);
                  undo.field("Scout", scout, "BoardMembersIDs", oldBoardMemberIDs);
                  undo.field("Scout", scout, "BoardChair", oldBoardChair);
                  undo.field("Scout", scout, "BoardChairID", oldBoardChairID);

                  for (AdultRecord seatedMember : members) {
                     String oldMemberRoom = seatedMember.getRoom();
                     seatedMember.setRoom(room.getRoom());
                     undo.field("Adult", seatedMember, "Room", oldMemberRoom);
                  }

                  scout.updateFields(true);
                  EagleBoardScheduler.this._scoutRecords.store();
                  EagleBoardScheduler.this._roomRecords.store();
                  EagleBoardScheduler.this._adultRecords.store();
                  undo.commit("Seat board for " + scout.getFullName());
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
