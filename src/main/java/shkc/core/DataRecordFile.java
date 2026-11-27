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
   /** A save slower than this is a warning: it holds up every request. */
   private static final long SLOW_STORE_MILLIS = 500L;
   private static final long NANOS_PER_MILLI = 1_000_000L;

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
            Log.error("add: exception in storing file: " + this.file.getAbsolutePath()
               + " (the record is in memory but not on disk: id=" + record.getID() + ")", storeFailure);
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

   /** The name of the file on disk, without its folder, for log lines. */
   public String getFileName() {
      return this.file.getName();
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
      long startedNanos = System.nanoTime();
      try {
         this.format.write(this.file, this.factory, this.records);
      } catch (IOException | RuntimeException storeFailure) {
         // The callers decide what to do about it; this line says which file, and why.
         Log.error("store FAILED: file=" + this.file.getName() + " records=" + this.records.size()
            + " path=" + this.file.getAbsolutePath() + " cause=" + storeFailure, null);
         throw storeFailure;
      }
      long elapsedMillis = (System.nanoTime() - startedNanos) / NANOS_PER_MILLI;
      if (elapsedMillis > SLOW_STORE_MILLIS) {
         Log.warn("store SLOW: file=" + this.file.getName() + " records=" + this.records.size() + " took " + elapsedMillis
            + " ms, and every request waits for it (a slow disk, a virus scanner, or a synced folder)");
      } else {
         Log.verbose("stored " + this.file.getName() + ": " + this.records.size() + " records, " + elapsedMillis + " ms");
      }
   }

   /** Reads the file and adds its records to this object's list. */
   public synchronized void load() throws IOException {
      int[] loadedCount = {0};
      try {
         this.format.read(this.file, this.factory, record -> {
            loadedCount[0]++;
            this.warnIfIdIsOddOnLoad(record, loadedCount[0]);
            this.add(this.castToFileKind(record), false);
            Log.verbose("LOADED: " + record);
         });
      } catch (IOException | RuntimeException loadFailure) {
         Log.error("load FAILED: file=" + this.file.getName() + " after " + loadedCount[0] + " records path="
            + this.file.getAbsolutePath() + " cause=" + loadFailure, null);
         throw loadFailure;
      }
      Log.info("loaded " + this.file.getName() + ": " + loadedCount[0] + " records");
   }

   /**
    * A record with no ID cannot be looked up, and a second record with an ID
    * already loaded hides the first from {@link #get(String)} while both stay
    * in {@link #getRecords}; either one explains a person who "vanished" or
    * appears twice. Both are still loaded, as they always were. (A person's
    * blank ID is filled in while the line is read, before this runs, so "no
    * ID" can only be seen here for a room or a setting; an ID that was
    * replaced is reported by {@link PersonRecord#postLoadUpdate}.)
    */
   private void warnIfIdIsOddOnLoad(DataRecord record, int recordNumber) {
      String id = record.getID();
      if (id == null || id.isEmpty()) {
         Log.warn("load: file=" + this.file.getName() + " record #" + recordNumber + " has no ID, it cannot be looked up");
      } else if (this.recordsById.containsKey(id)) {
         Log.warn("load: file=" + this.file.getName() + " record #" + recordNumber + " repeats the ID " + id
            + ", lookups find only the later one");
      }
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
