package shkc.core;

import java.util.Map;

public class ScoutRecord extends PersonRecord {
   // The youth file's columns (SPEC.md D-1). Phone and DOB stay, so files
   // still move between versions, though a new youth's are written empty and
   // one already on file is never served (D-7, D-8).
   public static final String[] COLUMNS = new String[]{
      "Type",
      "ID",
      "RegNum",
      "Last",
      "First",
      "Email",
      "Phone",
      "UnitType",
      "Unit",
      "UnitName",
      "DOB",
      "BoardType",
      "Leader",
      "RegTime",
      "LastUpdateTime",
      "Flags",
      "Room",
      "Status",
      "Result",
      "BoardChair",
      "BoardChairID",
      "BoardMembers",
      "BoardMembersIDs",
      "Notes"
   };

   public ScoutRecord() {
      this(null);
   }

   public ScoutRecord(Map values) {
      super("SCOUT", COLUMNS, values);
      this.setDefaults();
      this.updateFields(true);
   }

   @Override
   protected void setDefaults() {
      this.setIfNotSet("Status", "");
   }

   private void setIfNotSet(String column, String defaultValue) {
      String current = this.get(column);
      if (current == null || current.trim().length() == 0) {
         this.put(column, defaultValue);
      }
   }

   public void updateFrom(ScoutRecord source, String[] columns) {
      for (String column : columns) {
         String value = source.get(column);
         if (value != null && value.length() > 0) {
            this.put(column, value);
         }
      }
   }

   public String getRegNum() {
      return this.getValue("RegNum");
   }

   public void setRegNum(String regNum) {
      this.setValue("RegNum", regNum);
   }

   public String getBoardType() {
      return this.getValue("BoardType");
   }

   public String getStatus() {
      return this.getValue("Status");
   }

   public void setStatus(String status) {
      this.setValue("Status", status);
   }

   public void setBoardChair(String chairName) {
      this.setValue("BoardChair", chairName);
   }

   public void setBoardChairID(String chairId) {
      this.setValue("BoardChairID", chairId);
   }

   public void setBoardMembers(String memberNames) {
      this.setValue("BoardMembers", memberNames);
   }

   public void setBoardMemberIDs(String memberIds) {
      this.setValue("BoardMembersIDs", memberIds);
   }

   public void setResult(String result) {
      this.setValue("Result", result);
   }

   public void setNotes(String notes) {
      this.setValue("Notes", notes);
   }

   public static class Factory implements DataRecord.Factory {
      @Override
      public DataRecord newInstance() {
         return new ScoutRecord();
      }

      @Override
      public String[] getColumns() {
         return ScoutRecord.COLUMNS;
      }
   }
}
