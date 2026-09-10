package shkc.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DataFileConverter<T extends DataRecord> {
   private Map<String, DataFileConverter.FieldConverter> _converterMap = new HashMap<>();
   private DataRecord.Factory _factory;
   private Map<String, String> _columnMap = new HashMap<>();
   private DataRecordFile<T> _dataFile;
   private String[] _expectedCols;

   public DataFileConverter(DataRecordFile<T> dataFile, Map<String, String> columnMap, String[] expectedCols) {
      this._factory = dataFile._factory;
      this._dataFile = dataFile;
      this._columnMap = columnMap;
      this._expectedCols = expectedCols;
   }

   public void addConverter(String column, DataFileConverter.FieldConverter converter) {
      this._converterMap.put(column, converter);
   }

   public DataFileConverter.FieldConverter getConverter(String column) {
      return this._converterMap.get(column);
   }

   public String getFieldName(String heading) {
      heading = heading.trim();
      String mapped = heading;
      if (this._columnMap.get(heading) != null) {
         mapped = this._columnMap.get(heading);
      }

      for (String column : this._factory.getColumns()) {
         if (column.equalsIgnoreCase(mapped)) {
            return column;
         }
      }

      return null;
   }

   public String[] parseLine(String line, String[] headings, char delimiter) {
      try {
         ArrayList fields = new ArrayList();
         boolean inQuotes = false;
         char quoteChar = '"';
         StringBuffer field = new StringBuffer();

         for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
               if (c == quoteChar) {
                  inQuotes = false;
               } else {
                  field.append(c);
               }
            } else if (c == delimiter) {
               fields.add(field.toString());
               field = new StringBuffer();
            } else if (c == '"') {
               inQuotes = true;
               quoteChar = c;
            } else if (c == '\'') {
               inQuotes = true;
               quoteChar = c;
            } else {
               field.append(c);
            }
         }

         fields.add(field.toString());
         if (headings != null) {
            String[] padded = new String[headings.length];

            for (int fieldIndex = 0; fieldIndex < fields.size() && fieldIndex < padded.length; fieldIndex++) {
               padded[fieldIndex] = (String)fields.get(fieldIndex);
            }

            for (int padIndex = fields.size(); padIndex < headings.length; padIndex++) {
               padded[padIndex] = "";
            }

            return padded;
         } else {
            String[] exact = new String[fields.size()];

            for (int copyIndex = 0; copyIndex < fields.size(); copyIndex++) {
               exact[copyIndex] = (String)fields.get(copyIndex);
            }

            return exact;
         }
      } catch (Exception failure) {
         failure.printStackTrace();
         return null;
      }
   }

   public void convert(File file) throws IOException {
      BufferedReader reader = new BufferedReader(new FileReader(file));
      String line = null;
      int lineNumber = 0;
      String[] headings = null;

      while ((line = reader.readLine()) != null) {
         if (lineNumber == 0) {
            headings = this.parseLine(line, null, ',');
            if (this._expectedCols != null) {
               for (String expected : this._expectedCols) {
                  boolean found = false;

                  for (String heading : headings) {
                     if (expected.equals(heading)) {
                        found = true;
                        break;
                     }
                  }

                  if (!found) {
                     throw new IOException("error: '" + file.getAbsolutePath() + "'. Invalid file format: expected column: " + expected);
                  }
               }
            }
         }

         if (lineNumber > 0) {
            DataRecord record = this._factory.newInstance();
            boolean accepted = true;
            String[] fields = this.parseLine(line, headings, ',');
            String rejectReason = "";
            if (fields == null) {
               accepted = false;
               rejectReason = "no columns found in line";
            } else if (fields.length < headings.length) {
               accepted = false;
               rejectReason = "invalid # of columns. " + fields.length + " < " + headings.length;
            } else {
               for (int columnIndex = 0; columnIndex < headings.length; columnIndex++) {
                  String value = fields[columnIndex];
                  DataFileConverter.FieldConverter converter = this.getConverter(headings[columnIndex]);
                  if (converter != null) {
                     accepted = converter.convert(record, headings[columnIndex], value);
                     rejectReason = "invalid col '" + headings[columnIndex] + "' with value = '" + value + "'";
                  } else {
                     String fieldName = this.getFieldName(headings[columnIndex]);
                     if (fieldName != null) {
                        record.setValue(fieldName, value);
                     }
                  }

                  if (!accepted) {
                     break;
                  }
               }
            }

            if (accepted) {
               record.postLoadUpdate();
               this.procesNewRecord((T)record);
            } else {
               EagleBoardScheduler.verbose(" rejected [" + file.getName() + ":" + lineNumber + "]:" + rejectReason + " (" + line + ")");
            }
         }

         lineNumber++;
      }

      reader.close();
   }

   public void procesNewRecord(T record) {
      String id = record.getID();
      DataRecord existing = this._dataFile.get(id);
      if (existing == null) {
         this._dataFile.add((T)record, false);
      }
   }

   public DataRecordFile<T> getDataFile() {
      return this._dataFile;
   }

   public interface FieldConverter {
      boolean convert(DataRecord record, String column, String value);
   }

}
