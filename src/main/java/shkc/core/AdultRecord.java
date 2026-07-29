package shkc.core;

import java.util.Map;

public class AdultRecord extends PersonRecord {
   public static final String[] COLUMNS = new String[]{
      "Type",
      "ID",
      "Last",
      "First",
      "Email",
      "Phone",
      "UnitType",
      "Unit",
      "UnitName",
      "ProjectReview",
      "FinalBoard",
      "RegTime",
      "Room",
      "Flags",
      "Sel",
      "BoardHistory"
   };

   public AdultRecord() {
      this((AdultRecord)null);
   }

   public AdultRecord(Map var1) {
      super("ADULT", COLUMNS, var1);
      this.updateFields(true);
   }

   public AdultRecord(AdultRecord var1) {
      super("ADULT", COLUMNS, var1);
      this.updateFields(true);
   }

   public AdultRecord clone() {
      return new AdultRecord(this);
   }

   @Override
   public void updateFields(boolean var1) {
      super.updateFields(var1);
   }

   public void updateFrom(AdultRecord var1, String[] var2) {
      for (String var6 : var2) {
         String var7 = var1.get(var6);
         if (var7 != null && var7.length() > 0) {
            this.put(var6, var7);
         }
      }
   }

   public String getFinalBoardRole() {
      return this.getValue("FinalBoard");
   }

   public String getProjectReviewRole() {
      return this.getValue("ProjectReview");
   }

   public String getBoardHistory() {
      return this.getValue("BoardHistory");
   }

   public void setBoardHistory(String var1) {
      this.setValue("BoardHistory", var1);
   }

   public static class Factory implements DataRecord.Factory {
      @Override
      public DataRecord newInstance() {
         return new AdultRecord();
      }

      @Override
      public String[] getColumns() {
         return AdultRecord.COLUMNS;
      }
   }
}
