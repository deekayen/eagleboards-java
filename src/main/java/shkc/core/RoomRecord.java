package shkc.core;

import java.util.Map;

public class RoomRecord extends DataRecord {
   public static final String ROOM = "Room";
   public static final String BOARD_TYPE = "BoardType";
   public static final String SCOUT = "Scout";
   public static final String LEADERS = "Leaders";
   public static final String[] COLUMNS = new String[]{"Type", "ID", "Room", "BoardType", "Scout", "Leaders", "RegTime"};

   public RoomRecord() {
      this(null);
   }

   public RoomRecord(Map var1) {
      super("ROOM", COLUMNS, var1);
   }

   public RoomRecord(String var1, String[] var2, Map var3) {
      super(var1, var2, var3);
      String var4 = this.getType() + ":" + this.getRoom();
      this.put("ID", var4);
   }

   public String getRoom() {
      return this.getValue("Room");
   }

   public String getScout() {
      return this.getValue("Scout");
   }

   public void setScout(String var1) {
      this.put("Scout", var1);
   }

   public String getLeaders() {
      return this.getValue("Leaders");
   }

   public void setLeaders(String var1) {
      this.put("Leaders", var1);
   }

   public String getBoardType() {
      return this.getValue("BoardType");
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
