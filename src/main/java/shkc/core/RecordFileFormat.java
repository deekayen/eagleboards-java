package shkc.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * One way of keeping a file of records on disk. {@link DataRecordFile} holds the
 * records in memory and hands the reading and writing of the file to one of
 * these, chosen by {@link #forFile}.
 */
interface RecordFileFormat {
   /**
    * Reads every record in the file, handing each to {@code sink} as soon as it is read.
    *
    * @param factory makes the blank records to fill in, and names the columns
    */
   void read(File file, DataRecord.Factory factory, Consumer<DataRecord> sink) throws IOException;

   /** Replaces the file's contents with these records, in this order. */
   void write(File file, DataRecord.Factory factory, List<? extends DataRecord> records) throws IOException;

   /** The format of this file: a {@code .properties} name is the configuration file, anything else is CSV. */
   static RecordFileFormat forFile(File file) {
      boolean isProperties = file.getName().toLowerCase().endsWith(PropertiesRecordFormat.EXTENSION);
      return isProperties ? new PropertiesRecordFormat() : new CsvRecordFormat();
   }

   /** Replaces the file's contents with {@code text}. */
   static void writeText(File file, CharSequence text) throws IOException {
      try (PrintStream writer = new PrintStream(new FileOutputStream(file))) {
         writer.print(text);
      }
   }
}
