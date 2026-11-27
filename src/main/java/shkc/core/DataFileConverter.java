package shkc.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a comma-separated file made by some other system (a pre-registration
 * sheet, say) and turns each row into a record of this program's own kind.
 *
 * <p>The first line of the file names the columns. A heading becomes a record
 * column in one of two ways:
 * <ul>
 *   <li>if a {@link FieldConverter} was registered for the heading with
 *       {@link #addConverter}, the converter decides what to store (and may
 *       reject the whole row);</li>
 *   <li>otherwise the heading is looked up in the column map, then matched to
 *       one of the record's own columns ignoring upper/lower case, and the
 *       text is copied across unchanged. A heading that matches nothing is
 *       skipped.</li>
 * </ul>
 *
 * <p>Each accepted row is handed to {@link #processNewRecord}, which by
 * default adds it to the data file unless a record with the same ID is already
 * there. Subclasses override that to merge instead.
 *
 * @param <T> the kind of record being loaded
 */
public class DataFileConverter<T extends DataRecord> {
   /** The delimiter between the columns of a converted file. */
   private static final char DELIMITER = ',';
   private static final char DOUBLE_QUOTE = '"';
   private static final char SINGLE_QUOTE = '\'';

   private final Map<String, FieldConverter> converterMap = new HashMap<>();
   private final Map<String, String> columnMap;
   private final DataRecordFile<T> dataFile;
   private final String[] expectedColumns;

   /** In a reject reason, what comes before the offending cell's text (which is never logged). */
   private static final String REJECT_VALUE_MARKER = " with value = ";

   // What the last convert() did, for its summary line.
   private int rowsRead;
   private int rowsRejected;
   private int recordsAdded;
   private int recordsUpdated;
   private int recordsSkipped;

   /**
    * @param dataFile the file the converted records are added to
    * @param columnMap renames headings of the incoming file to the record's
    *                  column names; a heading mapped to "" matches no column
    *                  and is therefore ignored
    * @param expectedColumns headings that must all be present for the file to
    *                        be accepted at all; null to accept any file
    */
   public DataFileConverter(DataRecordFile<T> dataFile, Map<String, String> columnMap, String[] expectedColumns) {
      this.dataFile = dataFile;
      this.columnMap = columnMap;
      this.expectedColumns = expectedColumns;
   }

   /** Registers the converter to use for the column with this exact heading. */
   public void addConverter(String column, FieldConverter converter) {
      this.converterMap.put(column, converter);
   }

   /** The converter registered for this exact heading, or null if there is none. */
   public FieldConverter getConverter(String column) {
      return this.converterMap.get(column);
   }

   /**
    * Finds the record column that a heading of the incoming file stands for.
    *
    * @return the record's own spelling of the column name, or null if the
    *         heading matches none of its columns
    */
   public String getFieldName(String heading) {
      String trimmed = heading.trim();
      String mapped = this.columnMap.get(trimmed);
      if (mapped == null) {
         mapped = trimmed;
      }

      for (String column : this.dataFile.getColumns()) {
         if (column.equalsIgnoreCase(mapped)) {
            return column;
         }
      }

      return null;
   }

   /**
    * Splits one line into its fields. A quoted stretch (double or single
    * quotes) may contain the delimiter; the quote marks themselves are dropped.
    *
    * @param headings if not null, the result has exactly one entry per heading:
    *                 missing trailing fields become "" and extra ones are
    *                 dropped. If null, the result has one entry per field found.
    * @return the fields, or null if the line could not be parsed
    */
   public String[] parseLine(String line, String[] headings, char delimiter) {
      try {
         List<String> fields = splitIntoFields(line, delimiter);
         if (headings == null) {
            return fields.toArray(new String[0]);
         }

         String[] padded = new String[headings.length];
         for (int index = 0; index < padded.length; index++) {
            padded[index] = index < fields.size() ? fields.get(index) : "";
         }
         return padded;
      } catch (Exception failure) {
         // A line that cannot be parsed (a null line is the only way) is
         // reported like a line with no columns: the caller rejects that row
         // instead of stopping the whole import.
         Log.warn("convert: a line could not be split into fields, so its row is rejected", failure);
         return null;
      }
   }

   private static List<String> splitIntoFields(String line, char delimiter) {
      List<String> fields = new ArrayList<>();
      boolean inQuotes = false;
      char openingQuote = DOUBLE_QUOTE;
      StringBuilder field = new StringBuilder();

      for (int i = 0; i < line.length(); i++) {
         char c = line.charAt(i);
         if (inQuotes) {
            if (c == openingQuote) {
               inQuotes = false;
            } else {
               field.append(c);
            }
         } else if (c == delimiter) {
            fields.add(field.toString());
            field = new StringBuilder();
         } else if (c == DOUBLE_QUOTE || c == SINGLE_QUOTE) {
            inQuotes = true;
            openingQuote = c;
         } else {
            field.append(c);
         }
      }

      fields.add(field.toString());
      return fields;
   }

   /**
    * Reads a whole file, converting each row after the heading line and
    * passing every accepted one to {@link #processNewRecord}. Rejected rows
    * are skipped and mentioned in the event log (without their content) and,
    * in full, in the verbose log.
    *
    * @throws IOException if the file cannot be read, or if its heading line
    *                     lacks one of the expected columns
    */
   public void convert(File file) throws IOException {
      this.rowsRead = 0;
      this.rowsRejected = 0;
      this.recordsAdded = 0;
      this.recordsUpdated = 0;
      this.recordsSkipped = 0;
      try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
         String line = reader.readLine();
         if (line == null) {
            Log.warn("convert: file=" + file.getName() + " is empty (no heading line), nothing to import");
            return;
         }

         String[] headings = this.parseLine(line, null, DELIMITER);
         this.requireExpectedColumns(file, headings);
         Log.info("convert: file=" + file.getName() + " into " + this.dataFile.getFileName()
            + ", " + headings.length + " headings");

         int lineNumber = 1;
         while ((line = reader.readLine()) != null) {
            this.convertRow(file, lineNumber, line, headings);
            lineNumber++;
         }
      }
      Log.info("convert: file=" + file.getName() + " into " + this.dataFile.getFileName() + " rows=" + this.rowsRead
         + " rejected=" + this.rowsRejected + " added=" + this.recordsAdded + " updated=" + this.recordsUpdated
         + " skipped=" + this.recordsSkipped);
   }

   private void requireExpectedColumns(File file, String[] headings) throws IOException {
      if (this.expectedColumns == null) {
         return;
      }

      for (String expected : this.expectedColumns) {
         if (!contains(headings, expected)) {
            // The first line is only assumed to be headings: a file with no heading row, or the
            // wrong file, has a person's record there. Log.columnNames lists the cells only when
            // some cell is a column this converter knows, and otherwise says how many there are.
            Log.warn("convert: file=" + file.getName() + " REFUSED: expected column '" + expected
               + "' is not among the headings: " + Log.columnNames(headings, this.knownHeadings())
               + " (headings are matched exactly, including capitals)");
            throw new IOException("error: '" + file.getAbsolutePath() + "'. Invalid file format: expected column: " + expected);
         }
      }
   }

   /** Every heading this converter would recognize: the expected ones, the renamed ones and the record's own columns. */
   private java.util.Set<String> knownHeadings() {
      java.util.Set<String> known = new java.util.LinkedHashSet<>();
      if (this.expectedColumns != null) {
         known.addAll(java.util.Arrays.asList(this.expectedColumns));
      }
      known.addAll(this.columnMap.keySet());
      known.addAll(this.converterMap.keySet());
      known.addAll(java.util.Arrays.asList(this.dataFile.getColumns()));
      return known;
   }

   private static boolean contains(String[] values, String wanted) {
      for (String value : values) {
         if (wanted.equals(value)) {
            return true;
         }
      }
      return false;
   }

   /** Converts one data line and keeps the record, or logs why it was rejected. */
   private void convertRow(File file, int lineNumber, String line, String[] headings) {
      T record = this.dataFile.createNew();
      String[] fields = this.parseLine(line, headings, DELIMITER);
      String rejectReason = this.fillRecord(record, headings, fields);
      this.rowsRead++;
      if (rejectReason == null) {
         record.postLoadUpdate();
         this.processNewRecord(record);
      } else {
         this.rowsRejected++;
         Log.verbose(" rejected [" + file.getName() + ":" + lineNumber + "]:" + rejectReason + " (" + line + ")");
         // The row is never logged, nor the cell that failed: either can be an
         // email address or a name. The heading and the line are enough to find it.
         // lineNumber counts data rows from 1, the file's own line numbers start at the heading.
         int valueAt = rejectReason.indexOf(REJECT_VALUE_MARKER);
         String loggableReason = valueAt < 0 ? rejectReason : rejectReason.substring(0, valueAt);
         String rejectLine = "convert: file=" + file.getName() + " line=" + (lineNumber + 1) + " REJECTED: " + loggableReason;
         if (rejectReason.startsWith("invalid col")) {
            // A cancelled signup, or an item that is not a review, is an ordinary reason to skip a row.
            Log.info(rejectLine);
         } else {
            Log.warn(rejectLine);
         }
      }
   }

   /**
    * Stores each field of a row in the record.
    *
    * @return null if the whole row was accepted, otherwise the reason it was
    *         rejected
    */
   private String fillRecord(DataRecord record, String[] headings, String[] fields) {
      if (fields == null) {
         return "no columns found in line";
      }
      if (fields.length < headings.length) {
         return "invalid # of columns. " + fields.length + " < " + headings.length;
      }

      for (int columnIndex = 0; columnIndex < headings.length; columnIndex++) {
         String heading = headings[columnIndex];
         String value = fields[columnIndex];
         FieldConverter converter = this.getConverter(heading);
         if (converter != null) {
            if (!converter.convert(record, heading, value)) {
               return "invalid col '" + heading + "'" + REJECT_VALUE_MARKER + "'" + value + "'";
            }
         } else {
            String fieldName = this.getFieldName(heading);
            if (fieldName != null) {
               record.setValue(fieldName, value);
            }
         }
      }
      return null;
   }

   /**
    * Keeps one converted record. This version adds it to the data file unless
    * a record with the same ID is already there.
    */
   public void processNewRecord(T record) {
      DataRecord existing = this.dataFile.get(record.getID());
      if (existing == null) {
         this.dataFile.add(record, false);
         this.recordsAdded++;
         Log.info("convert: added " + record.getID() + " to " + this.dataFile.getFileName());
      } else {
         this.recordsSkipped++;
         Log.info("convert: skipped " + record.getID() + ", already in " + this.dataFile.getFileName());
      }
   }

   /** Subclasses that merge instead of adding count what they did here, so the summary line is right. */
   protected final void countAdded() {
      this.recordsAdded++;
   }

   protected final void countUpdated() {
      this.recordsUpdated++;
   }

   protected final void countSkipped() {
      this.recordsSkipped++;
   }

   /** The file that converted records are added to. */
   public DataRecordFile<T> getDataFile() {
      return this.dataFile;
   }

   /** Turns one cell of the incoming file into whatever the record should hold. */
   public interface FieldConverter {
      /**
       * @param record the record being built for this row
       * @param column the heading of the cell, exactly as in the file
       * @param value the text of the cell
       * @return false to reject the whole row, true to keep it
       */
      boolean convert(DataRecord record, String column, String value);
   }

}
