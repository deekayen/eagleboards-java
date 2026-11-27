package shkc.core;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

/**
 * Logging for the whole program: a console channel and an event log file.
 *
 * <h2>The event log file</h2>
 * <p>Once {@link #attachFile} has been called (the scheduler does it as soon as it
 * knows the event's data folder), every {@link #info}, {@link #warn} and
 * {@link #error} line is appended to {@value #FILE_NAME} in that folder, whatever
 * flags the program was started with. Each line carries a timestamp, a level, the
 * request it belongs to (see {@link #setContext}) and the thread, so what happened
 * to one tablet's request can be followed through the file. The file is flushed
 * after every line, so a crash or a pulled power cable loses nothing already
 * logged, and it rolls over by size, so an event cannot fill the disk.
 *
 * <p><strong>What may go in it.</strong> The log is participant data, handled
 * exactly like the CSV files beside it: it lives in the event's folder, which
 * is never committed, and it is not pasted into an issue unless a maintainer
 * has looked it over. It is meant to name people, since a record's ID is
 * {@code Type:Last:First:Unit} and "why was this adult refused" cannot be
 * answered without one. So refer to a person by record ID (or, for a youth,
 * the registration number as well), and to a board by its room. What never goes
 * in is what diagnosis does not need and a leak would hurt most: email
 * addresses, phone numbers, birthdates, free-text notes and the SignUpGenius
 * key. Use {@link #safeParams} for request parameters and {@link #redact} for
 * anything that might hold a URL or a key; do not concatenate a request
 * parameter into a log line by hand. As a backstop, every INFO, WARN and ERROR
 * line is scrubbed of anything shaped like an email address, a phone number or
 * a key before it is written (see {@link #scrub}).
 *
 * <h2>The console</h2>
 * <p>{@link #verbose} prints to standard output only after the {@code -v} flag,
 * exactly as it always did (and may include personal details and the key's
 * URL, which is why it is off by default). {@link #warn} and {@link #error}
 * also print one line to the console so an operator watching it sees them;
 * {@link #info} does not, so the console stays as quiet as it was.
 * With {@code -debug}, verbose messages also go to the file, marked DEBUG.
 *
 * <p>This class only holds static state and cannot be instantiated. Nothing in
 * it throws: a log that cannot be written must never take a request down.
 */
public final class Log {
   /** The log file's name inside the event's data folder. */
   public static final String FILE_NAME = "eagleboards.log";

   /** A file past this size is rolled over to {@code eagleboards.log.1}, and so on. */
   static final long MAX_FILE_BYTES = 5L * 1024L * 1024L;

   /** How many rolled-over files are kept besides the live one. */
   static final int KEPT_FILES = 5;

   /** Lines logged before the file is attached are held here, up to this many. */
   private static final int MAX_HELD_LINES = 1000;

   /** A message longer than this is cut, so one huge value cannot flood the file. */
   private static final int MAX_MESSAGE_CHARS = 4000;

   /**
    * How much past {@link #MAX_MESSAGE_CHARS} is scrubbed before the cut, so an
    * email address or phone number that straddles the cut is whole when it is
    * masked. The text scrubbed stays bounded, since the masking patterns are
    * slow on a very long unbroken run.
    */
   private static final int SCRUB_MARGIN_CHARS = 256;

   /** A column name longer than this is not a column name, and is logged as its length. */
   private static final int MAX_COLUMN_NAME_CHARS = 40;

   /** After a rollover fails, the next try waits this long, and the live file keeps growing meanwhile. */
   private static final long ROLL_RETRY_MILLIS = 60L * 1000L;

   /** Secrets shorter than this are not registered: masking a word that short would mangle ordinary text. */
   private static final int MIN_SECRET_CHARS = 6;

   private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

   /**
    * The request parameters (and record columns) whose values are written as
    * they are: identifiers (which name the person, see the class comment),
    * states and settings. {@link #safeParams} writes only the length of any
    * other value, so an email address, phone number or note is never logged.
    */
   private static final Set<String> SAFE_PARAMETERS = Set.of(
      "cmd", "ID", "Type", "RegNum", "ScoutID", "AdultID", "ChairID", "MemberIDs", "RoomID", "Room",
      "BoardType", "Status", "Result", "UnitType", "Unit", "ProjectReview", "FinalBoard", "WoodBadge",
      "Supporting", "BoardChairID", "BoardMembersIDs", "gr_id", "!nativeeditor_status", "RoomID1", "RoomID2",
      "First", "Last", "RefreshTimeSecs", "ConveneRedMins", "ProjectYellowMins", "ProjectRedMins",
      "FinalYellowMins", "FinalRedMins", "Name");

   /** Anything shaped like an email address, accented letters included. */
   private static final Pattern EMAIL_SHAPE =
      Pattern.compile("[\\p{L}\\p{N}._%+'&-]+@[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)+");

   /**
    * A North American phone number: ten digits in groups of three, three and
    * four, with any mix of spaces, dots, dashes, slashes and parentheses
    * between them, or with none. Ten digits are masked only when they are the
    * whole run, so a count, a timestamp or a longer number is left alone.
    */
   private static final Pattern PHONE_SHAPE =
      Pattern.compile("(?<![\\d-])(?:\\+?1[ .-])?\\(?\\d{3}\\)?[ .\\-/]*\\d{3}[ .\\-/]*\\d{4}(?![\\d-])");

   /**
    * A value that is a secret: {@code key=...}, {@code apikey=...}, {@code SUG_KEY=...}, {@code token=...},
    * {@code password=...} or {@code secret=...}, in a URL query, at the start of a line or after any other
    * non-word character. The name must not be the tail of a longer word, so {@code monkey=} is left alone.
    */
   private static final Pattern SECRET_QUERY_VALUE = Pattern.compile(
      "(?i)((?<![A-Za-z0-9])(?:(?:[A-Za-z0-9]+[_-])*(?:sug)?(?:api)?key|token|password|secret))=([^&\\s\"']+)");

   private static final Object FILE_LOCK = new Object();

   private static volatile boolean verbose = false;
   private static volatile boolean debugToFile = false;
   private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

   /** Values that must never be written, such as the SignUpGenius key, whatever shape the text around them has. */
   private static final Set<String> SECRETS = new CopyOnWriteArraySet<>();

   // Guarded by FILE_LOCK.
   private static File logFile = null;
   /** The file could not be opened at all; the program carries on with the console only. */
   private static boolean fileFailed = false;
   /** Writes to the open file are failing; lines wait in {@link #heldLines} until one succeeds. */
   private static boolean writeFailing = false;
   /** Lines held while the file could not be written, and lost because too many piled up. */
   private static int linesHeldDuringFailure = 0;
   private static int linesLostDuringFailure = 0;
   /** No rollover is tried before this time (epoch milliseconds), after one failed. */
   private static long nextRollAttemptMillis = 0L;
   private static boolean rollFailing = false;
   private static final Deque<String> heldLines = new ArrayDeque<>();

   private Log() {
   }

   // ------------------------------------------------------------------
   // Switches
   // ------------------------------------------------------------------

   /** Turns verbose console messages on or off. Called once from {@code main}. */
   public static void setVerbose(boolean enabled) {
      verbose = enabled;
   }

   /**
    * {@code -debug}: verbose messages also go to the log file, marked DEBUG.
    * They include personal details, so this is for reproducing one problem.
    */
   public static void setDebugToFile(boolean enabled) {
      debugToFile = enabled;
   }

   /** Whether {@code -debug} is on, so a caller can skip building an expensive message. */
   public static boolean isDebugToFile() {
      return debugToFile;
   }

   // ------------------------------------------------------------------
   // Levels
   // ------------------------------------------------------------------

   /**
    * Prints {@code message} to standard output when {@code -v} is on, and
    * writes it to the log file as DEBUG when {@code -debug} is on. May contain
    * personal details: never the only record of something that matters.
    */
   public static void verbose(Object message) {
      if (verbose) {
         System.out.println(message);
      }
      if (debugToFile) {
         write("DEBUG", String.valueOf(message), null);
      }
   }

   /** Logs a request and its parameters, the way every handler starts. */
   public static void request(HttpServletRequest request) {
      verbose(request);
      verbose(request.getParameterMap());
   }

   /** Something that happened and is worth knowing about when diagnosing an event. File only. */
   public static void info(String message) {
      write("INFO", message, null);
   }

   /** Something unexpected that the program recovered from. File and console. */
   public static void warn(String message) {
      warn(message, null);
   }

   /** Like {@link #warn(String)}, with the exception that caused it (its stack goes in the file). */
   public static void warn(String message, Throwable cause) {
      write("WARN", message, cause);
      System.out.println("WARN: " + singleLine(message) + (cause == null ? "" : " (" + cause + ")"));
   }

   /**
    * Prints an error message, then the current stack, whether or not verbose
    * is on, and writes both to the log file.
    */
   public static void error(String message) {
      System.out.println("\n\nERROR: " + message + "\n");
      Thread.dumpStack();
      write("ERROR", message, new Throwable("where the error was logged"));
   }

   /** An error with its cause: the stack trace goes to the file and the console. */
   public static void error(String message, Throwable cause) {
      write("ERROR", message, cause);
      System.out.println("ERROR: " + singleLine(message) + (cause == null ? "" : " (" + cause + ")"));
      if (cause != null) {
         cause.printStackTrace(System.out);
      }
   }

   // ------------------------------------------------------------------
   // Context: which request a line belongs to
   // ------------------------------------------------------------------

   /** Tags every line this thread logs with {@code context} (a request number) until {@link #clearContext}. */
   public static void setContext(String context) {
      CONTEXT.set(context);
   }

   /** Stops tagging this thread's lines. */
   public static void clearContext() {
      CONTEXT.remove();
   }

   // ------------------------------------------------------------------
   // Making text safe to write
   // ------------------------------------------------------------------

   /**
    * The request's parameters as {@code Name=value} pairs, in name order, with
    * the value written only for the parameters in the safe list; any other
    * is written as {@code Name=<N chars>}. This is the one way request
    * parameters reach the log: a name, email address, phone number or note
    * never does.
    */
   public static String safeParams(HttpServletRequest request) {
      try {
         return safeParams(request.getParameterMap());
      } catch (RuntimeException unreadable) {
         return "(parameters unreadable: " + unreadable.getClass().getSimpleName() + ")";
      }
   }

   /** As {@link #safeParams(HttpServletRequest)}, for an already-read parameter map. */
   public static String safeParams(Map<String, String[]> parameters) {
      StringBuilder out = new StringBuilder();
      for (Map.Entry<String, String[]> entry : new TreeMap<>(parameters).entrySet()) {
         if (out.length() > 0) {
            out.append(' ');
         }
         String name = clean(entry.getKey());
         String[] values = entry.getValue() == null ? new String[0] : entry.getValue();
         out.append(name).append('=');
         for (int i = 0; i < values.length; i++) {
            out.append(i == 0 ? "" : "|").append(safeValue(entry.getKey(), values[i]));
         }
      }
      return out.toString();
   }

   /** One value as {@link #safeParams} would write it: itself if its name is safe, else its length. */
   public static String safeValue(String name, String value) {
      if (value == null) {
         return "<null>";
      }
      if (name != null && SAFE_PARAMETERS.contains(name)) {
         return clean(value.length() > 200 ? value.substring(0, 200) + "..." : value);
      }
      return value.isEmpty() ? "<empty>" : "<" + value.length() + " chars>";
   }

   /**
    * Registers a value that must never reach the log or the console, such as
    * the SignUpGenius key: {@link #redact} then masks it wherever it appears,
    * in a URL, a service's error message or an exception, whatever the text
    * around it looks like. A null or very short value is ignored.
    */
   public static void registerSecret(String secret) {
      if (secret != null && secret.trim().length() >= MIN_SECRET_CHARS) {
         SECRETS.add(secret.trim());
      }
   }

   /** Forgets every registered secret. For tests. */
   static void clearSecretsForTests() {
      SECRETS.clear();
   }

   /**
    * {@code text} with the value of any {@code key=}, {@code token=} or
    * {@code password=} query parameter masked, and any registered secret
    * (see {@link #registerSecret}) wherever it appears.
    */
   public static String redact(String text) {
      if (text == null) {
         return null;
      }
      String masked = SECRET_QUERY_VALUE.matcher(text).replaceAll("$1=***");
      for (String secret : SECRETS) {
         masked = masked.replace(secret, "***");
      }
      return masked;
   }

   /**
    * A column name as it may be logged: itself, unless it is too long to be a
    * name, in which case only its length.
    */
   public static String columnName(String name) {
      if (name == null) {
         return "<null>";
      }
      return name.length() > MAX_COLUMN_NAME_CHARS ? "<" + name.length() + " chars>" : clean(name);
   }

   /**
    * Whether {@code names} (the first line of a file, split into cells) is a
    * heading row: at least one cell is a column this program knows, ignoring
    * case and surrounding spaces. A file whose first line is a record, a title
    * or the wrong file altogether fails this, and its cells are not logged.
    */
   public static boolean looksLikeHeading(String[] names, Collection<String> knownColumns) {
      for (String name : names) {
         String trimmed = name == null ? "" : name.trim();
         for (String known : knownColumns) {
            if (known != null && known.equalsIgnoreCase(trimmed) && !trimmed.isEmpty()) {
               return true;
            }
         }
      }
      return false;
   }

   /**
    * The first line of a file as it may be logged. Column names are
    * vocabulary and safe, but nothing guarantees the first line holds any: it
    * can be a person's record. So the cells are listed only when
    * {@link #looksLikeHeading} says it is a heading row; otherwise only how
    * many there are.
    */
   public static String columnNames(String[] names, Collection<String> knownColumns) {
      if (!looksLikeHeading(names, knownColumns)) {
         return names.length + " cells, none of them a column this version knows (the first line looks like data or a title, not a heading row)";
      }
      List<String> shown = new ArrayList<>();
      for (String name : names) {
         shown.add(columnName(name));
      }
      return shown.toString();
   }

   /**
    * The backstop applied to every INFO, WARN and ERROR line before it is
    * written: secrets masked as {@link #redact} does, and anything shaped like
    * an email address or a phone number replaced. It catches what a careless
    * log call or an exception message (one that quotes a CSV line, say) would
    * otherwise carry into the file; it is not a reason to log such things.
    */
   static String scrub(String text) {
      String scrubbed = redact(text);
      scrubbed = EMAIL_SHAPE.matcher(scrubbed).replaceAll("<email>");
      return PHONE_SHAPE.matcher(scrubbed).replaceAll("<phone>");
   }

   /**
    * The command line as it should be logged: the value after {@code -sugkey}
    * (or {@code --sugkey}) is masked, and the rest is untouched.
    */
   public static String redactArgs(String[] args) {
      String[] shown = Arrays.copyOf(args, args.length);
      for (int i = 0; i < shown.length; i++) {
         if (i < shown.length - 1 && ("-sugkey".equals(shown[i]) || "--sugkey".equals(shown[i]))) {
            shown[i + 1] = "***";
         }
         shown[i] = redactArg(shown[i]);
      }
      return String.join(" ", shown);
   }

   /**
    * One command-line argument as it may be shown: {@code -sugkey=KEY} (a
    * spelling the parser does not accept, but one an operator may type) has
    * its value masked, and so does anything shaped like {@code key=...}.
    */
   public static String redactArg(String arg) {
      if (arg == null) {
         return "null";
      }
      int equals = arg.indexOf('=');
      if (equals > 0 && arg.substring(0, equals).replace("-", "").equalsIgnoreCase("sugkey")) {
         return arg.substring(0, equals + 1) + "***";
      }
      return redact(arg);
   }

   /** {@code text} with line breaks and other control characters replaced, so one value cannot forge a log line. */
   public static String clean(String text) {
      if (text == null) {
         return "null";
      }
      StringBuilder out = new StringBuilder(text.length());
      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         out.append(c < 0x20 || c == 0x7f ? '?' : c);
      }
      return out.toString();
   }

   private static String singleLine(String message) {
      return clean(message == null ? "null" : message);
   }

   // ------------------------------------------------------------------
   // The file
   // ------------------------------------------------------------------

   /**
    * Starts writing the event log to {@value #FILE_NAME} in {@code folder}
    * (appending, so a restart during an event continues the same file) and
    * writes out any lines logged before now. If the file cannot be opened the
    * log carries on without it and says so once on the console.
    */
   public static void attachFile(File folder) {
      synchronized (FILE_LOCK) {
         File target = new File(folder, FILE_NAME);
         try {
            if (target.exists() && target.length() > MAX_FILE_BYTES) {
               roll(target);
            }
            Files.write(target.toPath(), new byte[0], StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            logFile = target;
            fileFailed = false;
            // A fresh start: lines held by an earlier failure are written below like any held line.
            writeFailing = false;
            linesHeldDuringFailure = 0;
            linesLostDuringFailure = 0;
            nextRollAttemptMillis = 0L;
            rollFailing = false;
         } catch (IOException | RuntimeException openFailure) {
            logFile = null;
            fileFailed = true;
            System.out.println("WARN: cannot write the event log " + target.getAbsolutePath() + ": " + openFailure);
         }
         flushHeldLines();
      }
   }

   /** Where the event log is being written, or null when it is not. */
   public static File getLogFile() {
      synchronized (FILE_LOCK) {
         return logFile;
      }
   }

   /** Stops writing to the file and forgets held lines. For tests: a running program never calls it. */
   static void detachFileForTests() {
      synchronized (FILE_LOCK) {
         logFile = null;
         fileFailed = false;
         writeFailing = false;
         linesHeldDuringFailure = 0;
         linesLostDuringFailure = 0;
         nextRollAttemptMillis = 0L;
         rollFailing = false;
         heldLines.clear();
      }
   }

   private static void write(String level, String message, Throwable cause) {
      try {
         String line = format(level, message, cause);
         synchronized (FILE_LOCK) {
            if (logFile == null) {
               if (!fileFailed) {
                  holdLine(line);
               }
               return;
            }
            append(line);
         }
      } catch (RuntimeException ignored) {
         // A log line must never be the thing that fails a request.
      }
   }

   // Caller holds FILE_LOCK. Keeps the newest MAX_HELD_LINES.
   private static void holdLine(String line) {
      if (heldLines.size() >= MAX_HELD_LINES) {
         heldLines.removeFirst();
         if (writeFailing) {
            linesLostDuringFailure++;
         }
      }
      heldLines.addLast(line);
   }

   private static String format(String level, String message, Throwable cause) {
      String original = message == null ? "null" : message;
      // The text is cut for size and scrubbed for privacy. Scrubbing happens
      // first, on a little more than will be kept, so an address or number that
      // straddles the cut is masked whole instead of left as a fragment; the
      // little is a bound, since the patterns are slow on a huge unbroken run.
      // DEBUG lines are what -debug asked for, personal details included, so
      // they lose only secrets; every other level is fully scrubbed.
      boolean isDebug = "DEBUG".equals(level);
      String text = boundedAndScrubbed(original, isDebug);
      String context = CONTEXT.get();
      StringBuilder line = new StringBuilder(text.length() + 64);
      line.append(TIMESTAMP.format(Instant.now())).append(' ')
         .append(String.format("%-5s", level)).append(' ')
         .append(context == null ? "-" : context).append(' ')
         .append('[').append(Thread.currentThread().getName()).append("] ")
         .append(clean(text));
      if (cause != null) {
         StringWriter trace = new StringWriter();
         cause.printStackTrace(new PrintWriter(trace));
         for (String traceLine : trace.toString().split("\\R")) {
            if (!traceLine.isEmpty()) {
               // An exception's message can quote the data it choked on.
               line.append("\n    ").append(boundedAndScrubbed(traceLine.replace('\t', ' '), isDebug));
            }
         }
      }
      return line.append('\n').toString();
   }

   /** {@code text} cut to {@link #MAX_MESSAGE_CHARS} (saying how much was cut) after masking, as {@link #format} explains. */
   private static String boundedAndScrubbed(String text, boolean secretsOnly) {
      String head = text.length() > MAX_MESSAGE_CHARS + SCRUB_MARGIN_CHARS
         ? text.substring(0, MAX_MESSAGE_CHARS + SCRUB_MARGIN_CHARS) : text;
      String masked = secretsOnly ? redact(head) : scrub(head);
      if (text.length() <= MAX_MESSAGE_CHARS) {
         return masked;
      }
      return masked.substring(0, Math.min(masked.length(), MAX_MESSAGE_CHARS)) + "... (" + (text.length() - MAX_MESSAGE_CHARS) + " more chars)";
   }

   // Caller holds FILE_LOCK.
   private static void flushHeldLines() {
      if (logFile == null) {
         heldLines.clear();
         return;
      }
      // Drained first: a line that cannot be written is held again by append,
      // and must not be picked up by this loop a second time.
      List<String> pending = new ArrayList<>(heldLines);
      heldLines.clear();
      for (String heldLine : pending) {
         append(heldLine);
      }
   }

   // Caller holds FILE_LOCK and has checked logFile is not null.
   private static void append(String line) {
      String text = line;
      long now = System.currentTimeMillis();
      if (now >= nextRollAttemptMillis) {
         try {
            if (logFile.length() > MAX_FILE_BYTES) {
               roll(logFile);
               text = format("INFO", "log continued: the file before this was rolled over to " + FILE_NAME + ".1 ("
                  + KEPT_FILES + " rolled files are kept)", null) + line;
            }
            rollFailing = false;
         } catch (IOException | RuntimeException rollFailure) {
            // The line still goes to the live file, which grows past its limit until the
            // roll works. Try again in a minute, not on every line.
            nextRollAttemptMillis = now + ROLL_RETRY_MILLIS;
            if (!rollFailing) {
               rollFailing = true;
               System.out.println("WARN: the event log " + logFile.getAbsolutePath() + " could not be rolled over, so it keeps growing: " + rollFailure);
            }
         }
      }
      try {
         if (writeFailing) {
            // Lines that waited out the failure go first, in order. One write, so a
            // failure part-way leaves nothing half written to repeat.
            StringBuilder waiting = new StringBuilder();
            for (String heldLine : heldLines) {
               waiting.append(heldLine);
            }
            int held = linesHeldDuringFailure;
            int lost = linesLostDuringFailure;
            waiting.append(format("WARN", "event log recovered: it could not be written for a while, " + held
               + " line(s) logged meanwhile are above" + (lost > 0 ? " and " + lost + " older line(s) were lost" : ""), null));
            Files.write(logFile.toPath(), (waiting.toString() + text).getBytes(StandardCharsets.UTF_8),
               StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            heldLines.clear();
            writeFailing = false;
            linesHeldDuringFailure = 0;
            linesLostDuringFailure = 0;
            System.out.println("WARN: the event log " + logFile.getAbsolutePath() + " is being written again");
         } else {
            Files.write(logFile.toPath(), text.getBytes(StandardCharsets.UTF_8),
               StandardOpenOption.CREATE, StandardOpenOption.APPEND);
         }
      } catch (IOException | RuntimeException writeFailure) {
         // Disk full, folder gone, file locked by another program. The file stays
         // attached: lines wait here (the newest ones, up to a limit) and are written
         // when a write works again. Say so once, when it starts.
         if (!writeFailing) {
            writeFailing = true;
            linesHeldDuringFailure = 0;
            linesLostDuringFailure = 0;
            System.out.println("WARN: the event log " + logFile.getAbsolutePath() + " cannot be written, lines are held until it can: " + writeFailure);
         }
         linesHeldDuringFailure++;
         holdLine(text);
      }
   }

   /** eagleboards.log.4 becomes .5, .3 becomes .4, ... and the live file becomes .1. The oldest is dropped. */
   private static void roll(File live) throws IOException {
      File oldest = new File(live.getPath() + "." + KEPT_FILES);
      Files.deleteIfExists(oldest.toPath());
      for (int n = KEPT_FILES - 1; n >= 1; n--) {
         File from = new File(live.getPath() + "." + n);
         if (from.exists()) {
            Files.move(from.toPath(), new File(live.getPath() + "." + (n + 1)).toPath(), StandardCopyOption.REPLACE_EXISTING);
         }
      }
      Files.move(live.toPath(), new File(live.getPath() + ".1").toPath(), StandardCopyOption.REPLACE_EXISTING);
   }
}
