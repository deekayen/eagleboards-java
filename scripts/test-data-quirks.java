// ------------------------------------------------------------------------
// test-data-quirks.java — regression checks for three small bugs that were
// found while tidying the source, each fixed with no change to anything
// else:
//
//   1. A pre-registration phone number such as "(555) 123-4567" used to be
//      stored garbled; it is now rebuilt from its digits as 555-123-4567.
//   2. Commandline's "required options" constructor threw a
//      NullPointerException, instead of MissingCommandlineArgument, when a
//      required option was left out.
//   3. Record timestamps came from one SimpleDateFormat shared by every
//      request thread, which is not thread-safe; many threads creating
//      records at once could produce unreadable stamps.
//
// No network, no running server and no test framework: each check prints
// FAIL and the script exits non-zero if any of them fails. Run it against
// the built jar (Java's single-file source launch):
//
//   java -cp target/eagleboardscheduler-<version>.jar scripts/test-data-quirks.java
//
// Runs on all four CI platforms (see .github/workflows/build.yml).
// ------------------------------------------------------------------------

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import shkc.core.AdultRecord;
import shkc.core.Commandline;
import shkc.core.DataRecordFile;
import shkc.core.MissingCommandlineArgument;
import shkc.core.NegaPreRegAdultRecordConverter;
import shkc.core.ScoutRecord;

public class TestDataQuirks {
   private static int failures = 0;
   private static int checks = 0;

   public static void main(String[] args) throws Exception {
      Path scratch = Files.createTempDirectory("data-quirks");
      try {
         checkPhoneNumbers(scratch);
         checkMissingRequiredOption();
         checkTimestampsUnderThreads();
      } finally {
         deleteTree(scratch);
      }

      if (failures > 0) {
         System.out.println("FAILED: " + failures + " of " + checks + " checks");
         System.exit(1);
      }
      System.out.println("PASS: data quirk checks (" + checks + ")");
   }

   private static void checkPhoneNumbers(Path scratch) throws IOException {
      // One sheet, one row per way of writing a number. "Scouts Contact
      // Number" feeds the adult importer's phone converter.
      List<String> sheet = List.of(
         "Email,First Name,Last Name,Scouts Contact Number,Scoutmasters Name,Unit Number",
         "paren@example.com,Pat,Paren,(555) 123-4567,Sam Master,Troop 1",
         "plain@example.com,Pia,Plain,5551234567,Sam Master,Troop 1",
         "dashed@example.com,Dee,Dashed,555-123-4567,Sam Master,Troop 1",
         "dotted@example.com,Dot,Dotted,(555)123-4567,Sam Master,Troop 1");
      File sheetFile = scratch.resolve("sheet.csv").toFile();
      Files.write(sheetFile.toPath(), sheet);

      DataRecordFile<AdultRecord> history = new DataRecordFile<>(scratch.resolve("history.csv").toFile(), new AdultRecord.Factory());
      new NegaPreRegAdultRecordConverter(history).convert(sheetFile);

      check("phone: (555) 123-4567 is rebuilt from its digits", "555-123-4567".equals(phoneOf(history, "paren@example.com")));
      check("phone: 5551234567 gets its dashes", "555-123-4567".equals(phoneOf(history, "plain@example.com")));
      check("phone: 555-123-4567 is kept as typed", "555-123-4567".equals(phoneOf(history, "dashed@example.com")));
      check("phone: (555)123-4567 is rebuilt from its digits", "555-123-4567".equals(phoneOf(history, "dotted@example.com")));
   }

   private static String phoneOf(DataRecordFile<AdultRecord> history, String email) {
      List<AdultRecord> matches = history.get("Email", email);
      return matches.isEmpty() ? "(no such adult)" : matches.get(0).getValue("Phone");
   }

   private static void checkMissingRequiredOption() throws Exception {
      String[] noArguments = new String[0];
      String[] noNames = new String[0];

      boolean missingReported = false;
      boolean otherFailure = false;
      try {
         new Commandline(noArguments, "", "", "d", noNames, noNames, new String[]{"port"});
      } catch (MissingCommandlineArgument expected) {
         missingReported = expected.getMessage().contains("d") && expected.getMessage().contains("port");
      } catch (Throwable unexpected) {
         otherFailure = true;
         System.out.println("  got " + unexpected);
      }
      check("commandline: a missing required option raises MissingCommandlineArgument naming it", missingReported && !otherFailure);

      boolean satisfied = true;
      try {
         new Commandline(new String[]{"-d", "somewhere", "-port", "80"}, "", "", "d", noNames, noNames, new String[]{"port"});
      } catch (Throwable unexpected) {
         satisfied = false;
         System.out.println("  got " + unexpected);
      }
      check("commandline: required options that were given are accepted", satisfied);
   }

   private static void checkTimestampsUnderThreads() throws Exception {
      // The stamp looks like 2026-11-02_14:05-0500. Every record made on any
      // thread must carry one that reads back that way.
      Pattern stamp = Pattern.compile("\\d{4}-\\d{2}-\\d{2}_\\d{2}:\\d{2}[+-]\\d{4}");
      int threads = 16;
      int recordsPerThread = 2000;
      ExecutorService pool = Executors.newFixedThreadPool(threads);
      List<Future<Integer>> results = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
         Callable<Integer> job = () -> {
            int bad = 0;
            for (int i = 0; i < recordsPerThread; i++) {
               ScoutRecord scout = new ScoutRecord(null);
               if (!stamp.matcher(scout.getRegTime()).matches() || !stamp.matcher(scout.getLastUpdateTime()).matches()) {
                  bad++;
               }
               if (scout.getMinsSinceLastUpdate().isEmpty()) {
                  bad++;
               }
            }
            return bad;
         };
         results.add(pool.submit(job));
      }
      int bad = 0;
      for (Future<Integer> result : results) {
         bad += result.get();
      }
      pool.shutdown();
      check("timestamps: " + (threads * recordsPerThread) + " records made on " + threads + " threads all read back (" + bad + " bad)", bad == 0);
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
