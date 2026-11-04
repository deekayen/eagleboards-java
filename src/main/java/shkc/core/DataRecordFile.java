package shkc.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The records of one data file, held in memory and saved back to that file.
 *
 * <p>Two on-disk formats are supported, chosen by the file name (see
 * {@link RecordFileFormat#forFile}): a name ending in {@code .properties} is
 * the configuration file ({@link PropertiesRecordFormat}), anything else is a
 * CSV file ({@link CsvRecordFormat}). This class keeps the records and the
 * lookups; the format classes do the reading and writing.
 *
 * <p>Records are kept in file order ({@link #getRecords}) and also looked up
 * by ID ({@link #get(String)}). {@link #load} and {@link #store} are
 * synchronized so a read never sees a half-written file.
 *
 * @param <T> the kind of record in the file
 */
public class DataRecordFile<T extends DataRecord> {
   private final File file;
   private final DataRecord.Factory factory;
   private final RecordFileFormat format;
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
      this.format = RecordFileFormat.forFile(file);
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
      this.format.write(this.file, this.factory, this.records);
   }

   /** Reads the file and adds its records to this object's list. */
   public synchronized void load() throws IOException {
      this.format.read(this.file, this.factory, record -> {
         this.add(this.castToFileKind(record), false);
         Log.verbose("LOADED: " + record);
      });
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
         record.toCSV(out, CsvRecordFormat.DELIMITER);
         out.append("\n");
      }

      return out.toString();
   }
}
