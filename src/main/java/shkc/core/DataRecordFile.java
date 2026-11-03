package shkc.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * The records of one data file, held in memory and saved back to that file.
 *
 * <p>Two on-disk formats are supported, chosen by the file name. A name ending
 * in {@code .properties} is the configuration file: one record, one
 * {@code column=value} line per column. Anything else is a CSV file with the
 * column names on the first line and one record per line after it.
 *
 * <p>Records are kept in file order ({@link #getRecords}) and also looked up
 * by ID ({@link #get(String)}). {@link #load} and {@link #store} are
 * synchronized so a read never sees a half-written file.
 *
 * @param <T> the kind of record in the file
 */
public class DataRecordFile<T extends DataRecord> {
   private static final String PROPERTIES_EXTENSION = ".properties";
   private static final char CSV_DELIMITER = ',';

   private final File file;
   private final DataRecord.Factory factory;
   private final Map<String, T> recordsById = new HashMap<>();
   private final List<T> records = new ArrayList<>();

   /**
    * Opens a data file: loads its records if it exists, otherwise creates it empty.
    *
    * @throws IOException if the file cannot be read or created
    */
   public DataRecordFile(File file, DataRecord.Factory factory) throws IOException {
      this.file = file;
      this.factory = factory;
      if (this.file.exists()) {
         this.load();
      } else if (!this.file.createNewFile()) {
         throw new IOException("cannot create data file: " + this.file.getAbsolutePath());
      }
   }

   /** A new, blank record of this file's kind. It is not added to the file. */
   public T createNew() {
      return this.newRecord();
   }

   /** Creates a blank record with the given ID and adds it; see {@link #add}. */
   public T addNew(String id, boolean persist) {
      T record = this.newRecord();
      record.put(DataRecord.COLUMN_ID, id);
      this.add(record, persist);
      return record;
   }

   /** The factory only knows it makes {@link DataRecord}s; this file's factory always makes {@code T}. */
   @SuppressWarnings("unchecked")
   private T newRecord() {
      return (T)this.factory.newInstance();
   }

   /**
    * Adds a record to the end of the file's list.
    *
    * @param persist true to write the whole file out straight away; a failure
    *                to write is logged, not thrown
    */
   public void add(T record, boolean persist) {
      this.records.add(record);
      this.recordsById.put(record.getID(), record);
      if (persist) {
         try {
            this.store();
         } catch (Exception storeFailure) {
            Log.error("\n\n   ERROR: exception in storing file: " + this.file.getAbsolutePath());
         }
      }
   }

   /** The record with this ID, or null if there is none (or {@code id} is null). */
   public T get(String id) {
      return id == null ? null : this.recordsById.get(id);
   }

   /** Every record whose {@code column} holds exactly {@code value}, in file order. */
   public List<T> get(String column, String value) {
      List<T> matches = new ArrayList<>();

      for (T record : this.records) {
         if (value.equals(record.get(column))) {
            matches.add(record);
         }
      }

      return matches;
   }

   public String[] getColumns() {
      return this.factory.getColumns();
   }

   /** The live list of records, in file order; changes to it change the file's contents. */
   public List<T> getRecords() {
      return this.records;
   }

   /** Removes the record with this ID from memory. The file is not rewritten until {@link #store}. */
   public void remove(String id) {
      T record = this.get(id);
      this.recordsById.remove(id);
      this.records.remove(record);
   }

   /** Writes every record to the file, replacing its contents. */
   public synchronized void store() throws IOException {
      if (this.isPropertiesFile()) {
         this.storeProperties();
      } else {
         this.storeCsv();
      }
   }

   private boolean isPropertiesFile() {
      return this.file.getName().toLowerCase().endsWith(PROPERTIES_EXTENSION);
   }

   /**
    * Writes a {@code .properties} file (the CONFIG record): a header comment,
    * then one line per column in column order. Values are written verbatim,
    * with no escaping, which is what keeps hex color values readable.
    */
   private void storeProperties() throws IOException {
      StringBuilder out = new StringBuilder();
      out.append("# Eagle Board Scheduler configuration\n");
      out.append("# Edit the values after each '='. Lines starting with # are comments.\n\n");

      for (DataRecord record : this.records) {
         for (String column : this.factory.getColumns()) {
            out.append(column).append("=").append(record.getValue(column)).append("\n");
         }
      }

      writeFile(out);
   }

   /** Writes a CSV file: the column names, then one line per record. */
   private void storeCsv() throws IOException {
      StringBuffer csv = new StringBuffer();

      boolean first = true;
      for (String columnName : this.factory.getColumns()) {
         if (!first) {
            csv.append(CSV_DELIMITER);
         }

         first = false;
         csv.append(columnName);
      }

      csv.append("\n");

      for (DataRecord record : this.records) {
         record.toCSV(csv, CSV_DELIMITER);
         csv.append("\n");
      }

      writeFile(csv);
   }

   private void writeFile(CharSequence text) throws IOException {
      try (PrintStream writer = new PrintStream(new FileOutputStream(this.file))) {
         writer.print(text);
      }
   }

   /** Reads the file and adds its records to this object's list. */
   public synchronized void load() throws IOException {
      if (this.isPropertiesFile()) {
         this.loadProperties();
      } else {
         this.loadCsv();
      }
   }

   /** Reads a {@code .properties} file into a single record, via {@link Properties}. */
   private void loadProperties() throws IOException {
      Properties props = new Properties();
      try (InputStream in = new FileInputStream(this.file)) {
         props.load(in);
      }

      DataRecord record = this.factory.newInstance();
      for (String column : this.factory.getColumns()) {
         String value = props.getProperty(column);
         // Skip empty values so a blank key doesn't clobber a sensible
         // default (e.g. the record's DEFAULT identity); config values are
         // never legitimately empty.
         if (value != null && value.length() > 0) {
            record.put(column, value);
         }
      }

      this.add(this.castToFileKind(record), false);
      Log.verbose("LOADED: " + record);
   }

   /** Reads a CSV file: the first line names the columns, every later line is a record. */
   private void loadCsv() throws IOException {
      try (BufferedReader reader = new BufferedReader(new FileReader(this.file))) {
         String headerLine = reader.readLine();
         if (headerLine == null) {
            return;
         }

         String[] columns = splitHeader(headerLine);

         String line;
         while ((line = reader.readLine()) != null) {
            DataRecord record = this.factory.newInstance();
            record.fromCSV(line, CSV_DELIMITER, columns);
            this.add(this.castToFileKind(record), false);
            Log.verbose("LOADED: " + record);
         }
      }
   }

   private static String[] splitHeader(String headerLine) {
      StringTokenizer headerTokens = new StringTokenizer(headerLine, String.valueOf(CSV_DELIMITER), false);
      String[] columns = new String[headerTokens.countTokens()];

      for (int columnIndex = 0; columnIndex < columns.length; columnIndex++) {
         columns[columnIndex] = headerTokens.nextToken();
      }

      return columns;
   }

   @SuppressWarnings("unchecked")
   private T castToFileKind(DataRecord record) {
      return (T)record;
   }

   /** Forgets every record in memory. The file itself is untouched until {@link #store}. */
   public void clearAll() {
      this.records.clear();
      this.recordsById.clear();
   }

   /** The records as CSV lines, without the header line. */
   @Override
   public String toString() {
      StringBuffer out = new StringBuffer();

      for (DataRecord record : this.getRecords()) {
         record.toCSV(out, CSV_DELIMITER);
         out.append("\n");
      }

      return out.toString();
   }
}
