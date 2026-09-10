package shkc.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

public class DataRecordFile<T extends DataRecord> {
   private File _file;
   DataRecord.Factory _factory = null;
   private Map<String, T> _recordMap = new HashMap<>();
   private List<T> _recordList = new ArrayList<>();

   public DataRecordFile(File file, DataRecord.Factory factory) throws IOException {
      this._file = file;
      this._factory = factory;
      if (this._file.exists()) {
         this.load();
      } else if (!this._file.createNewFile()) {
         throw new IOException("cannot create data file: " + this._file.getAbsolutePath());
      }
   }

   public T createNew() {
      return (T)this._factory.newInstance();
   }

   public T addNew(String id, boolean persist) {
      DataRecord record = this._factory.newInstance();
      record.put("ID", id);
      this.add((T)record, persist);
      return (T)record;
   }

   public void add(T record, boolean persist) {
      this._recordList.add((T)record);
      this._recordMap.put(record.getID(), (T)record);
      if (persist) {
         try {
            this.store();
         } catch (Exception storeFailure) {
            EagleBoardScheduler.error("\n\n   ERROR: exception in storing file: " + this._file.getAbsolutePath());
         }
      }
   }

   public T get(String id) {
      return id == null ? null : this._recordMap.get(id);
   }

   public List<T> get(String column, String value) {
      ArrayList matches = new ArrayList();

      for (DataRecord record : this._recordList) {
         if (value.equals(record.get(column))) {
            matches.add(record);
         }
      }

      return matches;
   }

   public String[] getColumns() {
      return this._factory.getColumns();
   }

   public List<T> getRecords() {
      return this._recordList;
   }

   public void remove(String id) {
      DataRecord record = this.get(id);
      this._recordMap.remove(id);
      this._recordList.remove(record);
   }

   public synchronized void store() throws IOException {
      // A ".properties" file holds a single record as key=value lines (used
      // for the CONFIG record): one line per column, in column order, with a
      // header comment. Hex color values are written verbatim (no escaping).
      // Logic is inlined here rather than in a helper so this class's declared
      // members stay identical to the original binary (the parity gate).
      if (this._file.getName().toLowerCase().endsWith(".properties")) {
         StringBuffer out = new StringBuffer();
         out.append("# Review Board Scheduler configuration\n");
         out.append("# Edit the values after each '='. Lines starting with # are comments.\n\n");

         for (DataRecord record : this._recordList) {
            for (String column : this._factory.getColumns()) {
               String value = record.getValue(column);
               if (value == null) {
                  value = "";
               }

               out.append(column).append("=").append(value).append("\n");
            }
         }

         PrintStream writer = new PrintStream(new FileOutputStream(this._file));
         writer.print(out.toString());
         writer.close();
         return;
      }

      boolean first = true;
      StringBuffer csv = new StringBuffer();

      for (String columnName : this._factory.getColumns()) {
         if (!first) {
            csv.append(",");
         }

         first = false;
         csv.append(columnName);
      }

      csv.append("\n");

      for (DataRecord csvRecord : this._recordList) {
         csvRecord.toCSV(csv, ',');
         csv.append("\n");
      }

      PrintStream csvWriter = new PrintStream(new FileOutputStream(this._file));
      csvWriter.print(csv.toString());
      csvWriter.close();
   }

   public synchronized void load() throws IOException {
      // ".properties" config file: parse key=value into a single record via
      // java.util.Properties. Inlined (see store()) to preserve signatures.
      if (this._file.getName().toLowerCase().endsWith(".properties")) {
         Properties props = new Properties();
         FileInputStream in = new FileInputStream(this._file);

         try {
            props.load(in);
         } finally {
            in.close();
         }

         DataRecord record = this._factory.newInstance();
         for (String column : this._factory.getColumns()) {
            String value = props.getProperty(column);
            // Skip empty values so a blank key doesn't clobber a sensible
            // default (e.g. the record's DEFAULT identity); config values are
            // never legitimately empty.
            if (value != null && value.length() > 0) {
               record.put(column, value);
            }
         }

         this.add((T)record, false);
         EagleBoardScheduler.verbose("LOADED: " + record);
         return;
      }

      BufferedReader reader = new BufferedReader(new FileReader(this._file));
      String line = null;
      int lineNumber = 0;
      String[] columns = null;

      while ((line = reader.readLine()) != null) {
         if (lineNumber == 0) {
            StringTokenizer headerTokens = new StringTokenizer(line, ",", false);
            columns = new String[headerTokens.countTokens()];

            for (int columnIndex = 0; columnIndex < columns.length; columnIndex++) {
               columns[columnIndex] = headerTokens.nextToken();
            }
         }

         if (lineNumber > 0) {
            DataRecord csvRecord = this._factory.newInstance();
            csvRecord.fromCSV(line, ',', columns);
            this.add((T)csvRecord, false);
            EagleBoardScheduler.verbose("LOADED: " + csvRecord);
         }

         lineNumber++;
      }

      reader.close();
   }

   public void clearAll() {
      this._recordList.clear();
      this._recordMap.clear();
   }

   @Override
   public String toString() {
      StringBuffer out = new StringBuffer();

      for (DataRecord record : this.getRecords()) {
         record.toCSV(out, ',');
         out.append("\n");
      }

      return out.toString();
   }
}
