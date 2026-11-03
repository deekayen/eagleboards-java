package shkc.core;

import java.util.StringTokenizer;

/**
 * The cell converters shared by everything that reads pre-registrations: the
 * scout and adult sheet importers ({@link NegaPreRegScoutRecordConverter},
 * {@link NegaPreRegAdultRecordConverter}) and the SignUpGenius import
 * ({@link SignUpGeniusPlugin}).
 *
 * <p>Each constant turns one cell of the incoming data into the right column(s)
 * of a {@link DataRecord}. Every one is stateless, so one instance can be
 * registered for any number of columns. A converter returns false to reject the
 * whole row (see {@link DataFileConverter.FieldConverter}).
 *
 * <p>This class only holds those constants and cannot be instantiated.
 */
final class PreRegFieldConverters {
   /** The headings a pre-registration sheet must have for the importers to accept it. */
   static final String[] REQUIRED_SHEET_COLUMNS = {
      "Email", "First Name", "Last Name", "Scouts Contact Number", "Scoutmasters Name", "Unit Number"
   };

   private static final String COLUMN_FIRST = "First";
   private static final String COLUMN_LAST = "Last";
   private static final String COLUMN_LEADER = "Leader";
   private static final String COLUMN_PHONE = "Phone";
   private static final String COLUMN_UNIT = "Unit";
   private static final String COLUMN_UNIT_TYPE = "UnitType";
   private static final String COLUMN_BOARD_TYPE = "BoardType";
   private static final String COLUMN_PROJECT_REVIEW = "ProjectReview";
   private static final String COLUMN_FINAL_BOARD = "FinalBoard";

   private static final String UNIT_TYPE_CREW = "Crew";
   private static final String UNIT_TYPE_PACK = "Pack";
   private static final String UNIT_TYPE_TROOP = "Troop";

   /** A phone number already written as 555-123-4567 has this many characters. */
   private static final int DASHED_PHONE_LENGTH = 12;
   /** The dashes of 555-123-4567 go in front of the digits at these positions. */
   private static final int FIRST_DASH_POSITION = 3;
   private static final int SECOND_DASH_POSITION = 6;

   /** A sheet's words are separated by plain spaces only. */
   private static final String WORD_SEPARATOR = " ";

   /** The first word of the cell, capitalized, becomes the first name. Rejects an empty cell. */
   static final DataFileConverter.FieldConverter FIRST_NAME =
      (record, column, value) -> putFirstWord(record, COLUMN_FIRST, value);

   /** The first word of the cell, capitalized, becomes the last name. Rejects an empty cell. */
   static final DataFileConverter.FieldConverter LAST_NAME =
      (record, column, value) -> putFirstWord(record, COLUMN_LAST, value);

   /**
    * A whole name in one cell: a single word is the last name; otherwise the
    * first word is the first name and the final word the last name (middle
    * names are dropped). Rejects an empty cell.
    */
   static final DataFileConverter.FieldConverter FULL_NAME = (record, column, value) -> {
      StringTokenizer words = new StringTokenizer(value, WORD_SEPARATOR);
      if (words.countTokens() == 0) {
         return false;
      }
      if (words.countTokens() == 1) {
         record.put(COLUMN_LAST, capitalizeFirstLetter(words.nextToken()));
         return true;
      }

      record.put(COLUMN_FIRST, capitalizeFirstLetter(words.nextToken()));
      while (words.hasMoreTokens()) {
         record.put(COLUMN_LAST, capitalizeFirstLetter(words.nextToken()));
      }
      return true;
   };

   /**
    * Appends the cell's words (each capitalized) to the leader column. A
    * leader already there is kept and the new one follows after a "/", so the
    * scoutmaster and the Life-to-Eagle coach columns end up in one cell. A
    * blank cell changes nothing.
    */
   static final DataFileConverter.FieldConverter LEADER = (record, column, value) -> {
      String trimmed = value.trim();
      if (trimmed.length() == 0) {
         return true;
      }

      StringBuilder leader = new StringBuilder();
      String existingLeader = record.getValue(COLUMN_LEADER);
      if (existingLeader.length() > 0) {
         leader.append(existingLeader).append("/");
      }

      StringTokenizer words = new StringTokenizer(trimmed, WORD_SEPARATOR);
      boolean firstWord = true;
      while (words.hasMoreElements()) {
         if (!firstWord) {
            leader.append(" ");
         }
         firstWord = false;
         leader.append(capitalizeFirstLetter(words.nextToken()));
      }

      record.setValue(COLUMN_LEADER, leader.toString());
      return true;
   };

   /**
    * Stores a phone number. One that is already 12 characters long is kept
    * as typed; anything else is rebuilt from its digits as 555-123-4567, so
    * "(555) 123-4567" and "5551234567" both come out that way.
    */
   static final DataFileConverter.FieldConverter PHONE = (record, column, value) -> {
      if (value.length() == DASHED_PHONE_LENGTH) {
         record.setValue(COLUMN_PHONE, value);
         return true;
      }

      String digits = digitsOnly(value);
      StringBuilder dashed = new StringBuilder();
      for (int position = 0; position < digits.length(); position++) {
         if (position == FIRST_DASH_POSITION || position == SECOND_DASH_POSITION) {
            dashed.append("-");
         }
         dashed.append(digits.charAt(position));
      }
      record.setValue(COLUMN_PHONE, dashed.toString());
      return true;
   };

   /** Rejects a row whose status says it was cancelled (any case). */
   static final DataFileConverter.FieldConverter NOT_CANCELLED =
      (record, column, value) -> !value.toLowerCase().contains("cancel");

   /**
    * Splits a unit cell such as "Troop 123" into the unit's number (its digits)
    * and its type: a cell starting with C is a Crew, P a Pack, anything else a
    * Troop. The cell is read as typed.
    */
   static final DataFileConverter.FieldConverter UNIT_AS_TYPED =
      (record, column, value) -> putUnit(record, value, value);

   /** Like {@link #UNIT_AS_TYPED}, but the type is read after trimming the cell's surrounding blanks. */
   static final DataFileConverter.FieldConverter UNIT_TRIMMED =
      (record, column, value) -> putUnit(record, value, value.trim());

   /** A sheet row for a youth: "review" in the item is a board of review, "project" a project proposal. */
   static final DataFileConverter.FieldConverter SCOUT_BOARD_TYPE = (record, column, value) -> {
      String item = value.toLowerCase();
      if (item.contains("review")) {
         record.put(COLUMN_BOARD_TYPE, BoardType.FINAL_BOARD);
         return true;
      }
      if (item.contains("project")) {
         record.put(COLUMN_BOARD_TYPE, BoardType.PROJECT);
         return true;
      }
      return false;
   };

   /** A sheet row for an adult: an item mentioning "adult" volunteers for both kinds of board as a member. */
   static final DataFileConverter.FieldConverter ADULT_BOARD_TYPE = (record, column, value) -> {
      if (!value.toLowerCase().contains("adult")) {
         return false;
      }
      record.put(COLUMN_PROJECT_REVIEW, AdultRole.MEMBER);
      record.put(COLUMN_FINAL_BOARD, AdultRole.MEMBER);
      return true;
   };

   private PreRegFieldConverters() {
   }

   /** Upper-cases the first character of a name if it is a letter; leaves the rest as typed. */
   static String capitalizeFirstLetter(String name) {
      if (name.length() == 0) {
         return name;
      }
      char first = name.charAt(0);
      return Character.isLetter(first) ? Character.toUpperCase(first) + name.substring(1) : name;
   }

   /** Just the digits of the text, in order. */
   static String digitsOnly(String text) {
      StringBuilder digits = new StringBuilder();
      for (int i = 0; i < text.length(); i++) {
         if (Character.isDigit(text.charAt(i))) {
            digits.append(text.charAt(i));
         }
      }
      return digits.toString();
   }

   private static boolean putFirstWord(DataRecord record, String column, String value) {
      StringTokenizer words = new StringTokenizer(value, WORD_SEPARATOR);
      if (!words.hasMoreTokens()) {
         return false;
      }
      record.put(column, capitalizeFirstLetter(words.nextToken()));
      return true;
   }

   private static boolean putUnit(DataRecord record, String cell, String textForType) {
      record.setValue(COLUMN_UNIT, digitsOnly(cell));
      String typeText = textForType.toUpperCase();
      if (typeText.startsWith("C")) {
         record.setValue(COLUMN_UNIT_TYPE, UNIT_TYPE_CREW);
      } else if (typeText.startsWith("P")) {
         record.setValue(COLUMN_UNIT_TYPE, UNIT_TYPE_PACK);
      } else {
         record.setValue(COLUMN_UNIT_TYPE, UNIT_TYPE_TROOP);
      }
      return true;
   }
}
