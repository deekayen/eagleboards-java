package shkc.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
      List<String> defaulted = new ArrayList<>();
      for (String column : factory.getColumns()) {
         String value = props.getProperty(column);
         // Skip empty values so a blank key doesn't clobber a sensible
         // default (e.g. the record's DEFAULT identity); config values are
         // never legitimately empty.
         if (value != null && value.length() > 0) {
            record.put(column, value);
         } else {
            defaulted.add(column);
         }
      }

      // Which settings are in force is the first thing to ask about a timer
      // that behaved oddly, so name the ones that fell back to a default.
      if (!defaulted.isEmpty()) {
         Log.info("load: file=" + file.getName() + " has no value for " + defaulted + ", the defaults are used");
      }
      List<String> unknown = new ArrayList<>();
      for (String key : props.stringPropertyNames()) {
         if (!Arrays.asList(factory.getColumns()).contains(key)) {
            unknown.add(key);
         }
      }
      if (!unknown.isEmpty()) {
         // A file that is not a settings file at all has none of the known keys, and its keys could be anything.
         String shown = Log.looksLikeHeading(props.stringPropertyNames().toArray(new String[0]), Arrays.asList(factory.getColumns()))
            ? unknown.stream().map(Log::columnName).sorted().toList().toString()
            : unknown.size() + " keys, none of them a setting this version knows (is this a settings file?)";
         Log.info("load: file=" + file.getName() + " has settings this version does not know: " + shown + ", they are ignored");
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
