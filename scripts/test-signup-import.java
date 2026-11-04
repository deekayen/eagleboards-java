// ------------------------------------------------------------------------
// test-signup-import.java: checks of what the SignUpGenius import makes of a
// report, and of the two on-disk file formats it saves through.
//
// The import used to be one class that fetched the report over the network
// and turned it into records in the same breath, so the second half could not
// be tried without a live SignUpGenius key. It is split now: SignUpGeniusClient
// fetches, and SignUpGeniusPlugin.importReport turns a report into records.
// This hands importReport a canned report and looks at the files that come out.
//
// There is no network, no running server, and no test framework. Each check
// prints FAIL, and the script exits non-zero if any of them fails. Run it
// against the built jar (Java's single-file source launch):
//
//   java -cp target/eagleboardscheduler-<version>.jar scripts/test-signup-import.java
//
// Runs on all four CI platforms (see .github/workflows/build.yml).
// ------------------------------------------------------------------------

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import shkc.core.AdultRecord;
import shkc.core.ConfigRecord;
import shkc.core.DataRecordFile;
import shkc.core.ScoutRecord;
import shkc.core.SignUpGeniusPlugin;

public class TestSignUpImport {
   private static int failures = 0;
   private static int checks = 0;

   /** One slot of a report: who, what for, when, and the three custom answers (unit, phone, leader). */
   private static String slot(String first, String last, String item, String email, String start, String unit, String phone, String leader) {
      return "{\"firstname\":\"" + first + "\",\"lastname\":\"" + last + "\",\"item\":\"" + item + "\",\"email\":\"" + email
         + "\",\"startdatestring\":\"" + start + "\",\"customfields\":[{\"value\":\"" + unit + "\"},{\"value\":\"" + phone
         + "\"},{\"value\":\"" + leader + "\"}]}";
   }

   public static void main(String[] args) throws Exception {
      Path scratch = Files.createTempDirectory("signup-import");
      try {
         checkImport(scratch);
         checkMissingAnswers(scratch);
         checkFileFormats(scratch);
      } finally {
         deleteTree(scratch);
      }

      if (failures > 0) {
         System.out.println("FAILED: " + failures + " of " + checks + " checks");
         System.exit(1);
      }
      System.out.println("PASS: sign-up import and file format checks (" + checks + ")");
   }

   private static void checkImport(Path scratch) throws Exception {
      File historyFile = scratch.resolve("history.csv").toFile();
      File scheduledFile = scratch.resolve("scheduled.csv").toFile();
      DataRecordFile<AdultRecord> history = new DataRecordFile<>(historyFile, new AdultRecord.Factory());
      DataRecordFile<ScoutRecord> scheduled = new DataRecordFile<>(scheduledFile, new ScoutRecord.Factory());

      // An adult who is already known, with an old phone number.
      AdultRecord known = history.addNew("ADULT:Known:Kay:1", false);
      known.setValue("Email", "kay@example.org");
      known.setValue("Phone", "111-111-1111");
      history.store();
      // What the import must leave alone: everything but the phone.
      String knownUnitBefore = known.getValue("Unit");
      String knownTypeBefore = known.getValue("UnitType");
      String knownProjectBefore = known.getValue("ProjectReview");
      String knownFinalBefore = known.getValue("FinalBoard");

      String report = "{\"data\":{\"signup\":["
         + slot("amy", "adult", "Adult volunteer", "amy@example.org", "2026-11-14 09:00:00", "Troop 12", "(555) 123-4567", "")
         + "," + slot("kay", "known", "Adult volunteer", "kay@example.org", "2026-11-14 09:00:00", "Troop 7", "5559876543", "")
         + "," + slot("ned", "scout", "Eagle Board of Review", "ned@example.org", "2026-11-14 10:00:00", "Troop 99", "555-000-1111", "Sam Master")
         + "," + slot("pia", "proposal", "Project proposal", "pia@example.org", "2026-11-14 10:30:00", "Pack 5", "", "")
         + "," + slot("ned", "again", "Eagle Board of Review", "ned@example.org", "2026-11-14 11:00:00", "Troop 99", "", "")
         + "," + slot("old", "month", "Eagle Board of Review", "old@example.org", "2026-10-10 10:00:00", "Troop 1", "", "")
         + "]}}";
      JsonNode parsed = new ObjectMapper().readTree(report);

      new SignUpGeniusPlugin("test-key", "1", history, scheduled).importReport(parsed, "2026-11");

      // Read back from disk, not from memory: what matters is what was saved.
      DataRecordFile<AdultRecord> savedHistory = new DataRecordFile<>(historyFile, new AdultRecord.Factory());
      DataRecordFile<ScoutRecord> savedScheduled = new DataRecordFile<>(scheduledFile, new ScoutRecord.Factory());

      check("adults: a new volunteer is added, the known one is not duplicated", savedHistory.getRecords().size() == 2);
      List<AdultRecord> amy = savedHistory.get("Email", "amy@example.org");
      check("adult: the new volunteer's phone is dashed from its digits",
         amy.size() == 1 && "555-123-4567".equals(amy.get(0).getValue("Phone")));
      check("adult: a volunteer comes for either kind of board, as a member",
         amy.size() == 1 && "Member".equals(amy.get(0).getValue("ProjectReview")) && "Member".equals(amy.get(0).getValue("FinalBoard")));
      check("adult: the unit is read from the answer", amy.size() == 1 && "12".equals(amy.get(0).getValue("Unit")) && "Troop".equals(amy.get(0).getValue("UnitType")));
      List<AdultRecord> kay = savedHistory.get("Email", "kay@example.org");
      check("adult: someone already known has only their phone refreshed",
         kay.size() == 1 && "555-987-6543".equals(kay.get(0).getValue("Phone")) && "ADULT:Known:Kay:1".equals(kay.get(0).getID())
            && knownUnitBefore.equals(kay.get(0).getValue("Unit")) && knownTypeBefore.equals(kay.get(0).getValue("UnitType"))
            && knownProjectBefore.equals(kay.get(0).getValue("ProjectReview")) && knownFinalBefore.equals(kay.get(0).getValue("FinalBoard")));

      check("youth: two scouts are added, the repeat email and the other month are not", savedScheduled.getRecords().size() == 2);
      List<ScoutRecord> ned = savedScheduled.get("Email", "ned@example.org");
      check("youth: a board of review is a Final board for a Troop 99 scout",
         ned.size() == 1 && "Final".equals(ned.get(0).getValue("BoardType")) && "99".equals(ned.get(0).getValue("Unit")));
      check("youth: the leader's name is kept", ned.size() == 1 && "Sam Master".equals(ned.get(0).getValue("Leader")));
      check("youth: no phone number is kept (SPEC.md D-8)", ned.size() == 1 && ned.get(0).getValue("Phone").isEmpty());
      check("youth: a project proposal is a Project board and a Pack is read from the unit",
         savedScheduled.get("Email", "pia@example.org").size() == 1
            && "Project".equals(savedScheduled.get("Email", "pia@example.org").get(0).getValue("BoardType"))
            && "Pack".equals(savedScheduled.get("Email", "pia@example.org").get(0).getValue("UnitType")));
      check("youth: a slot from another month is skipped", savedScheduled.get("Email", "old@example.org").isEmpty());
   }

   /** A slot whose custom questions went unanswered must still import, with blanks. */
   private static void checkMissingAnswers(Path scratch) throws Exception {
      File scheduledFile = scratch.resolve("scheduled-blank.csv").toFile();
      DataRecordFile<ScoutRecord> scheduled = new DataRecordFile<>(scheduledFile, new ScoutRecord.Factory());
      String report = "{\"data\":{\"signup\":[{\"firstname\":\"sue\",\"lastname\":\"blank\",\"item\":\"Eagle Board of Review\","
         + "\"email\":\"sue@example.org\",\"startdatestring\":\"2026-11-14 10:00:00\",\"customfields\":[]}]}}";

      boolean imported = true;
      try {
         // No adult history at all: adults are skipped, youth still come in.
         new SignUpGeniusPlugin("test-key", "1", null, scheduled).importReport(new ObjectMapper().readTree(report), "2026-11");
      } catch (Throwable unexpected) {
         imported = false;
         System.out.println("  got " + unexpected);
      }
      check("a slot with no answers imports without failing", imported && scheduled.getRecords().size() == 1);
      check("its unit is blank, not made up", scheduled.getRecords().size() == 1 && scheduled.getRecords().get(0).getValue("Unit").isEmpty());

      // The same, when the slot has no customfields key at all.
      String bare = "{\"data\":{\"signup\":[{\"firstname\":\"tom\",\"lastname\":\"bare\",\"item\":\"Eagle Board of Review\","
         + "\"email\":\"tom@example.org\",\"startdatestring\":\"2026-11-14 10:00:00\"}]}}";
      boolean bareImported = true;
      try {
         new SignUpGeniusPlugin("test-key", "1", null, scheduled).importReport(new ObjectMapper().readTree(bare), "2026-11");
      } catch (Throwable unexpected) {
         bareImported = false;
         System.out.println("  got " + unexpected);
      }
      check("a slot with no customfields key imports without failing", bareImported && scheduled.getRecords().size() == 2);
   }

   /** Both formats a DataRecordFile saves through: the CSV data files and the .properties configuration. */
   private static void checkFileFormats(Path scratch) throws Exception {
      File csvFile = scratch.resolve("round-trip.csv").toFile();
      DataRecordFile<ScoutRecord> scouts = new DataRecordFile<>(csvFile, new ScoutRecord.Factory());
      // Added out of alphabetical order, so reading them back in file order
      // cannot be mistaken for sorted order.
      ScoutRecord first = scouts.addNew("SCOUT:Bell:Bo:2", false);
      first.setValue("Last", "Bell");
      ScoutRecord second = scouts.addNew("SCOUT:Ames:Al:1", false);
      second.setValue("Last", "Ames");
      scouts.store();

      List<String> lines = Files.readAllLines(csvFile.toPath());
      check("csv: the first line names the columns", lines.get(0).startsWith("Type,ID,RegNum,Last,First"));
      check("csv: one line per record after it", lines.size() == 3);
      DataRecordFile<ScoutRecord> reopened = new DataRecordFile<>(csvFile, new ScoutRecord.Factory());
      check("csv: the records read back in order",
         reopened.getRecords().size() == 2 && "Bell".equals(reopened.getRecords().get(0).getValue("Last"))
            && "Ames".equals(reopened.getRecords().get(1).getValue("Last")));
      check("csv: a record is found by its ID", reopened.get("SCOUT:Bell:Bo:2") != null);

      File configFile = scratch.resolve("config.properties").toFile();
      DataRecordFile<ConfigRecord> config = new DataRecordFile<>(configFile, new ConfigRecord.Factory());
      ConfigRecord settings = new ConfigRecord();
      settings.setValue("FinalYellowMins", "33");
      config.add(settings, true);

      String text = Files.readString(configFile.toPath());
      check("properties: a comment header, then column=value lines",
         text.startsWith("# Eagle Board Scheduler configuration") && text.contains("\nFinalYellowMins=33\n"));
      DataRecordFile<ConfigRecord> reloaded = new DataRecordFile<>(configFile, new ConfigRecord.Factory());
      check("properties: one record reads back with its value",
         reloaded.getRecords().size() == 1 && "33".equals(reloaded.getRecords().get(0).getValue("FinalYellowMins")));

      // A hand-edited file with a blank value must not blank out the default.
      File handEdited = scratch.resolve("hand-edited.properties").toFile();
      Files.writeString(handEdited.toPath(), "# edited by hand\nID=\nFinalYellowMins=44\n");
      DataRecordFile<ConfigRecord> blank = new DataRecordFile<>(handEdited, new ConfigRecord.Factory());
      check("properties: a column left blank keeps its default, not an empty value",
         blank.getRecords().size() == 1 && "DEFAULT".equals(blank.getRecords().get(0).getValue("ID"))
            && "44".equals(blank.getRecords().get(0).getValue("FinalYellowMins")));
   }

   private static void check(String name, boolean condition) {
      checks++;
      if (!condition) {
         failures++;
         System.out.println("FAIL: " + name);
      }
   }

   private static void deleteTree(Path root) throws IOException {
      try (Stream<Path> paths = Files.walk(root)) {
         paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
      }
   }
}
