package shkc.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class NegaPreRegAdultRecordConverter extends DataFileConverter<AdultRecord> {
   private static Map<String, String> COLUMN_MAP = new HashMap<>();

   public NegaPreRegAdultRecordConverter(DataRecordFile<AdultRecord> dataFile) {
      super(dataFile, COLUMN_MAP, new String[]{"Email", "First Name", "Last Name", "Scouts Contact Number", "Scoutmasters Name", "Unit Number"});
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

   public void procesNewRecord(AdultRecord incoming) {
      DataRecordFile dataFile = this.getDataFile();
      String email = incoming.getEmail();
      AdultRecord existing = (AdultRecord)dataFile.get(incoming.getID());
      if (existing == null) {
         List sameEmail = dataFile.get("Email", email);
         if (sameEmail != null && sameEmail.size() != 0) {
            if (sameEmail.size() == 1) {
               EagleBoardScheduler.verbose("UPDATING PREREG ADULT RECORD[1]: " + sameEmail.get(0));
               ((AdultRecord)sameEmail.get(0)).updateFrom(incoming, new String[]{"Phone"});
            }
         } else {
            EagleBoardScheduler.verbose("ADDING NEW PREREG ADULT RECORD: " + incoming);
            dataFile.add(incoming, false);
         }
      } else {
         EagleBoardScheduler.verbose("UPDATING PREREG ADULT RECORD[2]: " + existing);
         existing.updateFrom(incoming, new String[]{"Phone", "Email"});
      }
   }

   private String cvtname(String name) {
      if (name.length() == 0) {
         return name;
      } else {
         return Character.isLetter(name.charAt(0)) ? Character.toUpperCase(name.charAt(0)) + name.substring(1) : name;
      }
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
      public boolean convert(DataRecord record, String column, String value) {
         if (value.toLowerCase().indexOf("adult") >= 0) {
            record.put("ProjectReview", "Member");
            record.put("FinalBoard", "Member");
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
      public boolean convert(DataRecord record, String column, String value) {
         StringTokenizer tokens = new StringTokenizer(value, " ", false);
         if (tokens.countTokens() >= 1) {
            record.put("First", NegaPreRegAdultRecordConverter.this.cvtname(tokens.nextToken()));
         } else if (tokens.countTokens() == 0) {
            return false;
         }

         return true;
      }
   }

   private class LastNameConverter implements DataFileConverter.FieldConverter {
      private LastNameConverter() {
      }

      @Override
      public boolean convert(DataRecord record, String column, String value) {
         StringTokenizer tokens = new StringTokenizer(value, " ", false);
         if (tokens.countTokens() >= 1) {
            record.put("Last", NegaPreRegAdultRecordConverter.this.cvtname(tokens.nextToken()));
         } else if (tokens.countTokens() == 0) {
            return false;
         }

         return true;
      }
   }

   private class NameConverter implements DataFileConverter.FieldConverter {
      private NameConverter() {
      }

      @Override
      public boolean convert(DataRecord record, String column, String value) {
         StringTokenizer tokens = new StringTokenizer(value, " ", false);
         if (tokens.countTokens() == 1) {
            record.put("Last", NegaPreRegAdultRecordConverter.this.cvtname(tokens.nextToken()));
         } else {
            if (tokens.countTokens() == 0) {
               return false;
            }

            record.put("First", NegaPreRegAdultRecordConverter.this.cvtname(tokens.nextToken()));

            while (tokens.hasMoreTokens()) {
               record.put("Last", NegaPreRegAdultRecordConverter.this.cvtname(tokens.nextToken()));
            }
         }

         return true;
      }
   }

   private class PhoneConverter implements DataFileConverter.FieldConverter {
      private PhoneConverter() {
      }

      @Override
      public boolean convert(DataRecord record, String column, String value) {
         if (value.length() == 12) {
            record.setValue("Phone", value);
         } else {
            StringBuffer out = new StringBuffer();

            for (int i = 0; i < value.length(); i++) {
               if (Character.isDigit(value.charAt(i))) {
                  out.append(value.charAt(i));
               }
            }

            String digits = out.toString();
            out = new StringBuffer();

            for (int digitIndex = 0; digitIndex < digits.length(); digitIndex++) {
               char digit = value.charAt(digitIndex);
               if (digitIndex == 3 || digitIndex == 6) {
                  out.append("-");
               }

               out.append(digit);
            }

            record.setValue("Phone", out.toString());
         }

         return true;
      }
   }

   private class StatusConverter implements DataFileConverter.FieldConverter {
      private StatusConverter() {
      }

      @Override
      public boolean convert(DataRecord record, String column, String value) {
         return value.toLowerCase().indexOf("cancel") < 0;
      }
   }

   private class UnitConverter implements DataFileConverter.FieldConverter {
      private UnitConverter() {
      }

      @Override
      public boolean convert(DataRecord record, String column, String value) {
         StringBuffer digits = new StringBuffer();

         for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
               digits.append(value.charAt(i));
            }
         }

         record.setValue("Unit", digits.toString());
         String unit = value.trim().toUpperCase();
         if (unit.startsWith("C")) {
            record.setValue("UnitType", "Crew");
         } else if (unit.startsWith("P")) {
            record.setValue("UnitType", "Pack");
         } else {
            record.setValue("UnitType", "Troop");
         }

         return true;
      }
   }
}
