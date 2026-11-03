package shkc.core;

import java.util.Map;

/**
 * One adult volunteer: the row in the adult file for today's event, and in
 * the adult history file that remembers adults from one event to the next.
 *
 * <p>{@code ProjectReview} and {@code FinalBoard} hold the adult's role for each
 * kind of board (Member, Chair, or Unavailable). {@code Room} is the room the
 * adult is committed to, or {@code N/A} when they have gone home.
 */
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
      // the scouts this adult introduces to their board (as their
      // Scoutmaster, say; SPEC.md D-23), separated by "|". Appended so older
      // files still line up.
      "WoodBadge",
      "Supporting"
   };

   public AdultRecord() {
      this((AdultRecord)null);
   }

   public AdultRecord(Map<String, ?> values) {
      super("ADULT", COLUMNS, values);
      this.updateFields(true);
   }

   /** Copies another adult, such as a history record being signed in for today. */
   public AdultRecord(AdultRecord source) {
      super("ADULT", COLUMNS, source);
      this.updateFields(true);
   }

   @Override
   public AdultRecord clone() {
      return new AdultRecord(this);
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
