package shkc.core;

import java.util.Map;

/**
 * The program's settings: the one row of {@code config.properties}. Holds the
 * minutes after which a room card turns yellow or red; see the Settings page.
 */
public class ConfigRecord extends DataRecord {
   public static final String[] COLUMNS = new String[]{
      "Type",
      "ID",
      "Name",
      "RefreshTimeSecs",
      "ConveneRedMins",
      "ProjectYellowMins",
      "ProjectRedMins",
      "FinalYellowMins",
      "FinalRedMins"
      // RegisteredColor..PostponedHiColor followed here once. They are retired
      // (SPEC.md D-19): an older config.properties that still sets them loads,
      // since only these columns are read, and saving it leaves them out.
   };

   public ConfigRecord() {
      this(null);
   }

   public ConfigRecord(Map<String, ?> values) {
      super("CONFIG", COLUMNS, values);
      // There is only ever one config record. Default its identity to DEFAULT
      // so a config update or a hand-edited file that omits ID/Name still
      // targets the single record in place instead of adding a duplicate.
      this.setIfNotSet(COLUMN_ID, "DEFAULT");
      this.setIfNotSet("Name", "DEFAULT");
      this.setIfNotSet("RefreshTimeSecs", "30");
      // How long the board may spend convening -- reading the application,
      // references and project workbook -- before the scout is brought in.
      // There is no yellow stage: this window is a cap, not a target, and a
      // board still previewing past it is keeping the candidate waiting.
      this.setIfNotSet("ConveneRedMins", "30");
      // Minutes since the scout was brought in at which the room card's timer
      // shows running long ("Yellow") then overdue ("Red"), per board type.
      this.setIfNotSet("ProjectYellowMins", "25");
      this.setIfNotSet("ProjectRedMins", "40");
      this.setIfNotSet("FinalYellowMins", "30");
      this.setIfNotSet("FinalRedMins", "45");
   }

   /** Sets {@code column} to {@code defaultValue} when it is empty. */
   private void setIfNotSet(String column, String defaultValue) {
      if (this.getValue(column).length() == 0) {
         this.setValue(column, defaultValue);
      }
   }

   /** Variant for a record kind other than CONFIG: its ID is the type and name. Not used at present. */
   public ConfigRecord(String recordType, String[] columns, Map<String, ?> values) {
      super(recordType, columns, values);
      String id = this.getType() + ":" + this.getName();
      this.put(COLUMN_ID, id);
   }

   public String getName() {
      return this.getValue("Name");
   }

   public static class Factory implements DataRecord.Factory {
      @Override
      public DataRecord newInstance() {
         return new ConfigRecord();
      }

      @Override
      public String[] getColumns() {
         return ConfigRecord.COLUMNS;
      }
   }
}
