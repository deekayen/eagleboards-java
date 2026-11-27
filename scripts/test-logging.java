// ------------------------------------------------------------------------
// test-logging.java: unit tests for the event log (Log), with no server.
//
// Log is the one place everything the program does at an event is written
// down, so these pin what a person diagnosing a problem relies on and what
// the privacy rule forbids:
//
//   * the line format (timestamp, level, request context, thread) and what
//     the console still prints,
//   * lines logged before the file is attached, the cap on them, and a
//     restart appending to the same file,
//   * rollover by size, and that only the newest KEPT_FILES rolled files stay,
//   * what is masked or shortened before it is written (scrub, redact,
//     redactArgs, safeParams, clean), and what must NOT be mangled,
//   * a folder that cannot be written never throws and never stops the program,
//     and a write or rollover that fails once does not end the log for the run,
//   * what a file's first line may put in the log (a data row is not a heading),
//     and that a message cut for length is masked before it is cut,
//   * many threads writing at once neither interleave nor lose lines.
//
// Log's size limit and a few helpers are package-private, and the source-file
// launcher loads this class in a different class loader than the jar's
// shkc.core classes, so package access is refused at run time even from a
// class declared in that package. The test therefore reads those members by
// reflection (setAccessible works because the jar is on the classpath, not
// the module path). No production code carries a test seam for this.
//
// No network, no running server, and no test framework: each check prints
// FAIL and the script exits non-zero if any of them fails. Run it against
// the built jar (Java's single-file source launch):
//
//   java -cp target/eagleboardscheduler-<version>.jar scripts/test-logging.java
//
// Runs on all four CI platforms (see .github/workflows/build.yml). The
// whole-program side (what a real event writes, and that nothing personal
// leaks) is scripts/test-logging.sh.
// ------------------------------------------------------------------------

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import shkc.core.ConfigRecord;
import shkc.core.Log;

public class TestLogging {
   private static int failures = 0;

   /** A whole log line as Log writes it: timestamp, padded level, context, [thread], text. */
   private static final Pattern LINE_SHAPE = Pattern.compile(
      "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) (INFO |WARN |ERROR|DEBUG) (\\S+) \\[([^\\]]+)\\] (.*)$");

   private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

   private static Path root;

   public static void main(String[] args) throws Exception {
      root = Files.createTempDirectory("eb-logging");
      try {
         testLineFormat();
         testConsole();
         testHeldLinesAndAppend();
         testHeldLinesCap();
         testClean();
         testScrubMasks();
         testScrubLeavesOrdinaryText();
         testRedactAndArgs();
         testSafeParams();
         testWrittenLinesAreScrubbed();
         testUnwritableFolder();
         testTransientFailuresDoNotEndTheLog();
         testRolloverFailureKeepsLogging();
         testScrubGaps();
         testScrubBeforeTheCut();
         testRegisteredSecret();
         testHeadingLines();
         testImplausibleSettings();
         testClockMovedNote();
         testRolloverOnAttach();
         testConcurrentWriters();
         testRolloverBySize();
      } finally {
         reset();
         deleteTree(root);
      }

      if (failures > 0) {
         System.out.println(failures + " logging check(s) failed");
         System.exit(1);
      }
      System.out.println("PASS: logging checks");
   }

   // ---- Line format ------------------------------------------------------

   private static void testLineFormat() throws Exception {
      File folder = newFolder("format");
      reset();
      Log.attachFile(folder);

      LocalDateTime before = LocalDateTime.now().minusSeconds(2);
      Thread worker = new Thread(() -> {
         Log.setContext("r42");
         Log.info("hello info");
         Log.warn("hello warn");
         Log.error("hello error", null);
         Log.clearContext();
         Log.info("after clear");
      }, "worker-7");
      quietly(() -> {
         worker.start();
         worker.join();
      });
      LocalDateTime after = LocalDateTime.now().plusSeconds(2);

      List<String> lines = readLines(new File(folder, Log.FILE_NAME));
      check("four lines were written", lines.size() == 4);
      if (lines.size() < 4) {
         return;
      }
      String[] levels = {"INFO ", "WARN ", "ERROR", "INFO "};
      String[] contexts = {"r42", "r42", "r42", "-"};
      String[] texts = {"hello info", "hello warn", "hello error", "after clear"};
      for (int i = 0; i < 4; i++) {
         java.util.regex.Matcher line = LINE_SHAPE.matcher(lines.get(i));
         if (!line.matches()) {
            check("line " + i + " has the timestamp/level/context/thread shape: " + lines.get(i), false);
            continue;
         }
         LocalDateTime written = LocalDateTime.parse(line.group(1), TIMESTAMP);
         check("line " + i + " carries the current local time", !written.isBefore(before) && !written.isAfter(after));
         check("line " + i + " level is padded to five: '" + line.group(2) + "'", levels[i].equals(line.group(2)));
         check("line " + i + " carries the context '" + contexts[i] + "'", contexts[i].equals(line.group(3)));
         check("line " + i + " carries the thread name", "worker-7".equals(line.group(4)));
         check("line " + i + " carries the text", texts[i].equals(line.group(5)));
      }
      check("the level column is two spaces wide after INFO (so levels line up)", lines.get(0).contains(" INFO  r42 ["));

      // A context set on one thread is not seen on another.
      Thread other = new Thread(() -> Log.info("other thread"), "other-thread");
      Log.setContext("r1");
      quietly(() -> {
         other.start();
         other.join();
      });
      Log.clearContext();
      List<String> more = readLines(new File(folder, Log.FILE_NAME));
      check("a context is per thread", more.size() == 5 && more.get(4).contains(" - [other-thread] other thread"));

      // DEBUG reaches the file only with -debug, and never the console without -v.
      Log.verbose("debug detail one");
      check("verbose without -debug is not written to the file", readLines(new File(folder, Log.FILE_NAME)).size() == 5);
      Log.setDebugToFile(true);
      check("isDebugToFile reports the switch", Log.isDebugToFile());
      String console = capture(() -> Log.verbose("debug detail two"));
      Log.setDebugToFile(false);
      List<String> withDebug = readLines(new File(folder, Log.FILE_NAME));
      check("verbose with -debug is written as DEBUG", withDebug.size() == 6 && withDebug.get(5).matches(".* DEBUG .*debug detail two$"));
      check("verbose without -v stays off the console", !console.contains("debug detail two"));
      Log.setDebugToFile(true);
      Log.verbose("calling https://example.test/api?key=ABCDEF123456&x=1");
      Log.setDebugToFile(false);
      String debugText = String.join("\n", readLines(new File(folder, Log.FILE_NAME)));
      check("a DEBUG line still has a key masked", debugText.contains("key=***") && !debugText.contains("ABCDEF123456"));
      reset();
   }

   // ---- Console ----------------------------------------------------------

   private static void testConsole() throws Exception {
      File folder = newFolder("console");
      reset();
      Log.attachFile(folder);

      check("info stays off the console", capture(() -> Log.info("quiet one")).isEmpty());
      String warn = capture(() -> Log.warn("careful\nsecond line"));
      check("warn prints one console line, newline cleaned", "WARN: careful?second line".equals(warn.trim()));
      String warnCause = capture(() -> Log.warn("careful", new IllegalStateException("because")));
      check("warn with a cause names it on the console", warnCause.trim().equals("WARN: careful (java.lang.IllegalStateException: because)"));
      String error = capture(() -> Log.error("broke", new IllegalStateException("why")));
      check("error with a cause prints ERROR and the stack", error.contains("ERROR: broke (java.lang.IllegalStateException: why)")
         && error.contains("at "));
      String errorBare = capture(() -> Log.error("broke bare"));
      check("error without a cause prints ERROR", errorBare.contains("ERROR: broke bare"));

      Log.setVerbose(true);
      String verbose = capture(() -> Log.verbose("shown with -v"));
      Log.setVerbose(false);
      check("verbose prints with -v", verbose.trim().equals("shown with -v"));

      List<String> lines = readLines(new File(folder, Log.FILE_NAME));
      check("everything but verbose reached the file, one line each", lines.size() - countStackLines(lines) == 5);
      reset();
   }

   // ---- Held lines, attach, restart --------------------------------------

   private static void testHeldLinesAndAppend() throws Exception {
      File folder = newFolder("held");
      reset();

      Log.setContext("early");
      Log.info("before attach one");
      quietly(() -> Log.warn("before attach two"));
      Log.clearContext();
      LocalDateTime loggedBy = LocalDateTime.now();
      Thread.sleep(30);
      check("no file before attach", Log.getLogFile() == null);

      Log.attachFile(folder);
      Log.info("after attach");
      File file = new File(folder, Log.FILE_NAME);
      List<String> lines = readLines(file);
      check("getLogFile names the file once attached", file.equals(Log.getLogFile()));
      check("lines logged before attach are written, once, then the new one", lines.size() == 3);
      if (lines.size() == 3) {
         check("held line one is first, with its context", lines.get(0).contains(" early [") && lines.get(0).endsWith("before attach one"));
         check("held line two is second, with its level", lines.get(1).contains(" WARN  early [") && lines.get(1).endsWith("before attach two"));
         check("the new line follows", lines.get(2).endsWith("after attach"));
         java.util.regex.Matcher first = LINE_SHAPE.matcher(lines.get(0));
         check("a held line keeps the time it was logged, not the time it was attached",
            first.matches() && !LocalDateTime.parse(first.group(1), TIMESTAMP).isAfter(loggedBy));
      }

      // A restart: the file is appended to, never truncated.
      reset();
      Log.attachFile(folder);
      Log.info("second run");
      List<String> afterRestart = readLines(file);
      check("attaching again appends (a restart continues the file)", afterRestart.size() == 4
         && afterRestart.get(0).endsWith("before attach one") && afterRestart.get(3).endsWith("second run"));

      // Attaching a different folder switches files and leaves the first alone.
      File elsewhere = newFolder("held-elsewhere");
      Log.attachFile(elsewhere);
      Log.info("in the other folder");
      check("a second folder gets its own file", readLines(new File(elsewhere, Log.FILE_NAME)).size() == 1);
      check("and the first file is not touched", readLines(file).size() == 4);
      reset();
   }

   private static void testHeldLinesCap() throws Exception {
      File folder = newFolder("cap");
      reset();
      int cap = (int) privateConstant("MAX_HELD_LINES");
      check("the held-lines cap is a sensible size (" + cap + ")", cap >= 100 && cap <= 100000);
      int total = cap + 500;
      for (int i = 0; i < total; i++) {
         Log.info("held " + i);
      }
      Log.attachFile(folder);
      List<String> lines = readLines(new File(folder, Log.FILE_NAME));
      check("only the newest " + cap + " held lines are written, got " + lines.size(), lines.size() == cap);
      if (!lines.isEmpty()) {
         check("the oldest held lines were the ones dropped", lines.get(0).endsWith("held 500"));
         check("the newest held line is last", lines.get(lines.size() - 1).endsWith("held " + (total - 1)));
      }
      reset();
   }

   // ---- clean ------------------------------------------------------------

   private static void testClean() throws Exception {
      check("clean replaces newlines and control characters",
         "a?b??c?d?e".equals(Log.clean("a\nb\r\nc\u0000d\u007fe")));
      check("clean replaces tabs", "a?b".equals(Log.clean("a\tb")));
      check("clean leaves ordinary text, colons and accents alone",
         "SCOUT:Ng\u00fcyen:Zo\u00eb:1001 (P3)".equals(Log.clean("SCOUT:Ng\u00fcyen:Zo\u00eb:1001 (P3)")));
      check("clean of null is the word null", "null".equals(Log.clean(null)));

      // A value cannot forge a second log line.
      File folder = newFolder("forge");
      reset();
      Log.attachFile(folder);
      Log.info("legit\n2030-01-01 00:00:00.000 ERROR r9 [main] forged line\r\nand another");
      List<String> lines = readLines(new File(folder, Log.FILE_NAME));
      check("a message with line breaks is one line (forging)", lines.size() == 1);
      check("and the break is visible as a ? in it", lines.size() == 1 && lines.get(0).contains("legit?2030-01-01"));

      // An exception message that quotes data with line breaks keeps its stack lines indented.
      quietly(() -> Log.warn("with a stack", new RuntimeException("first\nSECOND LINE\nthird")));
      List<String> withStack = readLines(new File(folder, Log.FILE_NAME));
      boolean allFollowers = true;
      for (int i = 1; i < withStack.size(); i++) {
         String line = withStack.get(i);
         if (!LINE_SHAPE.matcher(line).matches() && !line.startsWith("    ")) {
            allFollowers = false;
         }
      }
      check("a stack trace's lines are indented under their line", allFollowers && withStack.size() > 2);
      check("the stack names the exception", withStack.stream().anyMatch(l -> l.startsWith("    java.lang.RuntimeException: first")));

      // A message past the cap is cut, with the amount cut said.
      Log.info("x".repeat(10) + " " + "y".repeat(10000));
      List<String> longOne = readLines(new File(folder, Log.FILE_NAME));
      String last = longOne.get(longOne.size() - 1);
      check("a message over 4000 characters is cut and says how much", last.contains("... (") && last.endsWith(" more chars)")
         && last.length() < 4200);
      reset();
   }

   // ---- scrub ------------------------------------------------------------

   private static void testScrubMasks() throws Exception {
      String[][] masked = {
         {"canary.zz1@example.test", "<email>"},
         {"o'brien+scouts@mail-host.example.org", "<email>"},
         {"email=a.b@c.de.", "email=<email>."},
         {"CANARY.UPPER@EXAMPLE.TEST", "<email>"},
         {"call 555-010-0199 now", "call <phone> now"},
         {"call (555) 010-0199 now", "call <phone> now"},
         {"call (555)010-0199 now", "call <phone> now"},
         {"call 555.010.0199 now", "call <phone> now"},
         {"call 555 010 0199 now", "call <phone> now"},
         {"call +1 555 010 0199 now", "call <phone> now"},
         {"call 1-555-010-0199 now", "call <phone> now"},
         {"phone=555-010-0199;", "phone=<phone>;"},
         {"GET /x?key=ABCDEF123456 -> 200", "GET /x?key=*** -> 200"},
         {"GET /x?a=1&apikey=ABCDEF123456&b=2", "GET /x?a=1&apikey=***&b=2"},
         {"GET /x?a=1&token=tok3n&b=2", "GET /x?a=1&token=***&b=2"},
         {"failed for url ?user_key=ABCDEF123456", "failed for url ?user_key=***"},
         {"login password=hunter2 failed", "login password=*** failed"},
         {"the secret=abc123 was sent", "the secret=*** was sent"},
         {"key=ABCDEF123456 starts the line", "key=*** starts the line"},
         {"env SUG_KEY=ABCDEF123456 was set", "env SUG_KEY=*** was set"},
         {"sugkey=ABCDEF123456", "sugkey=***"},
         {"header x-api-key=ABCDEF123456 sent", "header x-api-key=*** sent"},
         {"KEY=UPPERCASE123 was sent", null},
      };
      for (String[] pair : masked) {
         if (pair[1] == null) {
            String got = scrub(" " + pair[0]);
            check("scrub masks an upper-case KEY=: '" + got + "'", got.contains("KEY=***") && !got.contains("UPPERCASE123"));
         } else {
            String got = scrub(pair[0]);
            check("scrub('" + pair[0] + "') is '" + pair[1] + "', got '" + got + "'", pair[1].equals(got));
         }
      }
      String several = scrub("a@b.example and 555-010-0199 and c@d.example ?key=KKKKKKKKKK1");
      check("scrub handles several secrets in one line", several.equals("<email> and <phone> and <email> ?key=***"));
   }

   private static void testScrubLeavesOrdinaryText() throws Exception {
      String[] untouched = {
         "youth 14 {Completed=6, Registered=8}, rooms 3/12 in use, adults 30 (5 on a board, 2 gone home)",
         "heap 123/4096 MB, threads 14, up 5 min",
         "2026-11-27 14:03:22.123 INFO",
         "clock 2026-11-27T14:03:22.123456-05:00[America/New_York]",
         "loaded scouts.csv: 14 records",
         "SCOUT:Aldridge:Alexander:1001 (W3) room=101 type=Final",
         "ADULT:Abernathy:Anneliese:2001 chair ADULT:Blackwood:Bartholomew:2002",
         "ROOM:200A ROOM:201B room 202",
         "POST /seat-board -> 200 (14 ms) from 127.0.0.1 [RoomID=ROOM:101 ScoutID=SCOUT:Aldridge:Alexander:1001]",
         "listening on port 18098, bind 192.168.1.20",
         "network: interface \"eth0\" has 10.0.0.5",
         "version 2026.11.12, jar C:\\x\\eagleboardscheduler-2026.11.12.jar (53469021 bytes, built 2026-11-12T15:04:05Z)",
         "java 25.0.1 (Eclipse Adoptium), Linux 6.8.0 amd64, 4 cpus, max heap 2048 MB",
         "a number 1234567890123 and a count 1000000 and 12-34-5678",
         "counts(P=3 W=11) range 100-200",
         "Email=<23 chars> Phone=<12 chars> Notes=<15 chars>",
         "keyboard, monkey=banana, keys=2, the key is under the mat",
         "tokens used 3, password policy ok",
         "meeting at nobody@ and @everyone and a@b",
         "https://example.test/page?room=101&scout=7",
      };
      for (String text : untouched) {
         String got = scrub(text);
         check("scrub leaves alone: '" + text + "' (got '" + got + "')", text.equals(got));
      }
   }

   // ---- redact, redactArgs -----------------------------------------------

   private static void testRedactAndArgs() {
      check("redact masks key= in a URL", "https://x.test/y?key=***&a=b".equals(Log.redact("https://x.test/y?key=SECRET99&a=b")));
      check("redact masks the very last value too", "https://x.test/y?a=b&token=***".equals(Log.redact("https://x.test/y?a=b&token=SECRET99")));
      check("redact of null is null", Log.redact(null) == null);
      check("redact leaves a URL with no secret alone", "https://x.test/y?a=b".equals(Log.redact("https://x.test/y?a=b")));

      check("redactArgs masks the -sugkey value, keeps the rest",
         "-a Hist.csv -sugkey *** -port 80 -d run".equals(Log.redactArgs(new String[]{"-a", "Hist.csv", "-sugkey", "CNRYKEY9Z4abcdef", "-port", "80", "-d", "run"})));
      check("redactArgs masks the --sugkey spelling",
         "--sugkey *** -v".equals(Log.redactArgs(new String[]{"--sugkey", "CNRYKEY9Z4abcdef", "-v"})));
      check("redactArgs masks it when it is the last option",
         "-d run -sugkey ***".equals(Log.redactArgs(new String[]{"-d", "run", "-sugkey", "CNRYKEY9Z4abcdef"})));
      check("redactArgs with -sugkey and no value does not fail",
         "-d run -sugkey".equals(Log.redactArgs(new String[]{"-d", "run", "-sugkey"})));
      check("redactArgs of nothing is empty", "".equals(Log.redactArgs(new String[0])));
      String id = Log.redactArgs(new String[]{"-sugid", "123456", "-sugkey", "KEYKEYKEYKEY"});
      check("redactArgs keeps the sign-up id (it is not a secret)", "-sugid 123456 -sugkey ***".equals(id));
      check("redactArgs masks -sugkey=KEY, a spelling the parser refuses, so the console never echoes it",
         "-sugkey=*** -v".equals(Log.redactArgs(new String[]{"-sugkey=QRSTXYZ123456789", "-v"})));
      check("redactArg masks --sugkey=KEY", "--sugkey=***".equals(Log.redactArg("--sugkey=QRSTXYZ123456789")));
      check("redactArg masks a key= query inside any argument", "x?key=***".equals(Log.redactArg("x?key=QRSTXYZ123456789")));
      check("redactArg leaves an ordinary argument alone", "-port".equals(Log.redactArg("-port")) && "run".equals(Log.redactArg("run")));
   }

   // ---- safeParams -------------------------------------------------------

   @SuppressWarnings("unchecked")
   private static void testSafeParams() {
      Map<String, String[]> parameters = new LinkedHashMap<>();
      parameters.put("ScoutID", new String[]{"SCOUT:Aldridge:Alexander:1001"});
      parameters.put("Email", new String[]{"canary.zz1@example.test"});
      parameters.put("Phone", new String[]{"555-010-0199"});
      parameters.put("Notes", new String[]{"CANARY_NOTE_Q7"});
      parameters.put("DOB", new String[]{"2010-01-31"});
      parameters.put("Empty", new String[]{""});
      parameters.put("Result", new String[]{"Approved"});
      parameters.put("MemberIDs", new String[]{"ADULT:A:B:1,ADULT:C:D:2"});
      parameters.put("Two", new String[]{"first", "second!"});
      parameters.put("Nothing", null);
      parameters.put("Room", new String[]{"a\nb"});
      parameters.put("bad\nname", new String[]{"v"});
      String out = Log.safeParams(parameters);

      check("safeParams writes a safe column in full", out.contains("ScoutID=SCOUT:Aldridge:Alexander:1001"));
      check("safeParams writes Result and the member list in full", out.contains("Result=Approved") && out.contains("MemberIDs=ADULT:A:B:1,ADULT:C:D:2"));
      check("safeParams writes only the length of an Email: " + out, out.contains("Email=<23 chars>"));
      check("safeParams writes only the length of a Phone", out.contains("Phone=<12 chars>"));
      check("safeParams writes only the length of Notes", out.contains("Notes=<14 chars>"));
      check("safeParams writes only the length of a birthdate", out.contains("DOB=<10 chars>"));
      check("safeParams never writes an email, phone or note value",
         !out.contains("canary") && !out.contains("555-010") && !out.contains("CANARY_NOTE") && !out.contains("2010-01-31"));
      check("safeParams marks an empty value", out.contains("Empty=<empty>"));
      check("safeParams writes two values of an unsafe name as two lengths", out.contains("Two=<5 chars>|<7 chars>"));
      check("safeParams copes with a null value list", out.contains("Nothing="));
      check("safeParams cleans a line break inside a safe value", out.contains("Room=a?b"));
      check("safeParams cleans a line break inside a name", out.contains("bad?name=<1 chars>"));
      check("safeParams is one line", out.indexOf('\n') < 0);
      check("safeParams lists names in order", out.indexOf("DOB=") < out.indexOf("Email=") && out.indexOf("Email=") < out.indexOf("Phone="));

      check("safeValue of null", "<null>".equals(Log.safeValue("Email", null)));
      check("safeValue of an unsafe name is its length", "<4 chars>".equals(Log.safeValue("Email", "a@b.")));
      check("safeValue of a safe name is the value", "Final".equals(Log.safeValue("BoardType", "Final")));
      check("safeValue cuts a very long safe value", Log.safeValue("MemberIDs", "m".repeat(500)).length() == 203
         && Log.safeValue("MemberIDs", "m".repeat(500)).endsWith("..."));
      check("a column that is not on the safe list is not written (a made-up one)", "<5 chars>".equals(Log.safeValue("Secret", "abcde")));

      // The request form reads getParameterMap, and a failure there is reported, not thrown.
      Map<String, String[]> fromRequest = new LinkedHashMap<>();
      fromRequest.put("Email", new String[]{"canary.zz1@example.test"});
      fromRequest.put("cmd", new String[]{"x"});
      jakarta.servlet.http.HttpServletRequest request = (jakarta.servlet.http.HttpServletRequest) Proxy.newProxyInstance(
         TestLogging.class.getClassLoader(), new Class<?>[]{jakarta.servlet.http.HttpServletRequest.class},
         (InvocationHandler) (proxy, method, methodArgs) -> {
            if ("getParameterMap".equals(method.getName())) {
               return fromRequest;
            }
            return null;
         });
      check("safeParams(request) reads the parameter map", "Email=<23 chars> cmd=x".equals(Log.safeParams(request)));
      jakarta.servlet.http.HttpServletRequest broken = (jakarta.servlet.http.HttpServletRequest) Proxy.newProxyInstance(
         TestLogging.class.getClassLoader(), new Class<?>[]{jakarta.servlet.http.HttpServletRequest.class},
         (InvocationHandler) (proxy, method, methodArgs) -> {
            throw new IllegalStateException("cannot parse query");
         });
      String unreadable = Log.safeParams(broken);
      check("safeParams(request) of an unreadable query says so instead of throwing: " + unreadable,
         unreadable.startsWith("(parameters unreadable"));
   }

   /** What reaches the file for the things a careless call might pass: scrub is applied to every non-DEBUG line. */
   private static void testWrittenLinesAreScrubbed() throws Exception {
      File folder = newFolder("scrubbed");
      reset();
      Log.attachFile(folder);
      Log.info("row: canary.zz1@example.test, 555-010-0199, ok");
      quietly(() -> Log.warn("load failed on line: ADULT,x,canary.zz2@example.test,(555) 010-0199",
         new RuntimeException("bad row canary.zz3@example.test 555.010.0199")));
      quietly(() -> Log.error("with key ?key=KKKKKKKKKK2", new RuntimeException("url ?token=TTTTTTTTTT3")));
      String text = String.join("\n", readLines(new File(folder, Log.FILE_NAME)));
      check("INFO, WARN and ERROR lines have emails scrubbed, including in a stack trace", !text.contains("canary.zz"));
      check("and phone numbers", !text.contains("555-010") && !text.contains("010-0199") && !text.contains("555.010"));
      check("and keys and tokens", !text.contains("KKKKKKKKKK2") && !text.contains("TTTTTTTTTT3"));
      check("and says what it removed", text.contains("<email>") && text.contains("<phone>") && text.contains("key=***"));
      reset();
   }

   // ---- Unwritable folders -----------------------------------------------

   private static void testUnwritableFolder() throws Exception {
      reset();
      File missing = new File(root.toFile(), "no-such-folder/at-all");
      String console = capture(() -> Log.attachFile(missing));
      check("a folder that does not exist does not throw, and says so on the console", console.contains("cannot write the event log"));
      check("and there is no log file", Log.getLogFile() == null);
      Log.info("still alive one");
      quietly(() -> Log.warn("still alive two", new RuntimeException("x")));
      quietly(() -> Log.error("still alive three"));
      Log.verbose("still alive four");
      check("logging after a failed attach does not throw", true);

      // A regular file where the folder should be.
      File notAFolder = new File(root.toFile(), "i-am-a-file");
      Files.writeString(notAFolder.toPath(), "x");
      String console2 = capture(() -> Log.attachFile(notAFolder));
      check("a file where the folder should be does not throw", Log.getLogFile() == null && console2.contains("cannot write"));

      // Recovery: a good attach after a failed one works.
      File good = newFolder("recovered");
      Log.attachFile(good);
      Log.info("recovered line");
      check("a later attach to a good folder works", readLines(new File(good, Log.FILE_NAME)).size() == 1);

      // The folder vanishes under a running log (a USB stick pulled).
      File vanishing = newFolder("vanishing");
      Log.attachFile(vanishing);
      Log.info("before it vanished");
      deleteTree(vanishing.toPath());
      String lost = capture(() -> Log.info("after it vanished"));
      check("a folder that disappears mid-run does not throw, and says so once",
         lost.contains("cannot be written") && lost.contains("held"));
      check("and the log stays attached, so it can recover", Log.getLogFile() != null);
      String again = capture(() -> Log.info("and again"));
      check("and later calls stay quiet and safe", again.isEmpty());
      quietly(() -> Log.warn("and a warning still reaches the console"));
      check("a warning still prints to the console while the file cannot be written", capture(() -> Log.warn("console only")).contains("WARN: console only"));

      // Restoring the folder is enough: the next line writes what was held, then itself.
      Files.createDirectories(vanishing.toPath());
      String back = capture(() -> Log.info("resumed"));
      check("the log says on the console that it is being written again", back.contains("is being written again"));
      List<String> resumed = readLines(new File(vanishing, Log.FILE_NAME));
      String resumedText = String.join("\n", resumed);
      check("lines logged while the folder was gone are written when it comes back, in order",
         resumedText.indexOf("after it vanished") >= 0 && resumedText.indexOf("after it vanished") < resumedText.indexOf("and again")
         && resumedText.indexOf("and again") < resumedText.indexOf("resumed"));
      check("and a line says the log recovered and how many lines it held", resumedText.contains("event log recovered") && resumedText.contains("line(s) logged meanwhile"));
      reset();
   }

   // ---- Rollover ---------------------------------------------------------

   private static void testRolloverOnAttach() throws Exception {
      File folder = newFolder("roll-attach");
      reset();
      long max = (long) packageConstant("MAX_FILE_BYTES");
      int kept = (int) packageConstant("KEPT_FILES");
      check("the size limit is a sensible " + max + " bytes", max >= 1024L * 1024L && max <= 100L * 1024L * 1024L);
      check("KEPT_FILES is " + kept, kept >= 1 && kept <= 20);

      // A file already past the limit when the program starts is rolled, not appended to.
      File live = new File(folder, Log.FILE_NAME);
      writeFileOfSize(live, max + 1, "PREVIOUS RUN");
      Log.attachFile(folder);
      Log.info("new run");
      File first = new File(folder, Log.FILE_NAME + ".1");
      check("an oversized file is rolled to .1 at start", first.isFile() && first.length() == max + 1);
      List<String> lines = readLines(live);
      check("and the live file starts fresh", lines.size() == 1 && lines.get(0).endsWith("new run"));

      // A file at exactly the limit is kept as it is.
      reset();
      File folder2 = newFolder("roll-attach-exact");
      File exact = new File(folder2, Log.FILE_NAME);
      writeFileOfSize(exact, max, "AT THE LIMIT");
      Log.attachFile(folder2);
      check("a file not past the limit is appended to", !new File(folder2, Log.FILE_NAME + ".1").exists());
      reset();
   }

   private static void testRolloverBySize() throws Exception {
      File folder = newFolder("roll-size");
      reset();
      long max = (long) packageConstant("MAX_FILE_BYTES");
      int kept = (int) packageConstant("KEPT_FILES");
      Log.attachFile(folder);

      // Each message is as long as Log allows, with spaces so the masking
      // patterns are not slowed by one huge unbroken word.
      StringBuilder filler = new StringBuilder();
      while (filler.length() < 3900) {
         filler.append("abcdefghi ");
      }
      String padding = filler.toString();
      int messageChars = 3900 + 12;
      long linesPerFile = max / messageChars + 1;
      long total = linesPerFile * (kept + 1) + linesPerFile / 2;

      long startedAt = System.currentTimeMillis();
      for (long i = 0; i < total; i++) {
         Log.info(String.format("SEQ %07d ", i) + padding);
      }
      long tookMillis = System.currentTimeMillis() - startedAt;
      System.out.println("  (wrote " + total + " lines, about " + total * (messageChars + 50) / (1024 * 1024) + " MB, in " + tookMillis + " ms)");

      File live = new File(folder, Log.FILE_NAME);
      check("the live file exists", live.isFile());
      for (int n = 1; n <= kept; n++) {
         File rolled = new File(folder, Log.FILE_NAME + "." + n);
         check("rolled file ." + n + " exists", rolled.isFile());
         if (rolled.isFile()) {
            check("rolled file ." + n + " is a little over the limit, not far past it (" + rolled.length() + ")",
               rolled.length() > max && rolled.length() < max + 20000);
         }
      }
      check("no rolled file beyond ." + kept + " is kept", !new File(folder, Log.FILE_NAME + "." + (kept + 1)).exists());
      check("the live file is under the limit", live.length() <= max + 20000);

      // The kept files hold one unbroken, ordered run of the newest lines.
      long previous = -1;
      boolean contiguous = true;
      long firstKept = -1;
      long lastKept = -1;
      int markers = 0;
      List<File> inOrder = new ArrayList<>();
      for (int n = kept; n >= 1; n--) {
         inOrder.add(new File(folder, Log.FILE_NAME + "." + n));
      }
      inOrder.add(live);
      for (File file : inOrder) {
         for (String line : readLines(file)) {
            if (line.contains(" INFO ") && line.contains("log continued: the file before this was rolled over")) {
               markers++;
               continue;
            }
            java.util.regex.Matcher seq = Pattern.compile("SEQ (\\d{7}) ").matcher(line);
            if (!LINE_SHAPE.matcher(line).matches() || !seq.find()) {
               contiguous = false;
               continue;
            }
            long number = Long.parseLong(seq.group(1));
            if (previous >= 0 && number != previous + 1) {
               contiguous = false;
            }
            if (firstKept < 0) {
               firstKept = number;
            }
            previous = number;
            lastKept = number;
         }
      }
      check("the kept files hold unbroken, ordered, whole lines (first " + firstKept + ", last " + lastKept + ")", contiguous);
      check("each rollover wrote a 'log continued' line at the top of the new file (" + markers + ")", markers >= kept);
      check("the newest line is in the live file", lastKept == total - 1);
      check("the oldest lines were dropped when the files went past the limit", firstKept > 0);
      reset();
   }

   // ---- Concurrency ------------------------------------------------------

   private static void testConcurrentWriters() throws Exception {
      File folder = newFolder("concurrent");
      reset();
      Log.attachFile(folder);
      int threads = 8;
      int each = 500;
      CountDownLatch start = new CountDownLatch(1);
      List<Thread> workers = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
         final int number = t;
         Thread worker = new Thread(() -> {
            Log.setContext("c" + number);
            try {
               start.await();
            } catch (InterruptedException interrupted) {
               return;
            }
            for (int i = 0; i < each; i++) {
               Log.info("T" + number + " L" + i + " " + "payload ".repeat(20) + "END");
            }
            Log.clearContext();
         }, "writer-" + t);
         workers.add(worker);
         worker.start();
      }
      start.countDown();
      for (Thread worker : workers) {
         worker.join();
      }

      List<String> lines = readLines(new File(folder, Log.FILE_NAME));
      check("8 threads x 500 lines wrote 4000 lines, got " + lines.size(), lines.size() == threads * each);
      Set<String> seen = new HashSet<>();
      int[] next = new int[threads];
      boolean wholeLines = true;
      boolean contextsMatch = true;
      boolean inOrderPerThread = true;
      Pattern payload = Pattern.compile("T(\\d+) L(\\d+) (?:payload ){20}END");
      for (String line : lines) {
         java.util.regex.Matcher shape = LINE_SHAPE.matcher(line);
         if (!shape.matches()) {
            wholeLines = false;
            continue;
         }
         java.util.regex.Matcher body = payload.matcher(shape.group(5));
         if (!body.matches()) {
            wholeLines = false;
            continue;
         }
         int thread = Integer.parseInt(body.group(1));
         int sequence = Integer.parseInt(body.group(2));
         if (!("c" + thread).equals(shape.group(3)) || !("writer-" + thread).equals(shape.group(4))) {
            contextsMatch = false;
         }
         if (sequence != next[thread]) {
            inOrderPerThread = false;
         }
         next[thread] = sequence + 1;
         seen.add(thread + ":" + sequence);
      }
      check("every line is whole (no interleaving)", wholeLines);
      check("every line carries its own thread's context and name", contextsMatch);
      check("each thread's lines are in order", inOrderPerThread);
      check("no line was lost or written twice", seen.size() == threads * each);
      reset();
   }

   // ---- A write or rollover that fails once -------------------------------

   private static void testTransientFailuresDoNotEndTheLog() throws Exception {
      File folder = newFolder("transient");
      reset();
      Log.attachFile(folder);
      Log.info("one");
      File live = new File(folder, Log.FILE_NAME);

      // A directory where the file should be makes every write fail until it is removed.
      Files.delete(live.toPath());
      Files.createDirectory(live.toPath());
      String first = capture(() -> Log.info("held one"));
      check("a failed write says so on the console, once, and says lines are held", first.contains("cannot be written") && first.contains("held"));
      check("the log is still attached after a failed write", Log.getLogFile() != null);
      check("further failed writes are quiet", capture(() -> {
         Log.info("held two");
         Log.info("held three");
      }).isEmpty());
      Files.delete(live.toPath());
      String back = capture(() -> Log.info("after"));
      check("once the cause is gone the next line writes, and the console says so", back.contains("is being written again"));
      String text = String.join("\n", readLines(live));
      check("the held lines are in the file, in order, before the new one",
         text.indexOf("held one") >= 0 && text.indexOf("held one") < text.indexOf("held two")
         && text.indexOf("held two") < text.indexOf("held three") && text.indexOf("held three") < text.indexOf("after"));
      check("and the recovery is recorded as a WARN naming how many were held",
         text.contains("WARN ") && text.contains("event log recovered") && text.contains("3 line(s) logged meanwhile"));

      // Too many held lines: the newest are kept, and the loss is counted.
      Files.delete(live.toPath());
      Files.createDirectory(live.toPath());
      int cap = (int) packageConstant("MAX_HELD_LINES");
      quietly(() -> {
         for (int i = 0; i < cap + 50; i++) {
            Log.info("pile " + i);
         }
      });
      Files.delete(live.toPath());
      quietly(() -> Log.info("recovered after the pile"));
      List<String> lines = readLines(live);
      String pile = String.join("\n", lines);
      check("a long outage keeps only the newest held lines", pile.contains("pile " + (cap + 49)) && !pile.contains("pile 0\n") && !pile.contains(" pile 0"));
      check("and says how many older lines were lost", pile.contains("older line(s) were lost"));
      reset();
   }

   private static void testRolloverFailureKeepsLogging() throws Exception {
      File folder = newFolder("roll-fails");
      reset();
      long max = (long) packageConstant("MAX_FILE_BYTES");
      Log.attachFile(folder);
      Log.info("before");
      File live = new File(folder, Log.FILE_NAME);

      // The oldest rolled name is a folder with something in it, so deleting it fails.
      File blocker = new File(folder, Log.FILE_NAME + "." + (int) packageConstant("KEPT_FILES"));
      Files.createDirectory(blocker.toPath());
      Files.writeString(new File(blocker, "keep").toPath(), "x");
      writeFileOfSize(live, max + 10, "filler");

      String console = capture(() -> Log.info("written although the rollover fails"));
      check("a rollover that fails says so on the console", console.contains("could not be rolled over"));
      check("it does not end the log: the file is still attached", Log.getLogFile() != null);
      check("and the line was written to the live file", String.join("\n", readLines(live)).contains("written although the rollover fails"));
      check("a second line does not repeat the console warning or retry at once",
         capture(() -> Log.info("second line")).isEmpty() && String.join("\n", readLines(live)).contains("second line"));

      // The cause is removed and the wait is over: the next line rolls.
      Files.delete(new File(blocker, "keep").toPath());
      Files.delete(blocker.toPath());
      Field retry = Log.class.getDeclaredField("nextRollAttemptMillis");
      retry.setAccessible(true);
      retry.setLong(null, 0L);
      quietly(() -> Log.info("rolls now"));
      check("once the cause is gone the log rolls over and goes on", new File(folder, Log.FILE_NAME + ".1").isFile()
         && String.join("\n", readLines(live)).contains("rolls now"));
      reset();
   }

   // ---- Masking gaps ------------------------------------------------------

   private static void testScrubGaps() throws Exception {
      String[][] masked = {
         {"mail josé@example.com now", "mail <email> now"},
         {"mail user@exämple.com now", "mail <email> now"},
         {"mail a&b@x.com now", "mail <email> now"},
         {"call 5551234567 now", "call <phone> now"},
         {"call (555)-123-4567 now", "call <phone> now"},
         {"call 555 - 123 - 4567 now", "call <phone> now"},
         {"call 555/123/4567 now", "call <phone> now"},
         {"call 555.123.4567, then", "call <phone>, then"},
      };
      for (String[] pair : masked) {
         String got = scrub(pair[0]);
         check("scrub('" + pair[0] + "') is '" + pair[1] + "', got '" + got + "'", pair[1].equals(got));
      }
      String[] untouched = {
         "epoch 1760000000123 ms and 12345678901 bytes",
         "a count of 5551234 and a year 2026",
         "room 101/102 and 100 200 30",
         "user@localhost and a@b",
      };
      for (String text : untouched) {
         check("scrub leaves alone: '" + text + "'", text.equals(scrub(text)));
      }
   }

   private static void testScrubBeforeTheCut() throws Exception {
      File folder = newFolder("cut");
      reset();
      Log.attachFile(folder);
      int cap = 4000;

      // An address that begins just before the cut would be left as a fragment if the text were cut first.
      String filler = "word ".repeat(cap / 5);
      Log.info(filler.substring(0, cap - 10) + "bob.smith@example.com and more text after it " + "z ".repeat(200));
      Log.info(filler.substring(0, cap - 5) + "(555) 010-0199 and more text after it " + "z ".repeat(200));
      Log.info(filler.substring(0, cap - 3) + "carol@example.org " + "z ".repeat(200));
      String text = String.join("\n", readLines(new File(folder, Log.FILE_NAME)));
      check("an email straddling the 4000-character cut leaves no fragment", !text.contains("bob") && !text.contains("@") && !text.contains("example.com"));
      check("a phone number straddling the cut leaves no fragment", !text.contains("010-01") && !text.contains("555") && !text.contains("(555"));
      check("a long message is still cut and says how much", text.contains(" more chars)"));

      // The masking patterns are slow on one huge unbroken word, so the text scrubbed is bounded.
      long started = System.currentTimeMillis();
      Log.info("a".repeat(1_000_000));
      quietly(() -> Log.warn("b".repeat(300_000), new RuntimeException("c".repeat(300_000))));
      long tookMillis = System.currentTimeMillis() - started;
      check("a megabyte with no spaces is logged quickly (" + tookMillis + " ms)", tookMillis < 5000);
      reset();
   }

   private static void testRegisteredSecret() throws Exception {
      File folder = newFolder("secret");
      reset();
      Log.attachFile(folder);
      Log.registerSecret("ABC123KEYVALUE");
      Log.registerSecret(null);
      Log.registerSecret("abc");
      check("a registered secret is masked in a form with no key= in front of it", "Invalid key ***".equals(Log.redact("Invalid key ABC123KEYVALUE")));
      check("and inside JSON", "{\"user_key\":\"***\"}".equals(Log.redact("{\"user_key\":\"ABC123KEYVALUE\"}")));
      check("and everywhere it appears", "*** and ***".equals(Log.redact("ABC123KEYVALUE and ABC123KEYVALUE")));
      check("a short value is not registered, so ordinary text is not mangled", "an abc here".equals(Log.redact("an abc here")));
      Log.info("the service said: Invalid key ABC123KEYVALUE");
      quietly(() -> Log.warn("trouble", new RuntimeException("rejected ABC123KEYVALUE")));
      Log.verbose("not written without -debug");
      Log.setDebugToFile(true);
      Log.verbose("debug saw ABC123KEYVALUE");
      String text = String.join("\n", readLines(new File(folder, Log.FILE_NAME)));
      check("a registered secret never reaches the file, at any level", !text.contains("ABC123KEYVALUE"));
      reset();
      check("clearing the secrets stops the masking (a test of the test)", "ABC123KEYVALUE".equals(Log.redact("ABC123KEYVALUE")));
   }

   // ---- What a first line may put in the log --------------------------------

   private static void testHeadingLines() throws Exception {
      java.util.List<String> known = java.util.List.of("ID", "Last", "First", "Email", "Unit");
      String[] heading = {"ID", "Last", "First", "Email", "Unit", "Extra"};
      String listed = Log.columnNames(heading, known);
      check("a heading row is listed, since column names are vocabulary: " + listed, listed.contains("Extra") && listed.contains("Email"));
      check("a heading row is recognized ignoring case and spaces", Log.looksLikeHeading(new String[]{" id ", "x"}, known));

      String[] record = {"ADULT:Smith:Alex:123", "Smith", "Alex", "alex.smith@example.test", "5551234567", "Troop", "123", "Leader notes about Alex"};
      String hidden = Log.columnNames(record, known);
      check("the first line of a file with no heading row is not listed: " + hidden,
         !hidden.contains("Smith") && !hidden.contains("Alex") && !hidden.contains("555") && !hidden.contains("@") && !hidden.contains("notes"));
      check("only how many cells it has is said", hidden.startsWith("8 cells"));
      check("a data row is not a heading", !Log.looksLikeHeading(record, known));
      check("a very long name is shown as its length", Log.columnName("x".repeat(100)).equals("<100 chars>"));
      check("an ordinary name is shown", "Extra".equals(Log.columnName("Extra")));
      check("a name's line breaks are cleaned", "a?b".equals(Log.columnName("a\nb")));
   }

   // ---- Settings and the clock, which only the log notices ------------------

   private static void testImplausibleSettings() {
      check("the default settings are plausible", new ConfigRecord().implausibleSettings().isEmpty());

      ConfigRecord yellowAtRed = new ConfigRecord();
      yellowAtRed.setValue("FinalYellowMins", "45");
      List<String> problems = yellowAtRed.implausibleSettings();
      check("a yellow stage not below its red one is reported: " + problems,
         problems.size() == 1 && problems.get(0).contains("FinalYellowMins 45 is not below FinalRedMins 45"));

      ConfigRecord junk = new ConfigRecord();
      junk.setValue("ProjectYellowMins", "soon");
      junk.setValue("ConveneRedMins", "0");
      junk.setValue("FinalRedMins", "-5");
      List<String> junkProblems = junk.implausibleSettings();
      check("a value that is not a whole number of minutes of 1 or more is reported by name: " + junkProblems,
         junkProblems.size() == 3 && junkProblems.stream().anyMatch(p -> p.startsWith("ProjectYellowMins is soon"))
            && junkProblems.stream().anyMatch(p -> p.startsWith("ConveneRedMins is 0"))
            && junkProblems.stream().anyMatch(p -> p.startsWith("FinalRedMins is -5")));
   }

   private static void testClockMovedNote() throws Exception {
      Class<?> monitor = Class.forName("shkc.core.EventMonitor");
      Method note = monitor.getDeclaredMethod("clockMovedNote", long.class, long.class);
      note.setAccessible(true);
      long beat = (long) packageConstantOf(monitor, "HEARTBEAT_MILLIS");
      long t0 = 1_700_000_000_000L;
      check("a heartbeat on time says nothing", note.invoke(null, t0, t0 + beat) == null);
      check("a few seconds early or late says nothing", note.invoke(null, t0, t0 + beat + 20_000L) == null
         && note.invoke(null, t0, t0 + beat - 20_000L) == null);
      String slept = (String) note.invoke(null, t0, t0 + beat + 7L * 60_000L);
      check("a laptop asleep for 7 minutes is noticed, which the old 10 minute test missed: " + slept,
         slept != null && slept.contains("was not running for about 7 minutes"));
      String back = (String) note.invoke(null, t0, t0 + beat - 3L * 60_000L);
      check("a clock set back is noticed: " + back, back != null && back.contains("clock moved back about 3 minutes"));
      String brief = (String) note.invoke(null, t0, t0 + beat + 45_000L);
      check("a short jump is said in seconds: " + brief, brief != null && brief.contains("about 45 seconds"));
   }

   private static Object packageConstantOf(Class<?> owner, String name) throws Exception {
      Field field = owner.getDeclaredField(name);
      field.setAccessible(true);
      return field.get(null);
   }

   // ---- Helpers ----------------------------------------------------------

   private static String scrub(String text) throws Exception {
      Method method = Log.class.getDeclaredMethod("scrub", String.class);
      method.setAccessible(true);
      try {
         return (String) method.invoke(null, text);
      } catch (InvocationTargetException failure) {
         throw new RuntimeException(failure.getCause());
      }
   }

   private static Object packageConstant(String name) throws Exception {
      Field field = Log.class.getDeclaredField(name);
      field.setAccessible(true);
      return field.get(null);
   }

   private static Object privateConstant(String name) throws Exception {
      return packageConstant(name);
   }

   /** Back to a log with no file, no held lines and the switches off. */
   private static void reset() {
      try {
         Method method = Log.class.getDeclaredMethod("detachFileForTests");
         method.setAccessible(true);
         method.invoke(null);
      } catch (ReflectiveOperationException failure) {
         throw new RuntimeException("Log.detachFileForTests is missing: " + failure);
      }
      try {
         Method method = Log.class.getDeclaredMethod("clearSecretsForTests");
         method.setAccessible(true);
         method.invoke(null);
      } catch (ReflectiveOperationException failure) {
         throw new RuntimeException("Log.clearSecretsForTests is missing: " + failure);
      }
      Log.setDebugToFile(false);
      Log.setVerbose(false);
      Log.clearContext();
   }

   private static File newFolder(String name) {
      File folder = new File(root.toFile(), name);
      folder.mkdirs();
      return folder;
   }

   private static void writeFileOfSize(File file, long size, String firstLine) throws IOException {
      byte[] head = (firstLine + "\n").getBytes(StandardCharsets.UTF_8);
      byte[] chunk = new byte[8192];
      java.util.Arrays.fill(chunk, (byte) 'z');
      try (java.io.OutputStream out = Files.newOutputStream(file.toPath())) {
         out.write(head);
         long written = head.length;
         while (written < size) {
            int count = (int) Math.min(chunk.length, size - written);
            out.write(chunk, 0, count);
            written += count;
         }
      }
   }

   private static List<String> readLines(File file) throws IOException {
      if (!file.exists()) {
         return new ArrayList<>();
      }
      return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
   }

   private static int countStackLines(List<String> lines) {
      int count = 0;
      for (String line : lines) {
         if (line.startsWith("    ")) {
            count++;
         }
      }
      return count;
   }

   /** Runs {@code action} with the console (out and err) captured, and returns what it printed. */
   private static String capture(Runnable action) {
      PrintStream savedOut = System.out;
      PrintStream savedErr = System.err;
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      System.setOut(capture);
      System.setErr(capture);
      try {
         action.run();
      } finally {
         System.setOut(savedOut);
         System.setErr(savedErr);
      }
      return buffer.toString(StandardCharsets.UTF_8);
   }

   private interface Action {
      void run() throws Exception;
   }

   /** Runs {@code action} with the console swallowed. */
   private static void quietly(Action action) {
      capture(() -> {
         try {
            action.run();
         } catch (Exception failure) {
            throw new RuntimeException(failure);
         }
      });
   }

   private static void deleteTree(Path folder) throws IOException {
      if (!Files.exists(folder)) {
         return;
      }
      try (Stream<Path> paths = Files.walk(folder)) {
         paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
      }
   }

   private static void check(String name, boolean condition) {
      if (!condition) {
         failures++;
         System.out.println("FAIL: " + name);
      }
   }
}
