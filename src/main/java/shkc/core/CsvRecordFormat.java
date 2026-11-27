package shkc.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.function.Consumer;

/**
 * A CSV file: the column names on the first line, then one record per line.
 * Every data file except the configuration is kept this way.
 */
final class CsvRecordFormat implements RecordFileFormat {
   static final char DELIMITER = ',';

   /** A badly damaged file would otherwise write a warning for every line. */
   private static final int MAX_ODD_LINES_LOGGED = 20;

   @Override
   public void read(File file, DataRecord.Factory factory, Consumer<DataRecord> sink) throws IOException {
      try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
         String headerLine = reader.readLine();
         if (headerLine == null) {
            Log.warn("load: file=" + file.getName() + " is empty (no header line), so 0 records were read."
               + " If it held records before, a write was cut short or the file was replaced");
            return;
         }

         String[] columns = splitHeader(headerLine);
         logHeaderDifferences(file, columns, factory.getColumns());

         int lineNumber = 1;
         int oddLines = 0;
         String line;
         while ((line = reader.readLine()) != null) {
            lineNumber++;
            String oddness = describeOddLine(line, columns.length);
            if (oddness != null) {
               oddLines++;
               if (oddLines <= MAX_ODD_LINES_LOGGED) {
                  Log.warn("load: file=" + file.getName() + " line=" + lineNumber + " " + oddness);
               }
            }
            DataRecord record = factory.newInstance();
            record.fromCSV(line, DELIMITER, columns);
            sink.accept(record);
         }
         if (oddLines > MAX_ODD_LINES_LOGGED) {
            Log.warn("load: file=" + file.getName() + " had " + oddLines + " odd lines in all, only the first "
               + MAX_ODD_LINES_LOGGED + " are listed");
         }
      }
   }

   /**
    * A column in the file that this version does not know is dropped when the
    * file is next saved, and one it knows but the file lacks is filled with
    * its default, so both are worth a line when a file came from another version.
    */
   private static void logHeaderDifferences(File file, String[] fileColumns, String[] knownColumns) {
      if (!Log.looksLikeHeading(fileColumns, Arrays.asList(knownColumns))) {
         // Nothing on the first line is a column this version knows, so it is not a heading row: it
         // is a person's record (a damaged or headerless file) or another kind of file. Its cells
         // are not logged, only how many, and no value is matched to a column.
         Log.warn("load: file=" + file.getName() + " first line has " + fileColumns.length
            + " cells and none is a column this version knows, so it looks like a record or another kind of file, not a heading row."
            + " No value in the file is matched to a column");
         return;
      }
      List<String> unknown = new ArrayList<>();
      for (String fileColumn : fileColumns) {
         if (!Arrays.asList(knownColumns).contains(fileColumn)) {
            unknown.add(Log.columnName(fileColumn));
         }
      }
      List<String> absent = new ArrayList<>();
      for (String knownColumn : knownColumns) {
         if (!Arrays.asList(fileColumns).contains(knownColumn)) {
            absent.add(knownColumn);
         }
      }
      if (!unknown.isEmpty()) {
         Log.warn("load: file=" + file.getName() + " has columns this version does not know " + unknown
            + ", their values are dropped the next time the file is saved");
      }
      if (!absent.isEmpty()) {
         Log.info("load: file=" + file.getName() + " lacks columns " + absent + ", they start with their defaults");
      }
   }

   /**
    * What is odd about a line that is blank or has a different number of fields
    * than the header, or null for a normal line. The line is still read, as it
    * always was (a short line leaves its last columns at their defaults, an
    * extra field is ignored); its content is never described, only its shape.
    */
   private static String describeOddLine(String line, int columnCount) {
      if (line.trim().isEmpty()) {
         return "is blank, read as a record of defaults";
      }
      int fieldCount = 1;
      for (int i = 0; i < line.length(); i++) {
         if (line.charAt(i) == DELIMITER) {
            fieldCount++;
         }
      }
      if (fieldCount == columnCount) {
         return null;
      }
      return "has " + fieldCount + " fields but the header has " + columnCount
         + (fieldCount < columnCount ? ", the missing ones are left at their defaults" : ", the extra ones are ignored");
   }

   /** The column names, then one line per record. */
   @Override
   public void write(File file, DataRecord.Factory factory, List<? extends DataRecord> records) throws IOException {
      StringBuffer csv = new StringBuffer();

      boolean first = true;
      for (String columnName : factory.getColumns()) {
         if (!first) {
            csv.append(DELIMITER);
         }

         first = false;
         csv.append(columnName);
      }

      csv.append("\n");

      for (DataRecord record : records) {
         record.toCSV(csv, DELIMITER);
         csv.append("\n");
      }

      RecordFileFormat.writeText(file, csv);
   }

   private static String[] splitHeader(String headerLine) {
      StringTokenizer headerTokens = new StringTokenizer(headerLine, String.valueOf(DELIMITER), false);
      String[] columns = new String[headerTokens.countTokens()];

      for (int columnIndex = 0; columnIndex < columns.length; columnIndex++) {
         columns[columnIndex] = headerTokens.nextToken();
      }

      return columns;
   }
}
