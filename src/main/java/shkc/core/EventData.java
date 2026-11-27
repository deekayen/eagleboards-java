package shkc.core;

import java.io.File;

/**
 * Everything the request handlers share about the event being run: the data
 * folder, the six record files, the lock that guards them and the Undo log.
 *
 * <p>Handlers receive one {@code EventData} instead of reaching into the
 * scheduler, so a handler (or a rule such as "which room is this board in?")
 * can be exercised in a test by building an {@code EventData} over temporary
 * files, with no web server running.
 *
 * <p>Every handler that reads or changes records does so inside
 * {@code synchronized (data.getLock())}.
 */
public class EventData {
   private final Object lock = new Object();
   private final File dataRoot;
   private final DataRecordFile<ScoutRecord> scouts;
   private final DataRecordFile<ScoutRecord> scoutsScheduled;
   private final DataRecordFile<AdultRecord> adults;
   private final DataRecordFile<AdultRecord> adultHistory;
   private final DataRecordFile<RoomRecord> rooms;
   private final DataRecordFile<ConfigRecord> config;
   private final UndoLog undoLog = new UndoLog();

   /**
    * @param dataRoot        this event's folder, named for its date
    * @param scouts          the youth who have signed in today
    * @param scoutsScheduled the youth who reserved a slot beforehand (pre-registrations)
    * @param adults          the adults who have signed in today
    * @param adultHistory    every adult who has ever signed in, used to pre-fill the form
    * @param rooms           the rooms boards can sit in
    * @param config          the settings (a single row)
    */
   public EventData(File dataRoot, DataRecordFile<ScoutRecord> scouts, DataRecordFile<ScoutRecord> scoutsScheduled,
      DataRecordFile<AdultRecord> adults, DataRecordFile<AdultRecord> adultHistory,
      DataRecordFile<RoomRecord> rooms, DataRecordFile<ConfigRecord> config) {
      this.dataRoot = dataRoot;
      this.scouts = scouts;
      this.scoutsScheduled = scoutsScheduled;
      this.adults = adults;
      this.adultHistory = adultHistory;
      this.rooms = rooms;
      this.config = config;
   }

   /** The monitor every handler locks while it reads or changes records. */
   public Object getLock() {
      return this.lock;
   }

   public File getDataRoot() {
      return this.dataRoot;
   }

   public DataRecordFile<ScoutRecord> getScouts() {
      return this.scouts;
   }

   public DataRecordFile<ScoutRecord> getScoutsScheduled() {
      return this.scoutsScheduled;
   }

   public DataRecordFile<AdultRecord> getAdults() {
      return this.adults;
   }

   public DataRecordFile<AdultRecord> getAdultHistory() {
      return this.adultHistory;
   }

   public DataRecordFile<RoomRecord> getRooms() {
      return this.rooms;
   }

   public DataRecordFile<ConfigRecord> getConfig() {
      return this.config;
   }

   public UndoLog getUndoLog() {
      return this.undoLog;
   }

   /**
    * Whether {@code records} is scouts.csv or scouts_scheduled.csv, whose
    * phone numbers are never served (SPEC.md D-8). The read handlers serve the
    * adult files too, and an adult's number still goes out.
    */
   public boolean isYouthFile(DataRecordFile<?> records) {
      return records == this.scouts || records == this.scoutsScheduled;
   }

   /**
    * The file that holds records of an Undo record type.
    *
    * @param recordType {@code "Scout"}, {@code "Room"} or {@code "Adult"}
    * @return that file, or {@code null} for any other type
    */
   public DataRecordFile<? extends DataRecord> recordsFor(String recordType) {
      if ("Scout".equals(recordType)) {
         return this.scouts;
      } else if ("Room".equals(recordType)) {
         return this.rooms;
      } else if ("Adult".equals(recordType)) {
         return this.adults;
      }
      return null;
   }

   /**
    * Finds the room card a youth's board is sitting in.
    *
    * @return the room, or {@code null} when no room card matches any more
    */
   public RoomRecord findRoomOfBoard(ScoutRecord scout) {
      // By name first.
      for (RoomRecord candidateRoom : this.rooms.getRecords()) {
         if (candidateRoom.getRoom().equals(scout.getRoom())) {
            return candidateRoom;
         }
      }

      // A room renamed under the board no longer matches by name, but its
      // record still names the scout. Without this it stayed "occupied"
      // by a board that had finished, and nothing could be seated there.
      for (RoomRecord candidateRoom : this.rooms.getRecords()) {
         if (candidateRoom.getScout().length() > 0 && candidateRoom.getScout().equals(scout.getFullName())) {
            return candidateRoom;
         }
      }
      return null;
   }

   /**
    * Frees every adult committed to a board's room.
    *
    * <p>The adults are released by the room name the SCOUT holds, not through
    * the room record. If the room was renamed or deleted on the Admin page
    * while the board sat in it, the record is gone, but the review still
    * happened: refusing there lost the result, and the Reset that followed
    * left every member committed to a room that no longer existed. An empty
    * name and {@link RoomMarker#NO_ROOM} are never a board's room (the latter
    * marks the adults who have gone home), so they release nobody.
    *
    * @param boardRoom   the room name the scout holds
    * @param undoOrNull  where to record each change for Undo, or {@code null} for an action (Reset) that offers no Undo
    * @return how many adults were freed
    */
   public int releaseAdultsFromRoom(String boardRoom, UndoLog.Builder undoOrNull) {
      if (boardRoom.length() == 0 || RoomMarker.NO_ROOM.equals(boardRoom)) {
         Log.info("release-adults: room=" + Log.safeValue("Room", boardRoom) + " is never a board's room -> released nobody");
         return 0;
      }
      int released = 0;
      StringBuilder releasedIds = new StringBuilder();
      for (AdultRecord adult : this.adults.getRecords()) {
         if (adult.getRoom().equals(boardRoom)) {
            String oldAdultRoom = adult.getRoom();
            adult.setRoom("");
            if (undoOrNull != null) {
               undoOrNull.field("Adult", adult, "Room", oldAdultRoom);
            }
            released++;
            releasedIds.append(released > 1 ? ", " : "").append(adult.getID());
         }
      }
      if (released == 0) {
         Log.info("release-adults: room=" + Log.safeValue("Room", boardRoom) + " released=0 (no adult was committed to that room name)"
            + " undoable=" + (undoOrNull != null));
      } else {
         Log.info("release-adults: room=" + Log.safeValue("Room", boardRoom) + " released=" + released + " adults=[" + releasedIds + "]"
            + " (room: free again) undoable=" + (undoOrNull != null));
      }
      return released;
   }

   /**
    * A youth as the event log names one: the record ID, then the registration
    * number in parentheses (for example {@code Scout:Doe:Jane:123 (P3)}). Both
    * are identifiers, never contact details.
    */
   static String scoutLabel(ScoutRecord scout) {
      String regNum = scout.getRegNum();
      return Log.clean(scout.getID()) + (regNum == null || regNum.isEmpty() ? "" : " (" + Log.clean(regNum) + ")");
   }
}
