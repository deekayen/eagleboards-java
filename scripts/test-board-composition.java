// ------------------------------------------------------------------------
// test-board-composition.java: unit tests for the board rules, with no
// server in the way.
//
// Seat Board and Change Board Members both ask BoardCompositionChecker
// whether a requested board is legal (size, chair, one board at a time,
// "No thanks" answers, duplicates). That check needs only the adults file,
// so it can be tested directly against records built in a temporary folder.
// The same run covers the small pieces the handlers lean on: the single
// level of Undo (UndoLog), ScoutStatus.holdsRoom, and EventData's lookup of
// the room a board sits in and its release of the board's adults.
//
// No network, no running server, and no test framework: each check prints
// FAIL and the script exits non-zero if any of them fails. Run it against
// the built jar (Java's single-file source launch):
//
//   java -cp target/eagleboardscheduler-<version>.jar scripts/test-board-composition.java
//
// Runs on all four CI platforms (see .github/workflows/build.yml).
// ------------------------------------------------------------------------

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import shkc.core.AdultRecord;
import shkc.core.BoardComposition;
import shkc.core.BoardCompositionChecker;
import shkc.core.ConfigRecord;
import shkc.core.DataRecordFile;
import shkc.core.EventData;
import shkc.core.RoomRecord;
import shkc.core.ScoutRecord;
import shkc.core.ScoutStatus;
import shkc.core.UndoField;
import shkc.core.UndoLog;

public class TestBoardComposition {
   private static int failures = 0;

   private static DataRecordFile<ScoutRecord> scouts;
   private static DataRecordFile<ScoutRecord> scoutsScheduled;
   private static DataRecordFile<AdultRecord> adults;
   private static DataRecordFile<AdultRecord> adultHistory;
   private static DataRecordFile<RoomRecord> rooms;
   private static DataRecordFile<ConfigRecord> config;

   public static void main(String[] args) throws IOException {
      Path folder = Files.createTempDirectory("eb-board-composition");
      try {
         openFiles(folder);
         testBoardCompositionChecker();
         testUndoLog();
         testScoutStatus();
         testEventData(folder);
      } finally {
         deleteTree(folder);
      }

      if (failures > 0) {
         System.out.println(failures + " board composition check(s) failed");
         System.exit(1);
      }
      System.out.println("PASS: board composition checks");
   }

   // ---- BoardCompositionChecker ------------------------------------------

   private static void testBoardCompositionChecker() {
      BoardCompositionChecker checker = new BoardCompositionChecker(adults);

      ScoutRecord finalScout = newScout("Sam", "Scout", "Final");
      ScoutRecord projectScout = newScout("Pat", "Proposal", "Project");

      AdultRecord chair = newAdult("Chris", "Chair", "Chair", "Chair");
      AdultRecord memberA = newAdult("Alex", "Able", "Member", "Member");
      AdultRecord memberB = newAdult("Bo", "Baker", "Member", "Member");
      AdultRecord memberC = newAdult("Cam", "Clark", "Member", "Member");
      AdultRecord memberD = newAdult("Dana", "Dunn", "Member", "Member");
      AdultRecord memberE = newAdult("Eli", "Estes", "Member", "Member");
      AdultRecord memberF = newAdult("Fay", "Fox", "Member", "Member");

      // The accepted case: a legal board of four, the chair among them.
      String legalBoard = ids(chair, memberA, memberB, memberC);
      BoardComposition accepted = checker.check(finalScout, chair.getID(), legalBoard, null);
      check("legal board is accepted", !accepted.isRefused());
      check("accepted board has no error", accepted.getError() == null);
      check("accepted board lists four members", accepted.getMembers().size() == 4);
      check("accepted board names its chair", accepted.getChair() == chair);
      check("accepted board names members by full name, comma separated, in order",
         "Chris Chair,Alex Able,Bo Baker,Cam Clark".equals(accepted.getMemberNames()));

      // Spaces around the ids in the request are ignored.
      BoardComposition spaced = checker.check(finalScout, chair.getID(),
         " " + chair.getID() + " , " + memberA.getID() + " ," + memberB.getID(), null);
      check("ids with spaces around them are accepted", !spaced.isRefused());

      // No members at all.
      checkRefusal("no members: null", checker.check(finalScout, chair.getID(), null, null),
         "ERROR: No board members selected");
      checkRefusal("no members: blank", checker.check(finalScout, chair.getID(), "   ", null),
         "ERROR: No board members selected");

      // A member id that is not in the adults file.
      checkRefusal("unknown member id",
         checker.check(finalScout, chair.getID(), chair.getID() + ",ADULT:Nobody:No:1", null),
         "ERROR: Invalid Member ID ADULT:Nobody:No:1");

      // A member already committed to another board.
      AdultRecord busy = newAdult("Bea", "Busy", "Member", "Member");
      busy.setRoom("Room 5");
      String withBusy = ids(chair, memberA, busy);
      checkRefusal("member in another room",
         checker.check(finalScout, chair.getID(), withBusy, null),
         "ERROR: Member Bea Busy already assigned to a board in room Room 5");
      checkRefusal("member in another room, different board room named",
         checker.check(finalScout, chair.getID(), withBusy, "Room 9"),
         "ERROR: Member Bea Busy already assigned to a board in room Room 5");
      check("member already in this board's own room may stay",
         !checker.check(finalScout, chair.getID(), withBusy, "Room 5").isRefused());

      // The Disable button's marker has its own wording.
      AdultRecord gone = newAdult("Gus", "Gone", "Member", "Member");
      gone.setRoom("N/A");
      checkRefusal("disabled member",
         checker.check(finalScout, chair.getID(), ids(chair, memberA, gone), null),
         "ERROR: Member Gus Gone has been disabled for today");
      checkRefusal("disabled member is refused even in a board room",
         checker.check(finalScout, chair.getID(), ids(chair, memberA, gone), "N/A"),
         "ERROR: Member Gus Gone has been disabled for today");

      // "No thanks" at sign-in, for one kind of board only.
      AdultRecord noFinal = newAdult("Nan", "Nofinal", "Member", "Unavailable");
      checkRefusal("unavailable for final boards",
         checker.check(finalScout, chair.getID(), ids(chair, memberA, noFinal), null),
         "ERROR: Member Nan Nofinal is Unavailable for Final boards");
      check("unavailable for final boards but fine on a project proposal",
         !checker.check(projectScout, chair.getID(), ids(chair, noFinal), null).isRefused());

      AdultRecord noProject = newAdult("Pia", "Noproject", "Unavailable", "Member");
      checkRefusal("unavailable for project proposals",
         checker.check(projectScout, chair.getID(), ids(chair, noProject), null),
         "ERROR: Member Pia Noproject is Unavailable for Project boards");
      check("unavailable for project proposals but fine on a final board",
         !checker.check(finalScout, chair.getID(), ids(chair, memberA, noProject), null).isRefused());

      // The same adult listed twice must not count as two people.
      checkRefusal("duplicate member",
         checker.check(finalScout, chair.getID(), ids(chair, memberA, memberA), null),
         "ERROR: Member Alex Able is listed more than once");

      // Board size: a final board needs three, a project proposal two.
      checkRefusal("final board below the minimum of three",
         checker.check(finalScout, chair.getID(), ids(chair, memberA), null),
         "ERROR: Only 2 board member(s) selected; 3 required for Final boards");
      checkRefusal("final board of one",
         checker.check(finalScout, chair.getID(), ids(chair), null),
         "ERROR: Only 1 board member(s) selected; 3 required for Final boards");
      check("final board of exactly three is accepted",
         !checker.check(finalScout, chair.getID(), ids(chair, memberA, memberB), null).isRefused());
      checkRefusal("project proposal below the minimum of two",
         checker.check(projectScout, chair.getID(), ids(chair), null),
         "ERROR: Only 1 board member(s) selected; 2 required for Project boards");
      check("project proposal of exactly two is accepted",
         !checker.check(projectScout, chair.getID(), ids(chair, memberA), null).isRefused());

      // No more than six on any board.
      check("board of exactly six is accepted",
         !checker.check(finalScout, chair.getID(),
            ids(chair, memberA, memberB, memberC, memberD, memberE), null).isRefused());
      String sevenMembers = ids(chair, memberA, memberB, memberC, memberD, memberE, memberF);
      checkRefusal("final board above the maximum of six",
         checker.check(finalScout, chair.getID(), sevenMembers, null),
         "ERROR: 7 board members selected; no more than 6 permitted (Guide to Advancement 8.0.0.3)");
      checkRefusal("project proposal above the maximum of six",
         checker.check(projectScout, chair.getID(), sevenMembers, null),
         "ERROR: 7 board members selected; no more than 6 permitted (Guide to Advancement 8.0.0.3)");

      // The chair.
      checkRefusal("unknown chair id",
         checker.check(finalScout, "ADULT:Nobody:No:1", legalBoard, null),
         "ERROR: Invalid Chair ID ADULT:Nobody:No:1");
      checkRefusal("null chair id",
         checker.check(finalScout, null, legalBoard, null),
         "ERROR: Invalid Chair ID null");

      AdultRecord outsideChair = newAdult("Olga", "Outside", "Chair", "Chair");
      checkRefusal("chair not on the board",
         checker.check(finalScout, outsideChair.getID(), legalBoard, null),
         "ERROR: Chair Olga Outside is not one of the board members");

      checkRefusal("chair who is only a member",
         checker.check(finalScout, memberA.getID(), legalBoard, null),
         "ERROR: Alex Able is not qualified to chair a Final board (role: Member)");

      AdultRecord projectOnlyChair = newAdult("Perry", "Projectchair", "Chair", "Member");
      checkRefusal("chair for project proposals only, on a final board",
         checker.check(finalScout, projectOnlyChair.getID(), ids(projectOnlyChair, memberA, memberB), null),
         "ERROR: Perry Projectchair is not qualified to chair a Final board (role: Member)");
      check("chair for project proposals only, on a project proposal",
         !checker.check(projectScout, projectOnlyChair.getID(), ids(projectOnlyChair, memberA), null).isRefused());

      AdultRecord blankRole = newAdult("Ben", "Blank", "", "");
      checkRefusal("chair with no role at all",
         checker.check(finalScout, blankRole.getID(), ids(blankRole, memberA, memberB), null),
         "ERROR: Ben Blank is not qualified to chair a Final board (role: none)");

      // The order the checks run in: a member problem is reported before a size problem.
      checkRefusal("member problems come before size problems",
         checker.check(finalScout, chair.getID(), chair.getID() + ",ADULT:Nobody:No:1", null),
         "ERROR: Invalid Member ID ADULT:Nobody:No:1");
      checkRefusal("size problems come before chair problems",
         checker.check(finalScout, "ADULT:Nobody:No:1", ids(memberA), null),
         "ERROR: Only 1 board member(s) selected; 3 required for Final boards");

      // A refused board carries no members.
      BoardComposition refused = checker.check(finalScout, chair.getID(), null, null);
      check("refused board has no members", refused.getMembers().isEmpty());
      check("refused board has no chair", refused.getChair() == null);

      // The national floor (Guide to Advancement 8.0.3.0 #2, SPEC.md D-4): one
      // member must be from outside the youth's unit. The council's same-unit
      // rule is only a warning, but this is refused whoever asks, and the
      // Event page's hasNonUnitMember (process_seat.js) says the same.
      String seatingRefusal = "ERROR: Everyone on the board is from the youth's unit, and a board needs "
         + "at least one member from outside it (Guide to Advancement 8.0.3.0). Add someone from another unit.";
      String changingRefusal = "ERROR: Everyone left on the board is from the youth's unit, and a board needs "
         + "at least one member from outside it (Guide to Advancement 8.0.3.0). Add someone from another unit.";

      ScoutRecord unitScout = newScout("Ulysses", "Unitscout", "Final", "30");
      ScoutRecord unitProject = newScout("Una", "Unitproject", "Project", "30");
      AdultRecord unitChair = newAdult("Pia", "Samechair", "Chair", "Chair", "30");
      AdultRecord unitA = newAdult("Sal", "Samea", "Member", "Member", "30");
      AdultRecord unitB = newAdult("Sue", "Sameb", "Member", "Member", "30");
      AdultRecord outsider = newAdult("Olaf", "Outsider", "Member", "Member", "31");
      AdultRecord otherUnitChair = newAdult("Cleo", "Outchair", "Chair", "Chair", "31");
      AdultRecord longerUnit = newAdult("Lou", "Longerunit", "Member", "Member", "300");
      AdultRecord noUnit = newAdult("Nia", "Nounit", "Member", "Member", null);
      String ownUnit = ids(unitChair, unitA, unitB);

      checkRefusal("a board of only the youth's own unit is refused",
         checker.check(unitScout, unitChair.getID(), ownUnit, null), seatingRefusal);
      checkRefusal("the same board is refused when its members are being changed",
         checker.check(unitScout, unitChair.getID(), ownUnit, "Room 30"), changingRefusal);
      checkRefusal("a project proposal of only the youth's own unit is refused too",
         checker.check(unitProject, unitChair.getID(), ids(unitChair, unitA), null), seatingRefusal);
      check("one member from another unit is enough",
         !checker.check(unitScout, unitChair.getID(), ids(unitChair, unitA, outsider), null).isRefused());
      check("one outside member is enough when changing members",
         !checker.check(unitScout, unitChair.getID(), ids(unitChair, unitA, outsider), "Room 30").isRefused());
      check("an outside chair is enough",
         !checker.check(unitScout, otherUnitChair.getID(), ids(otherUnitChair, unitA, unitB), null).isRefused());
      check("a unit that only starts the same way is another unit (Troop300 is not Troop30)",
         !checker.check(unitScout, unitChair.getID(), ids(unitChair, unitA, longerUnit), null).isRefused());
      check("a member with no unit counts as outside",
         !checker.check(unitScout, unitChair.getID(), ids(unitChair, unitA, noUnit), null).isRefused());

      ScoutRecord noUnitScout = newScout("Nils", "Noscoutunit", "Final", null);
      check("a youth with no unit has no unit to be refused against",
         !checker.check(noUnitScout, unitChair.getID(), ownUnit, null).isRefused());

      // The order the checks run in: size and chair problems are reported first.
      checkRefusal("size problems come before the unit rule",
         checker.check(unitScout, unitChair.getID(), ids(unitChair, unitA), null),
         "ERROR: Only 2 board member(s) selected; 3 required for Final boards");
      checkRefusal("chair problems come before the unit rule",
         checker.check(unitScout, unitA.getID(), ownUnit, null),
         "ERROR: Sal Samea is not qualified to chair a Final board (role: Member)");

      // The published limits.
      check("minimum for a board of review is 3", BoardCompositionChecker.MIN_BOARD_OF_REVIEW_MEMBERS == 3);
      check("minimum for a project proposal is 2", BoardCompositionChecker.MIN_PROJECT_PROPOSAL_MEMBERS == 2);
      check("maximum for any board is 6", BoardCompositionChecker.MAX_BOARD_MEMBERS == 6);
   }

   // ---- UndoLog ----------------------------------------------------------

   private static void testUndoLog() {
      UndoLog log = new UndoLog();
      check("new undo log is empty", log.isEmpty());
      check("new undo log has no fields", log.fields() == null);
      check("new undo log has no description", log.description() == null);

      ScoutRecord scout = newScout("Una", "Undo", "Final");
      scout.setStatus("Registered");
      String oldStatus = scout.getStatus();
      scout.setStatus("Seated");

      UndoLog.Builder first = log.begin();
      check("a builder with nothing recorded is empty", first.isEmpty());
      first.field("Scout", scout, "Status", oldStatus);
      check("a builder with a field is not empty", !first.isEmpty());
      check("nothing is kept until commit", log.isEmpty());

      first.commit("Seat board for Una Undo");
      check("committed log is not empty", !log.isEmpty());
      check("committed log has the description", "Seat board for Una Undo".equals(log.description()));
      check("committed log has one field", log.fields().size() == 1);
      UndoField field = log.fields().get(0);
      check("field remembers the record type", "Scout".equals(field.recordType()));
      check("field remembers the record id", scout.getID().equals(field.id()));
      check("field remembers the column", "Status".equals(field.column()));
      check("field remembers the old value", "Registered".equals(field.oldValue()));
      check("field reads the new value when recorded", "Seated".equals(field.newValue()));

      // A second action replaces the first: there is one level of Undo.
      AdultRecord adult = newAdult("Ada", "Adult", "Member", "Member");
      adult.setRoom("Room 7");
      UndoLog.Builder second = log.begin();
      second.field("Adult", adult, "Room", "");
      second.commit("Second action");
      check("commit replaces the previous action's description", "Second action".equals(log.description()));
      check("commit replaces the previous action's fields", log.fields().size() == 1
         && "Adult".equals(log.fields().get(0).recordType()));
      check("recorded new value is what the setter wrote", "Room 7".equals(log.fields().get(0).newValue()));

      // A builder that is begun but never committed leaves the log alone.
      log.begin().field("Adult", adult, "Room", "x");
      check("an uncommitted builder changes nothing", "Second action".equals(log.description()));

      // Committing a builder with no fields leaves nothing to undo.
      log.begin().commit("Nothing happened");
      check("committing no fields leaves the log empty", log.isEmpty());

      log.begin().field("Adult", adult, "Room", "").commit("Third action");
      check("log has something again", !log.isEmpty());
      log.clear();
      check("clear empties the log", log.isEmpty());
      check("clear forgets the fields", log.fields() == null);
      check("clear forgets the description", log.description() == null);
   }

   // ---- ScoutStatus ------------------------------------------------------

   private static void testScoutStatus() {
      check("Seated holds a room", ScoutStatus.holdsRoom(ScoutStatus.SEATED));
      check("InProgress holds a room", ScoutStatus.holdsRoom(ScoutStatus.IN_PROGRESS));
      check("Registered holds no room", !ScoutStatus.holdsRoom(ScoutStatus.REGISTERED));
      check("Verified holds no room", !ScoutStatus.holdsRoom(ScoutStatus.VERIFIED));
      check("Completed holds no room", !ScoutStatus.holdsRoom(ScoutStatus.COMPLETED));
      check("Postponed holds no room", !ScoutStatus.holdsRoom(ScoutStatus.POSTPONED));
      check("an empty status holds no room", !ScoutStatus.holdsRoom(""));
      check("a null status holds no room", !ScoutStatus.holdsRoom(null));

      // These words are written to the CSV files and read by the browser pages.
      check("status words match the files",
         "Registered".equals(ScoutStatus.REGISTERED) && "Seated".equals(ScoutStatus.SEATED)
         && "InProgress".equals(ScoutStatus.IN_PROGRESS) && "Completed".equals(ScoutStatus.COMPLETED)
         && "Postponed".equals(ScoutStatus.POSTPONED));
   }

   // ---- EventData --------------------------------------------------------

   private static void testEventData(Path folder) {
      EventData data = new EventData(folder.toFile(), scouts, scoutsScheduled, adults, adultHistory, rooms, config);

      check("event data hands back its data folder", data.getDataRoot().equals(folder.toFile()));
      check("event data hands back the scouts file", data.getScouts() == scouts);
      check("event data has its own undo log", data.getUndoLog() != null && data.getUndoLog().isEmpty());
      check("event data has a lock", data.getLock() != null);

      check("scouts.csv is a youth file", data.isYouthFile(scouts));
      check("scouts_scheduled.csv is a youth file", data.isYouthFile(scoutsScheduled));
      check("adults.csv is not a youth file", !data.isYouthFile(adults));
      check("rooms.csv is not a youth file", !data.isYouthFile(rooms));

      check("recordsFor Scout", data.recordsFor("Scout") == scouts);
      check("recordsFor Room", data.recordsFor("Room") == rooms);
      check("recordsFor Adult", data.recordsFor("Adult") == adults);
      check("recordsFor anything else", data.recordsFor("Config") == null);

      // findRoomOfBoard: by name first.
      RoomRecord roomOne = newRoom("Room 1", "");
      RoomRecord roomTwo = newRoom("Room 2", "Rita Renamed");
      ScoutRecord byName = newScout("Nina", "Named", "Final");
      byName.setRoom("Room 1");
      check("room found by the name the scout holds", data.findRoomOfBoard(byName) == roomOne);

      // A room renamed under the board: its record still names the scout.
      ScoutRecord afterRename = newScout("Rita", "Renamed", "Final");
      afterRename.setRoom("The old room name");
      check("room found by scout name after a rename", data.findRoomOfBoard(afterRename) == roomTwo);
      check("a room's name is tried before its scout", data.findRoomOfBoard(byName) == roomOne);

      // No room at all.
      ScoutRecord nobody = newScout("Nora", "Nowhere", "Final");
      nobody.setRoom("N/A");
      check("no room matches a finished scout", data.findRoomOfBoard(nobody) == null);
      ScoutRecord blankRoom = newScout("Bill", "Blank", "Final");
      check("no room matches a scout with no room and no card naming them", data.findRoomOfBoard(blankRoom) == null);

      // releaseAdultsFromRoom.
      AdultRecord inRoomA = newAdult("Rae", "Roomone", "Member", "Member");
      inRoomA.setRoom("Room 1");
      AdultRecord inRoomB = newAdult("Ray", "Roomone", "Member", "Member");
      inRoomB.setRoom("Room 1");
      AdultRecord elsewhere = newAdult("Eve", "Elsewhere", "Member", "Member");
      elsewhere.setRoom("Room 2");
      AdultRecord goneHome = newAdult("Hal", "Home", "Member", "Member");
      goneHome.setRoom("N/A");

      UndoLog log = new UndoLog();
      UndoLog.Builder undo = log.begin();
      data.releaseAdultsFromRoom("Room 1", undo);
      check("adults in the board's room are freed", inRoomA.getRoom().isEmpty() && inRoomB.getRoom().isEmpty());
      check("adults in another room stay put", "Room 2".equals(elsewhere.getRoom()));
      check("adults who have gone home stay gone", "N/A".equals(goneHome.getRoom()));
      check("each freed adult is recorded for undo", undo.isEmpty() == false);
      undo.commit("Complete");
      List<UndoField> freed = log.fields();
      check("exactly the two freed adults are recorded", freed.size() == 2);
      check("a freed adult's old room is recorded", "Room 1".equals(freed.get(0).oldValue()) && "".equals(freed.get(0).newValue()));

      // An empty room name or the marker releases nobody, with or without an undo builder.
      data.releaseAdultsFromRoom("", null);
      data.releaseAdultsFromRoom("N/A", null);
      check("an empty room name releases nobody", "Room 2".equals(elsewhere.getRoom()));
      check("the N/A marker releases nobody", "N/A".equals(goneHome.getRoom()));

      // A null builder means the action offers no Undo (Reset) but still frees the adults.
      data.releaseAdultsFromRoom("Room 2", null);
      check("a null undo builder still frees the adults", elsewhere.getRoom().isEmpty());
   }

   // ---- Building records in memory ---------------------------------------

   /** Opens the six record files, each one a new empty file inside the temporary folder. */
   private static void openFiles(Path folder) throws IOException {
      scouts = new DataRecordFile<>(new File(folder.toFile(), "scouts.csv"), new ScoutRecord.Factory());
      scoutsScheduled = new DataRecordFile<>(new File(folder.toFile(), "scouts_scheduled.csv"), new ScoutRecord.Factory());
      adults = new DataRecordFile<>(new File(folder.toFile(), "adults.csv"), new AdultRecord.Factory());
      adultHistory = new DataRecordFile<>(new File(folder.toFile(), "adult_history.csv"), new AdultRecord.Factory());
      rooms = new DataRecordFile<>(new File(folder.toFile(), "rooms.csv"), new RoomRecord.Factory());
      config = new DataRecordFile<>(new File(folder.toFile(), "config.properties"), new ConfigRecord.Factory());
   }

   /** Adds a youth to scouts.csv. The ID becomes SCOUT:last:first:unit. */
   private static ScoutRecord newScout(String first, String last, String boardType) {
      return newScout(first, last, boardType, "1");
   }

   /** Adds a youth in Troop {@code unit}; a null unit leaves the unit blank, so {@code UnitName} is blank. */
   private static ScoutRecord newScout(String first, String last, String boardType, String unit) {
      Map<String, String> values = new HashMap<>();
      values.put("First", first);
      values.put("Last", last);
      if (unit != null) {
         values.put("Unit", unit);
         values.put("UnitType", "Troop");
      }
      values.put("BoardType", boardType);
      ScoutRecord scout = new ScoutRecord(values);
      scouts.add(scout, false);
      return scout;
   }

   /**
    * Adds an adult to adults.csv.
    *
    * @param projectRole their sign-in answer for a project proposal ("Member", "Chair", "Unavailable" or "")
    * @param finalRole   their sign-in answer for a board of review
    */
   private static AdultRecord newAdult(String first, String last, String projectRole, String finalRole) {
      return newAdult(first, last, projectRole, finalRole, "2");
   }

   /** Adds an adult in Troop {@code unit}; a null unit leaves the unit blank, so {@code UnitName} is blank. */
   private static AdultRecord newAdult(String first, String last, String projectRole, String finalRole, String unit) {
      Map<String, String> values = new HashMap<>();
      values.put("First", first);
      values.put("Last", last);
      if (unit != null) {
         values.put("Unit", unit);
         values.put("UnitType", "Troop");
      }
      values.put("ProjectReview", projectRole);
      values.put("FinalBoard", finalRole);
      AdultRecord adult = new AdultRecord(values);
      adults.add(adult, false);
      return adult;
   }

   /** Adds a room card to rooms.csv; {@code scoutName} is the youth it names, or empty. */
   private static RoomRecord newRoom(String roomName, String scoutName) {
      Map<String, String> values = new HashMap<>();
      values.put("Room", roomName);
      values.put("Scout", scoutName);
      RoomRecord room = new RoomRecord(values);
      rooms.add(room, false);
      return room;
   }

   /** The adults' IDs as a request's comma-separated member list. */
   private static String ids(AdultRecord... people) {
      StringBuilder list = new StringBuilder();
      for (AdultRecord person : people) {
         if (list.length() > 0) {
            list.append(",");
         }
         list.append(person.getID());
      }
      return list.toString();
   }

   // ---- Checking ---------------------------------------------------------

   private static void check(String name, boolean condition) {
      if (!condition) {
         failures++;
         System.out.println("FAIL: " + name);
      }
   }

   private static void checkRefusal(String name, BoardComposition board, String expectedMessage) {
      if (!board.isRefused()) {
         failures++;
         System.out.println("FAIL: " + name + ": expected a refusal, but the board was accepted");
      } else if (!expectedMessage.equals(board.getError())) {
         failures++;
         System.out.println("FAIL: " + name + ": expected \"" + expectedMessage + "\", got \"" + board.getError() + "\"");
      }
   }

   private static void deleteTree(Path folder) throws IOException {
      try (Stream<Path> paths = Files.walk(folder)) {
         paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
      }
   }
}
