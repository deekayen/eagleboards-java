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

   public DataRecordFile(File var1, DataRecord.Factory var2) throws IOException {
      this._file = var1;
      this._factory = var2;
      if (this._file.exists()) {
         this.load();
      } else if (!this._file.createNewFile()) {
         throw new IOException("cannot create data file: " + this._file.getAbsolutePath());
      }
   }

   public T createNew() {
      return (T)this._factory.newInstance();
   }

   public T addNew(String var1, boolean var2) {
      DataRecord var3 = this._factory.newInstance();
      var3.put("ID", var1);
      this.add((T)var3, var2);
      return (T)var3;
   }

   public void add(T var1, boolean var2) {
      this._recordList.add((T)var1);
      this._recordMap.put(var1.getID(), (T)var1);
      if (var2) {
         try {
            this.store();
         } catch (Exception var4) {
            EagleBoardScheduler.error("\n\n   ERROR: exception in storing file: " + this._file.getAbsolutePath());
         }
      }
   }

   public T get(String var1) {
      return var1 == null ? null : this._recordMap.get(var1);
   }

   public List<T> get(String var1, String var2) {
      ArrayList var3 = new ArrayList();

      for (DataRecord var5 : this._recordList) {
         if (var2.equals(var5.get(var1))) {
            var3.add(var5);
         }
      }

      return var3;
   }

   public String[] getColumns() {
      return this._factory.getColumns();
   }

   public List<T> getRecords() {
      return this._recordList;
   }

   public void remove(String var1) {
      DataRecord var2 = this.get(var1);
      this._recordMap.remove(var1);
      this._recordList.remove(var2);
   }

   public synchronized void store() throws IOException {
      // A ".properties" file holds a single record as key=value lines (used
      // for the CONFIG record): one line per column, in column order, with a
      // header comment. Hex color values are written verbatim (no escaping).
      // Logic is inlined here rather than in a helper so this class's declared
      // members stay identical to the original binary (the parity gate).
      if (this._file.getName().toLowerCase().endsWith(".properties")) {
         StringBuffer var10 = new StringBuffer();
         var10.append("# Review Board Scheduler configuration\n");
         var10.append("# Edit the values after each '='. Lines starting with # are comments.\n\n");

         for (DataRecord var12 : this._recordList) {
            for (String var14 : this._factory.getColumns()) {
               String var15 = var12.getValue(var14);
               if (var15 == null) {
                  var15 = "";
               }

               var10.append(var14).append("=").append(var15).append("\n");
            }
         }

         PrintStream var16 = new PrintStream(new FileOutputStream(this._file));
         var16.print(var10.toString());
         var16.close();
         return;
      }

      boolean var1 = true;
      StringBuffer var2 = new StringBuffer();

      for (String var6 : this._factory.getColumns()) {
         if (!var1) {
            var2.append(",");
         }

         var1 = false;
         var2.append(var6);
      }

      var2.append("\n");

      for (DataRecord var9 : this._recordList) {
         var9.toCSV(var2, ',');
         var2.append("\n");
      }

      PrintStream var8 = new PrintStream(new FileOutputStream(this._file));
      var8.print(var2.toString());
      var8.close();
   }

   public synchronized void load() throws IOException {
      // ".properties" config file: parse key=value into a single record via
      // java.util.Properties. Inlined (see store()) to preserve signatures.
      if (this._file.getName().toLowerCase().endsWith(".properties")) {
         Properties var10 = new Properties();
         FileInputStream var11 = new FileInputStream(this._file);

         try {
            var10.load(var11);
         } finally {
            var11.close();
         }

         DataRecord var12 = this._factory.newInstance();
         for (String var14 : this._factory.getColumns()) {
            String var15 = var10.getProperty(var14);
            // Skip empty values so a blank key doesn't clobber a sensible
            // default (e.g. the record's DEFAULT identity); config values are
            // never legitimately empty.
            if (var15 != null && var15.length() > 0) {
               var12.put(var14, var15);
            }
         }

         this.add((T)var12, false);
         EagleBoardScheduler.verbose("LOADED: " + var12);
         return;
      }

      BufferedReader var1 = new BufferedReader(new FileReader(this._file));
      String var2 = null;
      int var3 = 0;
      String[] var4 = null;

      while ((var2 = var1.readLine()) != null) {
         if (var3 == 0) {
            StringTokenizer var5 = new StringTokenizer(var2, ",", false);
            var4 = new String[var5.countTokens()];

            for (int var6 = 0; var6 < var4.length; var6++) {
               var4[var6] = var5.nextToken();
            }
         }

         if (var3 > 0) {
            DataRecord var8 = this._factory.newInstance();
            var8.fromCSV(var2, ',', var4);
            this.add((T)var8, false);
            EagleBoardScheduler.verbose("LOADED: " + var8);
         }

         var3++;
      }

      var1.close();
   }

   public void clearAll() {
      this._recordList.clear();
      this._recordMap.clear();
   }

   @Override
   public String toString() {
      StringBuffer var1 = new StringBuffer();

      for (DataRecord var3 : this.getRecords()) {
         var3.toCSV(var1, ',');
         var1.append("\n");
      }

      return var1.toString();
   }
}
