package shkc.core;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class NegaPreRegScoutRecordConverter extends DataFileConverter<ScoutRecord> {
   private static Map<String, String> COLUMN_MAP = new HashMap<>();

   public NegaPreRegScoutRecordConverter(DataRecordFile<ScoutRecord> var1) {
      super(var1, null, COLUMN_MAP, new String[]{"Email", "First Name", "Last Name", "Scouts Contact Number", "Scoutmasters Name", "Unit Number"});
      this.addConverter("First Name", new NegaPreRegScoutRecordConverter.FirstNameConverter());
      this.addConverter("Last Name", new NegaPreRegScoutRecordConverter.LastNameConverter());
      this.addConverter("Scouts Contact Number", new NegaPreRegScoutRecordConverter.PhoneConverter());
      this.addConverter("request_status", new NegaPreRegScoutRecordConverter.StatusConverter());
      this.addConverter("Item", new NegaPreRegScoutRecordConverter.BoardTypeConverter());
      this.addConverter("Unit Number", new NegaPreRegScoutRecordConverter.UnitConverter());
      this.addConverter("Scoutmasters Name", new NegaPreRegScoutRecordConverter.LeaderConverter());
      this.addConverter("Life-to-Eagle Coach", new NegaPreRegScoutRecordConverter.LeaderConverter());
   }

   public static String cvtname(String var0) {
      if (var0.length() == 0) {
         return var0;
      } else {
         return Character.isLetter(var0.charAt(0)) ? Character.toUpperCase(var0.charAt(0)) + var0.substring(1) : var0;
      }
   }


   static {
      COLUMN_MAP.put("Email", "Email");
      COLUMN_MAP.put("id", "");
      COLUMN_MAP.put("Scouts Contact Number", "phone");
      COLUMN_MAP.put("ScoutMasters Name", "Scoutmaster");
   }

   public static class BoardTypeConverter implements DataFileConverter.FieldConverter {
      @Override
      public boolean convert(DataRecord var1, String var2, String var3) {
         if (var3.toLowerCase().indexOf("review") >= 0) {
            var1.put("BoardType", "Final");
            return true;
         } else if (var3.toLowerCase().indexOf("project") >= 0) {
            var1.put("BoardType", "Project");
            return true;
         } else {
            return false;
         }
      }
   }

   public static class FirstNameConverter implements DataFileConverter.FieldConverter {
      @Override
      public boolean convert(DataRecord var1, String var2, String var3) {
         StringTokenizer var4 = new StringTokenizer(var3, " ", false);
         if (var4.countTokens() >= 1) {
            var1.put("First", NegaPreRegScoutRecordConverter.cvtname(var4.nextToken()));
         } else if (var4.countTokens() == 0) {
            return false;
         }

         return true;
      }
   }

   public static class LastNameConverter implements DataFileConverter.FieldConverter {
      @Override
      public boolean convert(DataRecord var1, String var2, String var3) {
         StringTokenizer var4 = new StringTokenizer(var3, " ", false);
         if (var4.countTokens() >= 1) {
            var1.put("Last", NegaPreRegScoutRecordConverter.cvtname(var4.nextToken()));
         } else if (var4.countTokens() == 0) {
            return false;
         }

         return true;
      }
   }

   public static class LeaderConverter implements DataFileConverter.FieldConverter {
      @Override
      public boolean convert(DataRecord var1, String var2, String var3) {
         var3 = var3.trim();
         if (var3.length() == 0) {
            return true;
         }

         StringBuffer var4 = new StringBuffer();
         String var5 = var1.getValue("Leader");
         if (var5.length() > 0) {
            var4.append(var5).append("/");
         }

         boolean var6 = true;
         StringTokenizer var7 = new StringTokenizer(var3, " ", false);

         while (var7.hasMoreElements()) {
            if (!var6) {
               var4.append(" ");
            }

            var6 = false;
            var4.append(NegaPreRegScoutRecordConverter.cvtname(var7.nextToken()));
         }

         var1.setValue("Leader", var4.toString());
         return true;
      }
   }

   public static class NameConverter implements DataFileConverter.FieldConverter {
      @Override
      public boolean convert(DataRecord var1, String var2, String var3) {
         StringTokenizer var4 = new StringTokenizer(var3, " ", false);
         if (var4.countTokens() == 1) {
            var1.put("Last", NegaPreRegScoutRecordConverter.cvtname(var4.nextToken()));
         } else {
            if (var4.countTokens() == 0) {
               return false;
            }

            var1.put("First", NegaPreRegScoutRecordConverter.cvtname(var4.nextToken()));

            while (var4.hasMoreTokens()) {
               var1.put("Last", NegaPreRegScoutRecordConverter.cvtname(var4.nextToken()));
            }
         }

         return true;
      }
   }

   public static class PhoneConverter implements DataFileConverter.FieldConverter {
      @Override
      public boolean convert(DataRecord var1, String var2, String var3) {
         if (var3.length() == 12) {
            var1.setValue("Phone", var3);
         } else {
            StringBuffer var4 = new StringBuffer();

            for (int var5 = 0; var5 < var3.length(); var5++) {
               if (Character.isDigit(var3.charAt(var5))) {
                  var4.append(var3.charAt(var5));
               }
            }

            String var9 = var4.toString();
            var4 = new StringBuffer();

            for (int var6 = 0; var6 < var9.length(); var6++) {
               char var7 = var3.charAt(var6);
               if (var6 == 3 || var6 == 6) {
                  var4.append("-");
               }

               var4.append(var7);
            }

            var1.setValue("Phone", var4.toString());
         }

         return true;
      }
   }

   public static class StatusConverter implements DataFileConverter.FieldConverter {
      @Override
      public boolean convert(DataRecord var1, String var2, String var3) {
         return var3.toLowerCase().indexOf("cancel") < 0;
      }
   }

   public static class UnitConverter implements DataFileConverter.FieldConverter {
      @Override
      public boolean convert(DataRecord var1, String var2, String var3) {
         StringBuffer var4 = new StringBuffer();

         for (int var5 = 0; var5 < var3.length(); var5++) {
            if (Character.isDigit(var3.charAt(var5))) {
               var4.append(var3.charAt(var5));
            }
         }

         var1.setValue("Unit", var4.toString());
         String var6 = var3.toUpperCase();
         if (var6.startsWith("C")) {
            var1.setValue("UnitType", "Crew");
         } else if (var6.startsWith("P")) {
            var1.setValue("UnitType", "Pack");
         } else {
            var1.setValue("UnitType", "Troop");
         }

         return true;
      }
   }
}
