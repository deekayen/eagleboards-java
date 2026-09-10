package shkc.core;

import java.util.Map;

public class RoomRecord extends DataRecord {
   public static final String[] COLUMNS = new String[]{"Type", "ID", "Room", "BoardType", "Scout", "Leaders", "RegTime"};

   public RoomRecord() {
      this(null);
   }

   public RoomRecord(Map values) {
      super("ROOM", COLUMNS, values);
   }

   public RoomRecord(String recordType, String[] columns, Map values) {
      super(recordType, columns, values);
      String id = this.getType() + ":" + this.getRoom();
      this.put("ID", id);
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
