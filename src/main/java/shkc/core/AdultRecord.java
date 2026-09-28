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
      "BoardHistory",
      // Per event, set at sign-in and never carried into the adult history:
      // "Y" if volunteering toward a Wood Badge ticket item, and the IDs of
      // the scouts this adult came to support (their Scoutmaster, say),
      // separated by "|". Appended so older files still line up.
      "WoodBadge",
      "Supporting"
   };

   public AdultRecord() {
      this((AdultRecord)null);
   }

   public AdultRecord(Map values) {
      super("ADULT", COLUMNS, values);
      this.updateFields(true);
   }

   public AdultRecord(AdultRecord source) {
      super("ADULT", COLUMNS, source);
      this.updateFields(true);
   }

   public AdultRecord clone() {
      return new AdultRecord(this);
   }

   public void updateFrom(AdultRecord source, String[] columns) {
      for (String column : columns) {
         String value = source.get(column);
         if (value != null && value.length() > 0) {
            this.put(column, value);
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

   public void setBoardHistory(String history) {
      this.setValue("BoardHistory", history);
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
