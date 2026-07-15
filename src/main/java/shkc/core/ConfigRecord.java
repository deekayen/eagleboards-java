package shkc.core;

import java.util.Map;

public class ConfigRecord extends DataRecord {
   public static final String NAME = "Name";
   public static final String REFRESH_TIME_SECS = "RefreshTimeSecs";
   public static final String SEATED_ALERT_TIME_MINS = "SeatedAlertTimeMins";
   public static final String SEATED_ALERT_REMINDER_MINS = "SeatedReminderTimeMins";
   public static final String IN_PROGRESS_ALERT_TIME_MINS = "InProgressAlertTimeMins";
   public static final String IN_PROGRESS_ALERT_REMINDER_MINS = "InProgressReminderTimeMins";
   public static final String REGISTERED_COLOR = "RegisteredColor";
   public static final String VERIFIED_COLOR = "VerifiedColor";
   public static final String SEATED_COLOR = "SeatedColor";
   public static final String IN_PROGRESS_COLOR = "InProgressColor";
   public static final String COMPLETED_COLOR = "CompletedColor";
   public static final String POSTPONED_COLOR = "PostponedColor";
   public static final String REGISTERED_HI_COLOR = "RegisteredHiColor";
   public static final String VERIFIED_HI_COLOR = "VerifiedHiColor";
   public static final String SEATED_HI_COLOR = "SeatedHiColor";
   public static final String IN_PROGRESS_HI_COLOR = "InProgressHiColor";
   public static final String COMPLETED_HI_COLOR = "CompletedHiColor";
   public static final String POSTPONED_HI_COLOR = "PostponedHiColor";
   public static final String[] COLUMNS = new String[]{
      "Type",
      "ID",
      "Name",
      "RefreshTimeSecs",
      "SeatedAlertTimeMins",
      "SeatedReminderTimeMins",
      "InProgressAlertTimeMins",
      "InProgressReminderTimeMins",
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
      this.setIfNotSet("RefreshTimeSecs", "30");
      this.setIfNotSet("SeatedAlertTimeMins", "10");
      this.setIfNotSet("SeatedReminderTimeMins", "10");
      this.setIfNotSet("InProgressAlertTimeMins", "20");
      this.setIfNotSet("InProgressReminderTimeMins", "10");
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
