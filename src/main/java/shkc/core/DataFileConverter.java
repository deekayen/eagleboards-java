package shkc.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class DataFileConverter<T extends DataRecord> {
   private Map<String, DataFileConverter.FieldConverter> _converterMap = new HashMap<>();
   private DataRecord.Factory _factory;
   private Map<String, String> _columnMap = new HashMap<>();
   private DataRecordFile<T> _dataFile;
   private DataFileConverter.Filter _filter;
   private String[] _expectedCols;

   public DataFileConverter(DataRecordFile<T> var1, DataFileConverter.Filter var2, Map<String, String> var3, String[] var4) {
      this._factory = var1._factory;
      this._dataFile = var1;
      this._columnMap = var3;
      this._filter = var2;
      this._expectedCols = var4;
   }

   public void addConverter(String var1, DataFileConverter.FieldConverter var2) {
      this._converterMap.put(var1, var2);
   }

   public DataFileConverter.FieldConverter getConverter(String var1) {
      return this._converterMap.get(var1);
   }

   public String getFieldName(String var1) {
      var1 = var1.trim();
      String var2 = var1;
      if (this._columnMap.get(var1) != null) {
         var2 = this._columnMap.get(var1);
      }

      for (String var6 : this._factory.getColumns()) {
         if (var6.equalsIgnoreCase(var2)) {
            return var6;
         }
      }

      return null;
   }

   public String[] parseLine(String var1, String[] var2, char var3) {
      try {
         ArrayList var4 = new ArrayList();
         boolean var5 = false;
         char var6 = '"';
         StringBuffer var7 = new StringBuffer();

         for (int var8 = 0; var8 < var1.length(); var8++) {
            char var9 = var1.charAt(var8);
            if (var5) {
               if (var9 == var6) {
                  var5 = false;
               } else {
                  var7.append(var9);
               }
            } else if (var9 == var3) {
               var4.add(var7.toString());
               var7 = new StringBuffer();
            } else if (var9 == '"') {
               var5 = true;
               var6 = var9;
            } else if (var9 == '\'') {
               var5 = true;
               var6 = var9;
            } else {
               var7.append(var9);
            }
         }

         var4.add(var7.toString());
         if (var2 != null) {
            String[] var12 = new String[var2.length];

            for (int var14 = 0; var14 < var4.size() && var14 < var12.length; var14++) {
               var12[var14] = (String)var4.get(var14);
            }

            for (int var15 = var4.size(); var15 < var2.length; var15++) {
               var12[var15] = "";
            }

            return var12;
         } else {
            String[] var11 = new String[var4.size()];

            for (int var13 = 0; var13 < var4.size(); var13++) {
               var11[var13] = (String)var4.get(var13);
            }

            return var11;
         }
      } catch (Exception var10) {
         var10.printStackTrace();
         return null;
      }
   }

   public void convert(File var1) throws IOException {
      BufferedReader var2 = new BufferedReader(new FileReader(var1));
      String var3 = null;
      int var4 = 0;
      String[] var5 = null;

      while ((var3 = var2.readLine()) != null) {
         if (var4 == 0) {
            var5 = this.parseLine(var3, null, ',');
            if (this._expectedCols != null) {
               for (String var9 : this._expectedCols) {
                  boolean var10 = false;

                  for (String var14 : var5) {
                     if (var9.equals(var14)) {
                        var10 = true;
                        break;
                     }
                  }

                  if (!var10) {
                     throw new IOException("error: '" + var1.getAbsolutePath() + "'. Invalid file format: expected column: " + var9);
                  }
               }
            }
         }

         if (var4 > 0) {
            DataRecord var16 = this._factory.newInstance();
            new StringTokenizer(var3, ",", false);
            boolean var17 = true;
            String[] var18 = this.parseLine(var3, var5, ',');
            String var19 = "";
            if (var18 == null) {
               var17 = false;
               var19 = "no columns found in line";
            } else if (var18.length < var5.length) {
               var17 = false;
               var19 = "invalid # of columns. " + var18.length + " < " + var5.length;
            } else {
               for (int var20 = 0; var20 < var5.length; var20++) {
                  String var21 = var18[var20];
                  DataFileConverter.FieldConverter var22 = this.getConverter(var5[var20]);
                  if (var22 != null) {
                     var17 = var22.convert(var16, var5[var20], var21);
                     var19 = "invalid col '" + var5[var20] + "' with value = '" + var21 + "'";
                  } else {
                     String var23 = this.getFieldName(var5[var20]);
                     if (var23 != null) {
                        var16.setValue(var23, var21);
                     }
                  }

                  if (this._filter != null) {
                     var17 = this._filter.filter(var5[var20], var21);
                     var19 = "filter rejected on col '" + var5[var20] + "' with value = '" + var21 + "'";
                  }

                  if (!var17) {
                     break;
                  }
               }
            }

            if (var17) {
               var16.postLoadUpdate();
               this.procesNewRecord((T)var16);
            } else {
               EagleBoardScheduler.verbose(" rejected [" + var1.getName() + ":" + var4 + "]:" + var19 + " (" + var3 + ")");
            }
         }

         var4++;
      }

      var2.close();
   }

   public void procesNewRecord(T var1) {
      String var2 = var1.getID();
      DataRecord var3 = this._dataFile.get(var2);
      if (var3 == null) {
         this._dataFile.add((T)var1, false);
      }
   }

   public DataRecordFile<T> getDataFile() {
      return this._dataFile;
   }

   public interface FieldConverter {
      boolean convert(DataRecord var1, String var2, String var3);
   }

   public interface Filter {
      boolean filter(String var1, String var2);
   }
}
