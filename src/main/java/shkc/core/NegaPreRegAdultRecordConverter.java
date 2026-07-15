package shkc.core;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class NegaPreRegAdultRecordConverter extends DataFileConverter<AdultRecord> {
   private static Map<String, String> COLUMN_MAP = new HashMap<>();

   public NegaPreRegAdultRecordConverter(DataRecordFile<AdultRecord> var1) {
      super(var1, null, COLUMN_MAP, new String[]{"Email", "First Name", "Last Name", "Scouts Contact Number", "Scoutmasters Name", "Unit Number"});
      this.addConverter("name", new NegaPreRegAdultRecordConverter.NameConverter());
      this.addConverter("phone", new NegaPreRegAdultRecordConverter.PhoneConverter());
      this.addConverter("request_status", new NegaPreRegAdultRecordConverter.StatusConverter());
      this.addConverter("Item", new NegaPreRegAdultRecordConverter.BoardTypeConverter());
      this.addConverter("UnitNumber", new NegaPreRegAdultRecordConverter.UnitConverter());
      this.addConverter("First Name", new NegaPreRegAdultRecordConverter.FirstNameConverter());
      this.addConverter("Last Name", new NegaPreRegAdultRecordConverter.LastNameConverter());
      this.addConverter("Scouts Contact Number", new NegaPreRegAdultRecordConverter.PhoneConverter());
      this.addConverter("request_status", new NegaPreRegAdultRecordConverter.StatusConverter());
      this.addConverter("Item", new NegaPreRegAdultRecordConverter.BoardTypeConverter());
      this.addConverter("Unit Number", new NegaPreRegAdultRecordConverter.UnitConverter());
   }

   public void procesNewRecord(AdultRecord var1) {
      DataRecordFile var2 = this.getDataFile();
      String var3 = var1.getEmail();
      AdultRecord var4 = (AdultRecord)var2.get(var1.getID());
      if (var4 == null) {
         List var5 = var2.get("Email", var3);
         if (var5 != null && var5.size() != 0) {
            if (var5.size() == 1) {
               EagleBoardScheduler.verbose("UPDATING PREREG ADULT RECORD[1]: " + var5.get(0));
               ((AdultRecord)var5.get(0)).updateFrom(var1, new String[]{"Phone"});
            }
         } else {
            EagleBoardScheduler.verbose("ADDING NEW PREREG ADULT RECORD: " + var1);
            var2.add(var1, false);
         }
      } else {
         EagleBoardScheduler.verbose("UPDATING PREREG ADULT RECORD[2]: " + var4);
         var4.updateFrom(var1, new String[]{"Phone", "Email"});
      }
   }

   private String cvtname(String var1) {
      if (var1.length() == 0) {
         return var1;
      } else {
         return Character.isLetter(var1.charAt(0)) ? Character.toUpperCase(var1.charAt(0)) + var1.substring(1) : var1;
      }
   }

   public static void main(String[] var0) throws Exception {
      DataRecordFile var1 = new DataRecordFile(new File("test_prereg.csv"), new AdultRecord.Factory());
      NegaPreRegAdultRecordConverter var2 = new NegaPreRegAdultRecordConverter(var1);
      var2.convert(new File(var0[0]));
      System.out.println(var1);
   }

   static {
      COLUMN_MAP.put("email", "Email");
      COLUMN_MAP.put("Unit Type", "UnitType");
      COLUMN_MAP.put("Unit Number", "Unit");
      COLUMN_MAP.put("id", "");
   }

   private class BoardTypeConverter implements DataFileConverter.FieldConverter {
      private BoardTypeConverter() {
      }

      @Override
      public boolean convert(DataRecord var1, String var2, String var3) {
         if (var3.toLowerCase().indexOf("adult") >= 0) {
            var1.put("ProjectReview", "Member");
            var1.put("FinalBoard", "Member");
            return true;
         } else {
            return false;
         }
      }
   }

   private class FirstNameConverter implements DataFileConverter.FieldConverter {
      private FirstNameConverter() {
      }

      @Override
      public boolean convert(DataRecord var1, String var2, String var3) {
         StringTokenizer var4 = new StringTokenizer(var3, " ", false);
         if (var4.countTokens() >= 1) {
            var1.put("First", NegaPreRegAdultRecordConverter.this.cvtname(var4.nextToken()));
         } else if (var4.countTokens() == 0) {
            return false;
         }

         return true;
      }
   }

   private class LastNameConverter implements DataFileConverter.FieldConverter {
      private LastNameConverter() {
      }

      @Override
      public boolean convert(DataRecord var1, String var2, String var3) {
         StringTokenizer var4 = new StringTokenizer(var3, " ", false);
         if (var4.countTokens() >= 1) {
            var1.put("Last", NegaPreRegAdultRecordConverter.this.cvtname(var4.nextToken()));
         } else if (var4.countTokens() == 0) {
            return false;
         }

         return true;
      }
   }

   private class NameConverter implements DataFileConverter.FieldConverter {
      private NameConverter() {
      }

      @Override
      public boolean convert(DataRecord var1, String var2, String var3) {
         StringTokenizer var4 = new StringTokenizer(var3, " ", false);
         if (var4.countTokens() == 1) {
            var1.put("Last", NegaPreRegAdultRecordConverter.this.cvtname(var4.nextToken()));
         } else {
            if (var4.countTokens() == 0) {
               return false;
            }

            var1.put("First", NegaPreRegAdultRecordConverter.this.cvtname(var4.nextToken()));

            while (var4.hasMoreTokens()) {
               var1.put("Last", NegaPreRegAdultRecordConverter.this.cvtname(var4.nextToken()));
            }
         }

         return true;
      }
   }

   private class PhoneConverter implements DataFileConverter.FieldConverter {
      private PhoneConverter() {
      }

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

   private class StatusConverter implements DataFileConverter.FieldConverter {
      private StatusConverter() {
      }

      @Override
      public boolean convert(DataRecord var1, String var2, String var3) {
         return var3.toLowerCase().indexOf("cancel") < 0;
      }
   }

   private class UnitConverter implements DataFileConverter.FieldConverter {
      private UnitConverter() {
      }

      @Override
      public boolean convert(DataRecord var1, String var2, String var3) {
         StringBuffer var4 = new StringBuffer();

         for (int var5 = 0; var5 < var3.length(); var5++) {
            if (Character.isDigit(var3.charAt(var5))) {
               var4.append(var3.charAt(var5));
            }
         }

         var1.setValue("Unit", var4.toString());
         String var6 = var3.trim().toUpperCase();
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
