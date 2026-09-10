package shkc.core;

import java.util.Map;

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
      "FinalRedMins",
      "RegisteredColor",
      "VerifiedColor",
      "SeatedColor",
      "InProgressColor",
      "CompletedColor",
      "PostponedColor",
      "RegisteredHiColor",
      "VerifiedHiColor",
      "SeatedHiColor",
      "InProgressHiColor",
      "CompletedHiColor",
      "PostponedHiColor"
   };

   public ConfigRecord() {
      this(null);
   }

   public ConfigRecord(Map values) {
      super("CONFIG", COLUMNS, values);
      // There is only ever one config record. Default its identity to DEFAULT
      // so a config update or a hand-edited file that omits ID/Name still
      // targets the single record in place instead of adding a duplicate.
      this.setIfNotSet("ID", "DEFAULT");
      this.setIfNotSet("Name", "DEFAULT");
      this.setIfNotSet("RefreshTimeSecs", "30");
      // How long the board may spend convening -- reading the application,
      // references and project workbook -- before the scout is brought in.
      // There is no yellow stage: this window is a cap, not a target, and a
      // board still previewing past it is keeping the candidate waiting.
      this.setIfNotSet("ConveneRedMins", "30");
      // Minutes since the scout was brought in at which the room card turns
      // yellow (warning) then red (overdue), per board type.
      this.setIfNotSet("ProjectYellowMins", "25");
      this.setIfNotSet("ProjectRedMins", "40");
      this.setIfNotSet("FinalYellowMins", "30");
      this.setIfNotSet("FinalRedMins", "45");
      this.setIfNotSet("RegisteredColor", "#ffcccc");
      this.setIfNotSet("VerifiedColor", "#ffffcc");
      this.setIfNotSet("SeatedColor", "#ccffff");
      this.setIfNotSet("InProgressColor", "#ccffcc");
      this.setIfNotSet("CompletedColor", "#ffffff");
      this.setIfNotSet("PostponedColor", "#909090");
      this.setIfNotSet("RegisteredHiColor", "#ff6666");
      this.setIfNotSet("VerifiedHiColor", "#ffff66");
      this.setIfNotSet("SeatedHiColor", "#66ffff");
      this.setIfNotSet("InProgressHiColor", "#66ff66");
      this.setIfNotSet("CompletedHiColor", "#eeeeee");
      this.setIfNotSet("PostponedHiColor", "#9f7f7f");
   }

   private void setIfNotSet(String column, String defaultValue) {
      if (this.getValue(column).length() == 0) {
         this.setValue(column, defaultValue);
      }
   }

   public ConfigRecord(String recordType, String[] columns, Map values) {
      super(recordType, columns, values);
      String id = this.getType() + ":" + this.getName();
      this.put("ID", id);
   }

   public void updateFrom(ConfigRecord source, String[] columns) {
      for (String column : columns) {
         String value = source.get(column);
         if (value != null && value.length() > 0) {
            this.put(column, value);
         }
      }
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
