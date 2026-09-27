package shkc.core;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class NegaPreRegScoutRecordConverter extends DataFileConverter<ScoutRecord> {
   private static Map<String, String> COLUMN_MAP = new HashMap<>();

   // SPEC.md D-8: no youth's phone number is imported. "Scouts Contact Number"
   // stays among the columns the sheet must have, since that is how the file
   // is recognized, but nothing reads it any more.
   public NegaPreRegScoutRecordConverter(DataRecordFile<ScoutRecord> dataFile) {
      super(dataFile, COLUMN_MAP, new String[]{"Email", "First Name", "Last Name", "Scouts Contact Number", "Scoutmasters Name", "Unit Number"});
      this.addConverter("First Name", new NegaPreRegScoutRecordConverter.FirstNameConverter());
      this.addConverter("Last Name", new NegaPreRegScoutRecordConverter.LastNameConverter());
      this.addConverter("request_status", new NegaPreRegScoutRecordConverter.StatusConverter());
      this.addConverter("Item", new NegaPreRegScoutRecordConverter.BoardTypeConverter());
      this.addConverter("Unit Number", new NegaPreRegScoutRecordConverter.UnitConverter());
      this.addConverter("Scoutmasters Name", new NegaPreRegScoutRecordConverter.LeaderConverter());
      this.addConverter("Life-to-Eagle Coach", new NegaPreRegScoutRecordConverter.LeaderConverter());
   }

   // And any other column that would land in Phone, whatever the sheet calls
   // it, is dropped before the record is kept.
   @Override
   public void procesNewRecord(ScoutRecord record) {
      record.setValue("Phone", "");
      super.procesNewRecord(record);
   }

   public static String cvtname(String name) {
      if (name.length() == 0) {
         return name;
      } else {
         return Character.isLetter(name.charAt(0)) ? Character.toUpperCase(name.charAt(0)) + name.substring(1) : name;
      }
   }


   static {
      COLUMN_MAP.put("Email", "Email");
      COLUMN_MAP.put("id", "");
      COLUMN_MAP.put("ScoutMasters Name", "Scoutmaster");
   }

   public static class BoardTypeConverter implements DataFileConverter.FieldConverter {
      @Override
      public boolean convert(DataRecord record, String column, String value) {
         if (value.toLowerCase().indexOf("review") >= 0) {
            record.put("BoardType", "Final");
            return true;
         } else if (value.toLowerCase().indexOf("project") >= 0) {
            record.put("BoardType", "Project");
            return true;
         } else {
            return false;
         }
      }
   }

   public static class FirstNameConverter implements DataFileConverter.FieldConverter {
      @Override
      public boolean convert(DataRecord record, String column, String value) {
         StringTokenizer tokens = new StringTokenizer(value, " ", false);
         if (tokens.countTokens() >= 1) {
            record.put("First", NegaPreRegScoutRecordConverter.cvtname(tokens.nextToken()));
         } else if (tokens.countTokens() == 0) {
            return false;
         }

         return true;
      }
   }

   public static class LastNameConverter implements DataFileConverter.FieldConverter {
      @Override
      public boolean convert(DataRecord record, String column, String value) {
         StringTokenizer tokens = new StringTokenizer(value, " ", false);
         if (tokens.countTokens() >= 1) {
            record.put("Last", NegaPreRegScoutRecordConverter.cvtname(tokens.nextToken()));
         } else if (tokens.countTokens() == 0) {
            return false;
         }

         return true;
      }
   }

   public static class LeaderConverter implements DataFileConverter.FieldConverter {
      @Override
      public boolean convert(DataRecord record, String column, String value) {
         value = value.trim();
         if (value.length() == 0) {
            return true;
         }

         StringBuffer out = new StringBuffer();
         String existingLeader = record.getValue("Leader");
         if (existingLeader.length() > 0) {
            out.append(existingLeader).append("/");
         }

         boolean first = true;
         StringTokenizer tokens = new StringTokenizer(value, " ", false);

         while (tokens.hasMoreElements()) {
            if (!first) {
               out.append(" ");
            }

            first = false;
            out.append(NegaPreRegScoutRecordConverter.cvtname(tokens.nextToken()));
         }

         record.setValue("Leader", out.toString());
         return true;
      }
   }

   public static class NameConverter implements DataFileConverter.FieldConverter {
      @Override
      public boolean convert(DataRecord record, String column, String value) {
         StringTokenizer tokens = new StringTokenizer(value, " ", false);
         if (tokens.countTokens() == 1) {
            record.put("Last", NegaPreRegScoutRecordConverter.cvtname(tokens.nextToken()));
         } else {
            if (tokens.countTokens() == 0) {
               return false;
            }

            record.put("First", NegaPreRegScoutRecordConverter.cvtname(tokens.nextToken()));

            while (tokens.hasMoreTokens()) {
               record.put("Last", NegaPreRegScoutRecordConverter.cvtname(tokens.nextToken()));
            }
         }

         return true;
      }
   }

   // For adults only now (SignUpGeniusPlugin); a youth's number is not kept.
   public static class PhoneConverter implements DataFileConverter.FieldConverter {
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

   public static class StatusConverter implements DataFileConverter.FieldConverter {
      @Override
      public boolean convert(DataRecord record, String column, String value) {
         return value.toLowerCase().indexOf("cancel") < 0;
      }
   }

   public static class UnitConverter implements DataFileConverter.FieldConverter {
      @Override
      public boolean convert(DataRecord record, String column, String value) {
         StringBuffer digits = new StringBuffer();

         for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
               digits.append(value.charAt(i));
            }
         }

         record.setValue("Unit", digits.toString());
         String unit = value.toUpperCase();
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
