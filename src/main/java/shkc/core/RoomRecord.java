package shkc.core;

import java.util.Map;

/**
 * One interview room: the row in the room file. {@code Scout} and
 * {@code Leaders} name who is in it now; {@code BoardType} says what kind of
 * board the room is for.
 */
public class RoomRecord extends DataRecord {
   public static final String[] COLUMNS = new String[]{"Type", "ID", "Room", "BoardType", "Scout", "Leaders", "RegTime"};

   public RoomRecord() {
      this(null);
   }

   public RoomRecord(Map<String, ?> values) {
      super("ROOM", COLUMNS, values);
   }

   /** Variant for a record kind other than ROOM: its ID is the type and room name. Not used at present. */
   public RoomRecord(String recordType, String[] columns, Map<String, ?> values) {
      super(recordType, columns, values);
      String id = this.getType() + ":" + this.getRoom();
      this.put(COLUMN_ID, id);
   }

   public String getRoom() {
      return this.getValue("Room");
   }

   public String getScout() {
      return this.getValue("Scout");
   }

   public void setScout(String scoutName) {
      this.put("Scout", scoutName);
   }

   public String getLeaders() {
      return this.getValue("Leaders");
   }

   public void setLeaders(String leaderNames) {
      this.put("Leaders", leaderNames);
   }

   public static class Factory implements DataRecord.Factory {
      @Override
      public DataRecord newInstance() {
         return new RoomRecord();
      }

      @Override
      public String[] getColumns() {
         return RoomRecord.COLUMNS;
      }
   }
}
