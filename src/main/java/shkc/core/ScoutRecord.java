package shkc.core;

import java.util.Map;

public class ScoutRecord extends PersonRecord {
   public static final String ADULT_SCOUT_RATIO = "AdultScoutRatio";
   public static final String REG_NUM = "RegNum";
   public static final String DOB = "DOB";
   public static final String BOARD_TYPE = "BoardType";
   public static final String LEADER = "Leader";
   public static final String STATUS = "Status";
   public static final String RESULT = "Result";
   public static final String BOARD_CHAIR_ID = "BoardChairID";
   public static final String BOARD_CHAIR = "BoardChair";
   public static final String BOARD_MEMBER_IDS = "BoardMembersIDs";
   public static final String BOARD_MEMBERS = "BoardMembers";
   public static final String NOTES = "Notes";
   public static final String[] COLUMNS = new String[]{
      "Type",
      "ID",
      "RegNum",
      "Last",
      "First",
      "AdultScoutRatio",
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
   public static final String STATUS_REGISTERED = "Registered";
   public static final String STATUS_VERIFIED = "Verified";
   public static final String STATUS_SEATED = "Seated";
   public static final String STATUS_IN_PROGRESS = "InProgress";
   public static final String STATUS_COMPLETED = "Completed";
   public static final String STATUS_POSTPONED = "Postponed";
   public static final String RESULT_NO_RESULT = "";
   public static final String RESULT_APPROVED = "Approved";
   public static final String RESULT_SUSPENDED = "Suspended";
   public static final String RESULT_NOT_APPROVED = "NotApproved";

   public ScoutRecord() {
      this(null);
   }

   public ScoutRecord(Map var1) {
      super("SCOUT", COLUMNS, var1);
      this.setDefaults();
      this.updateFields(true);
   }

   @Override
   public void updateFields(boolean var1) {
      super.updateFields(var1);
   }

   @Override
   protected void setDefaults() {
      this.setIfNotSet("Status", "");
   }

   private void setIfNotSet(String var1, String var2) {
      String var3 = this.get(var1);
      if (var3 == null || var3.trim().length() == 0) {
         this.put(var1, var2);
      }
   }

   public ScoutRecord clone() {
      return new ScoutRecord(this);
   }

   public void updateFrom(ScoutRecord var1, String[] var2) {
      for (String var6 : var2) {
         String var7 = var1.get(var6);
         if (var7 != null && var7.length() > 0) {
            this.put(var6, var7);
         }
      }
   }

   public String getDOB() {
      return this.getValue("DOB");
   }

   public String getRegNum() {
      return this.getValue("RegNum");
   }

   public void setRegNum(String var1) {
      this.setValue("RegNum", var1);
   }

   public String getLeader() {
      return this.getValue("Leader");
   }

   public String getBoardType() {
      return this.getValue("BoardType");
   }

   public String getStatus() {
      return this.getValue("Status");
   }

   public void setStatus(String var1) {
      this.setValue("Status", var1);
   }

   public String getBoardChair() {
      return this.getValue("BoardChair");
   }

   public void setBoardChair(String var1) {
      this.setValue("BoardChair", var1);
   }

   public String getBoardChairID() {
      return this.getValue("BoardChairID");
   }

   public void setBoardChairID(String var1) {
      this.setValue("BoardChairID", var1);
   }

   public String getBoardMembers() {
      return this.getValue("BoardMembers");
   }

   public void setBoardMembers(String var1) {
      this.setValue("BoardMembers", var1);
   }

   public String getBoardMemberIDs() {
      return this.getValue("BoardMembersIDs");
   }

   public void setBoardMemberIDs(String var1) {
      this.setValue("BoardMembersIDs", var1);
   }

   public String getResult() {
      return this.getValue("Result");
   }

   public void setResult(String var1) {
      this.setValue("Result", var1);
   }

   public String getNotes() {
      return this.getValue("Notes");
   }

   public void setNotes(String var1) {
      this.setValue("Notes", var1);
   }

   public String getAdultScoutRatio() {
      return this.getValue("AdultScoutRatio");
   }

   public void setAdultScoutRatio(String var1) {
      this.setValue("AdultScoutRatio", var1);
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
