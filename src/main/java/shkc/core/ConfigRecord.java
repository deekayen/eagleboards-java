package shkc.core;

import java.util.Map;

public class ConfigRecord extends DataRecord {
   public static final String[] COLUMNS = new String[]{
      "Type",
      "ID",
      "Name",
      "RefreshTimeSecs",
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

   public ConfigRecord(Map var1) {
      super("CONFIG", COLUMNS, var1);
      // There is only ever one config record. Default its identity to DEFAULT
      // so a config update or a hand-edited file that omits ID/Name still
      // targets the single record in place instead of adding a duplicate.
      this.setIfNotSet("ID", "DEFAULT");
      this.setIfNotSet("Name", "DEFAULT");
      this.setIfNotSet("RefreshTimeSecs", "30");
      // Minutes since a board was seated at which its room card turns yellow
      // (warning) then red (overdue), per board type.
      this.setIfNotSet("ProjectYellowMins", "25");
      this.setIfNotSet("ProjectRedMins", "40");
      this.setIfNotSet("FinalYellowMins", "40");
      this.setIfNotSet("FinalRedMins", "50");
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

   private void setIfNotSet(String var1, String var2) {
      if (this.getValue(var1).length() == 0) {
         this.setValue(var1, var2);
      }
   }

   public ConfigRecord(String var1, String[] var2, Map var3) {
      super(var1, var2, var3);
      String var4 = this.getType() + ":" + this.getName();
      this.put("ID", var4);
   }

   public void updateFrom(ConfigRecord var1, String[] var2) {
      for (String var6 : var2) {
         String var7 = var1.get(var6);
         if (var7 != null && var7.length() > 0) {
            this.put(var6, var7);
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
