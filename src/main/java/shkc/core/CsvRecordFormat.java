package shkc.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.StringTokenizer;
import java.util.function.Consumer;

/**
 * A CSV file: the column names on the first line, then one record per line.
 * Every data file except the configuration is kept this way.
 */
final class CsvRecordFormat implements RecordFileFormat {
   static final char DELIMITER = ',';

   @Override
   public void read(File file, DataRecord.Factory factory, Consumer<DataRecord> sink) throws IOException {
      try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
         String headerLine = reader.readLine();
         if (headerLine == null) {
            return;
         }

         String[] columns = splitHeader(headerLine);

         String line;
         while ((line = reader.readLine()) != null) {
            DataRecord record = factory.newInstance();
            record.fromCSV(line, DELIMITER, columns);
            sink.accept(record);
         }
      }
   }

   /** The column names, then one line per record. */
   @Override
   public void write(File file, DataRecord.Factory factory, List<? extends DataRecord> records) throws IOException {
      StringBuffer csv = new StringBuffer();

      boolean first = true;
      for (String columnName : factory.getColumns()) {
         if (!first) {
            csv.append(DELIMITER);
         }

         first = false;
         csv.append(columnName);
      }

      csv.append("\n");

      for (DataRecord record : records) {
         record.toCSV(csv, DELIMITER);
         csv.append("\n");
      }

      RecordFileFormat.writeText(file, csv);
   }

   private static String[] splitHeader(String headerLine) {
      StringTokenizer headerTokens = new StringTokenizer(headerLine, String.valueOf(DELIMITER), false);
      String[] columns = new String[headerTokens.countTokens()];

      for (int columnIndex = 0; columnIndex < columns.length; columnIndex++) {
         columns[columnIndex] = headerTokens.nextToken();
      }

      return columns;
   }
}
