package shkc.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Writes the event's vital signs to the event log: once at startup and shutdown
 * and every few minutes in between.
 *
 * <p>Most problems found at an event are first noticed some time after they
 * happened ("the board was stuck a while ago"). A line of counts every few
 * minutes shows what state the event was in at any moment, and a gap in the
 * lines shows when the program (or the laptop, asleep with its lid shut) was
 * not running at all. The lines hold counts, record IDs and room names, never
 * a name or contact detail.
 *
 * <p>The monitor also checks that the boards and the rooms agree: a scout
 * seated with no room card, a card holding a scout with no board, an adult
 * committed to a room nobody holds. Counts alone cannot show these, and the
 * Admin tables can create every one of them. A disagreement is warned about
 * when it first appears and noted when it clears, not on every heartbeat.
 */
final class EventMonitor {
   /** How often the heartbeat is written. */
   static final long HEARTBEAT_MILLIS = 5L * 60L * 1000L;

   /** The heartbeat arriving this much earlier or later than due means the clock moved or the machine slept. */
   static final long CLOCK_DRIFT_MILLIS = 30L * 1000L;

   /** Waiting longer than this for the data lock, which every request holds while it works, is worth a line. */
   static final long SLOW_LOCK_MILLIS = 1000L;

   private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

   private final EventData data;
   private final long startedMillis = System.currentTimeMillis();

   /** The disagreements found by the last check, so only a change is reported. Guarded by {@code this}. */
   private Set<String> knownProblems = new TreeSet<>();

   /** How long the last snapshot waited for the data lock. */
   private volatile long lastLockWaitMillis = 0L;

   EventMonitor(EventData data) {
      this.data = data;
   }

   /** Starts the heartbeat on a daemon thread, which never keeps the program alive. */
   void start() {
      Thread heartbeat = new Thread(this::run, "heartbeat");
      heartbeat.setDaemon(true);
      heartbeat.start();
   }

   private void run() {
      long lastBeat = System.currentTimeMillis();
      while (true) {
         try {
            Thread.sleep(HEARTBEAT_MILLIS);
         } catch (InterruptedException stopping) {
            return;
         }
         long now = System.currentTimeMillis();
         warnIfClockMoved(lastBeat, now);
         lastBeat = now;
         logSnapshot("heartbeat");
         checkConsistency("heartbeat");
      }
   }

   /**
    * The beat is due {@link #HEARTBEAT_MILLIS} after the last. A wall clock that
    * shows far more means the thread was not scheduled (the computer slept, or
    * the clock was set forward), and far less means the clock was set back.
    * Room timers count from wall-clock times, so they are off by the same amount
    * either way. The comparison is wall clock only: a monotonic clock may or may
    * not count the time asleep, depending on the platform.
    */
   static String clockMovedNote(long lastBeatMillis, long nowMillis) {
      long drift = (nowMillis - lastBeatMillis) - HEARTBEAT_MILLIS;
      if (Math.abs(drift) <= CLOCK_DRIFT_MILLIS) {
         return null;
      }
      String size = Math.abs(drift) >= 120000L ? Math.abs(drift) / 60000L + " minutes" : Math.abs(drift) / 1000L + " seconds";
      String lastAt = CLOCK.format(Instant.ofEpochMilli(lastBeatMillis));
      return drift > 0
         ? "the program was not running for about " + size + " since the last heartbeat at " + lastAt
            + " (the computer slept, or its clock was set forward), so room timers read that much long"
         : "the clock moved back about " + size + " since the last heartbeat at " + lastAt
            + " (it was set back), so room timers read that much short";
   }

   private static void warnIfClockMoved(long lastBeatMillis, long nowMillis) {
      String note = clockMovedNote(lastBeatMillis, nowMillis);
      if (note != null) {
         Log.warn(note);
      }
   }

   /** Writes the counts that describe where the event stands, prefixed with why they are being written. */
   void logSnapshot(String reason) {
      try {
         Log.info(reason + ": " + snapshot());
         long waited = this.lastLockWaitMillis;
         if (waited >= SLOW_LOCK_MILLIS) {
            Log.warn("the " + reason + " snapshot waited " + waited + " ms for the data lock, so a request was holding it that long");
         }
      } catch (RuntimeException failure) {
         Log.warn("could not take the " + reason + " snapshot", failure);
      }
   }

   /** The state of the event as counts, e.g. {@code youth 14 [Completed 6, Registered 8], rooms 3/12 in use, ...}. */
   String snapshot() {
      Runtime runtime = Runtime.getRuntime();
      long usedMegabytes = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
      long maxMegabytes = runtime.maxMemory() / (1024L * 1024L);
      long upMinutes = (System.currentTimeMillis() - this.startedMillis) / 60000L;
      String state;
      long waitStarted = System.nanoTime();
      synchronized (this.data.getLock()) {
         this.lastLockWaitMillis = (System.nanoTime() - waitStarted) / 1_000_000L;
         Map<String, Integer> youthByStatus = new TreeMap<>();
         for (ScoutRecord scout : this.data.getScouts().getRecords()) {
            youthByStatus.merge(blankAs(scout.getStatus(), "(none)"), 1, Integer::sum);
         }
         int roomsInUse = 0;
         for (RoomRecord room : this.data.getRooms().getRecords()) {
            if (room.getScout().length() > 0) {
               roomsInUse++;
            }
         }
         int committed = 0;
         int goneHome = 0;
         for (AdultRecord adult : this.data.getAdults().getRecords()) {
            String room = adult.getRoom();
            if (RoomMarker.NO_ROOM.equals(room)) {
               goneHome++;
            } else if (room != null && room.length() > 0) {
               committed++;
            }
         }
         state = "youth " + this.data.getScouts().getRecords().size() + " " + youthByStatus
            + ", rooms " + roomsInUse + "/" + this.data.getRooms().getRecords().size() + " in use"
            + ", adults " + this.data.getAdults().getRecords().size()
            + " (" + committed + " on a board, " + goneHome + " gone home)"
            + ", scheduled " + this.data.getScoutsScheduled().getRecords().size()
            + ", history " + this.data.getAdultHistory().getRecords().size();
      }
      return state + "; heap " + usedMegabytes + "/" + maxMegabytes + " MB, threads "
         + Thread.activeCount() + ", up " + upMinutes + " min";
   }

   /**
    * Writes which board sits in which room, by record ID, and checks the boards and rooms agree. Written at
    * startup, so after a restart the log says what the program came back with.
    */
   void logBoards(String reason) {
      try {
         String boards;
         synchronized (this.data.getLock()) {
            boards = boardsInRooms();
         }
         Log.info(reason + " boards: " + boards);
         checkConsistency(reason);
      } catch (RuntimeException failure) {
         Log.warn("could not list the boards for the " + reason + " snapshot", failure);
      }
   }

   // Caller holds the data lock.
   private String boardsInRooms() {
      List<String> boards = new ArrayList<>();
      for (ScoutRecord scout : this.data.getScouts().getRecords()) {
         if (ScoutStatus.holdsRoom(scout.getStatus())) {
            boards.add("room " + Log.clean(scout.getRoom()) + ": " + EventData.scoutLabel(scout) + " " + scout.getStatus()
               + ", chair " + shownId(scout.getValue("BoardChairID")) + ", members [" + shownId(scout.getValue("BoardMembersIDs")) + "]");
         }
      }
      return boards.isEmpty() ? "(none seated)" : String.join("; ", boards);
   }

   private static String shownId(String ids) {
      return ids == null || ids.isEmpty() ? "(none)" : Log.clean(ids);
   }

   /**
    * Warns about each way the boards and rooms disagree that was not there at the last check, and notes each
    * that has cleared. Read-only: nothing is repaired.
    */
   synchronized void checkConsistency(String reason) {
      try {
         Set<String> current;
         synchronized (this.data.getLock()) {
            current = findDisagreements();
         }
         for (String problem : current) {
            if (!this.knownProblems.contains(problem)) {
               Log.warn("consistency (" + reason + "): " + problem);
            }
         }
         for (String cleared : this.knownProblems) {
            if (!current.contains(cleared)) {
               Log.info("consistency (" + reason + "): no longer true: " + cleared);
            }
         }
         this.knownProblems = current;
      } catch (RuntimeException failure) {
         Log.warn("could not check that the boards and rooms agree", failure);
      }
   }

   // Caller holds the data lock. IDs and room names only.
   private Set<String> findDisagreements() {
      Set<String> problems = new TreeSet<>();
      Map<String, List<String>> boardsByRoom = new TreeMap<>();
      for (ScoutRecord scout : this.data.getScouts().getRecords()) {
         if (!ScoutStatus.holdsRoom(scout.getStatus())) {
            continue;
         }
         String who = Log.clean(scout.getID()) + " (" + scout.getStatus() + ")";
         String room = scout.getRoom();
         if (room.isEmpty() || RoomMarker.NO_ROOM.equals(room)) {
            problems.add(who + " is on a board but holds no room");
            continue;
         }
         boardsByRoom.computeIfAbsent(room, k -> new ArrayList<>()).add(who);
         RoomRecord card = this.data.findRoomOfBoard(scout);
         if (card == null) {
            problems.add(who + " sits in room " + Log.clean(room) + ", which has no room card");
         } else if (!card.getScout().equals(scout.getFullName())) {
            problems.add(who + " sits in room " + Log.clean(card.getRoom()) + ", whose card does not name this scout");
         }
      }
      for (Map.Entry<String, List<String>> held : boardsByRoom.entrySet()) {
         if (held.getValue().size() > 1) {
            problems.add("room " + Log.clean(held.getKey()) + " is held by " + held.getValue().size() + " boards: " + held.getValue());
         }
      }
      for (RoomRecord card : this.data.getRooms().getRecords()) {
         if (!card.getScout().isEmpty() && !boardsByRoom.containsKey(card.getRoom())) {
            problems.add("room card " + Log.clean(card.getRoom()) + " names a scout, but no board is seated in that room");
         }
      }
      for (AdultRecord adult : this.data.getAdults().getRecords()) {
         String room = adult.getRoom();
         if (room != null && !room.isEmpty() && !RoomMarker.NO_ROOM.equals(room) && !boardsByRoom.containsKey(room)) {
            problems.add(Log.clean(adult.getID()) + " is committed to room " + Log.clean(room) + ", which no board holds");
         }
      }
      return problems;
   }

   private static String blankAs(String value, String replacement) {
      return value == null || value.isEmpty() ? replacement : value;
   }
}
