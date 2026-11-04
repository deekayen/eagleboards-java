package shkc.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * A {@code .properties} file: the configuration, which is a single record held
 * as one {@code column=value} line per column.
 */
final class PropertiesRecordFormat implements RecordFileFormat {
   static final String EXTENSION = ".properties";

   /** Reads the file into a single record, via {@link Properties}. */
   @Override
   public void read(File file, DataRecord.Factory factory, Consumer<DataRecord> sink) throws IOException {
      Properties props = new Properties();
      try (InputStream in = new FileInputStream(file)) {
         props.load(in);
      }

      DataRecord record = factory.newInstance();
      for (String column : factory.getColumns()) {
         String value = props.getProperty(column);
         // Skip empty values so a blank key doesn't clobber a sensible
         // default (e.g. the record's DEFAULT identity); config values are
         // never legitimately empty.
         if (value != null && value.length() > 0) {
            record.put(column, value);
         }
      }

      sink.accept(record);
   }

   /**
    * A header comment, then one line per column in column order. Values are
    * written verbatim, with no escaping, which is what keeps hex color values
    * readable.
    */
   @Override
   public void write(File file, DataRecord.Factory factory, List<? extends DataRecord> records) throws IOException {
      StringBuilder out = new StringBuilder();
      out.append("# Eagle Board Scheduler configuration\n");
      out.append("# Edit the values after each '='. Lines starting with # are comments.\n\n");

      for (DataRecord record : records) {
         for (String column : factory.getColumns()) {
            out.append(column).append("=").append(record.getValue(column)).append("\n");
         }
      }

      RecordFileFormat.writeText(file, out);
   }
}
