package shkc.core;

import java.io.IOException;
import java.util.StringTokenizer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Serves a record file's rows to the Admin and Event pages, as the
 * {@code /xxx-cells} endpoints.
 *
 * <p>Request parameters: {@code cols} (the columns wanted, default all),
 * {@code data} (extra columns, for the data format), {@code fmt}
 * ({@code rows}, the default, or {@code data} or {@code csv}), {@code filter}
 * (a column and the values to keep, written {@code Column~value1#value2}) and
 * {@code filename} (makes the answer a download).
 *
 * <p>A birthdate is never served (SPEC.md D-7) and neither is a youth's phone
 * number (D-8): a column that holds one is read as empty, so the other columns
 * still line up. An adult's phone number still goes out.
 *
 * @param <T> the kind of record in the file
 */
public class RecordCellsHandler<T extends DataRecord> implements WebServer.WebHandler {
   private static final String FORMAT_ROWS = "rows";
   private static final String FORMAT_DATA = "data";
   private static final String FORMAT_CSV = "csv";
   private static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

   private final EventData data;
   private final DataRecordFile<T> records;

   public RecordCellsHandler(EventData data, DataRecordFile<T> records) {
      this.data = data;
      this.records = records;
   }

   @Override
   public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      synchronized (this.data.getLock()) {
         Log.request(request);
         String colsParam = request.getParameter("cols");
         String dataParam = request.getParameter("data");
         String format = request.getParameter("fmt");
         String fileName = request.getParameter("filename");
         if (format == null) {
            format = FORMAT_ROWS;
         }

         RowFilter filter = RowFilter.parse(request.getParameter("filter"));
         Log.verbose("COLS: " + colsParam);
         Log.verbose("DATA: " + dataParam);
         Log.verbose("FILTER: " + filter.column + " ~ " + filter.values);

         String[] columns = RequestFields.columns(colsParam, this.records.getColumns());
         String[] extraColumns = RequestFields.columns(dataParam, null);
         // SPEC.md D-7 / O-5: a birthdate already on file stays there but is
         // never served. The values are read through these copies, where DOB
         // names a column that holds nothing; `columns` keeps the real names
         // for the CSV header, so every column still lines up.
         String[] valueColumns = CheckInApi.withholdBirthdate(columns);
         String[] valueExtraColumns = CheckInApi.withholdBirthdate(extraColumns);
         // SPEC.md D-8: a youth's phone number is kept and withheld the same
         // way. This handler serves the adult files too, and theirs go out.
         boolean youthFile = this.data.isYouthFile(this.records);
         if (youthFile) {
            valueColumns = CheckInApi.withholdYouthPhone(valueColumns);
            valueExtraColumns = CheckInApi.withholdYouthPhone(valueExtraColumns);
         }
         // Nor may a filter on a withheld column tell which rows hold what.
         filter = filter.withholding(youthFile);

         StringBuffer out = new StringBuffer();
         String contentType;
         if (format.equals(FORMAT_DATA)) {
            contentType = "text/xml";
            this.appendDataFormat(out, filter, valueColumns, valueExtraColumns, format);
         } else if (format.equals(FORMAT_CSV)) {
            contentType = "text/csv";
            this.appendCsvFormat(out, filter, columns, valueColumns, format);
         } else {
            contentType = "text/xml";
            this.appendRowsFormat(out, filter, valueColumns, valueExtraColumns);
         }

         if (fileName != null && fileName.length() > 0) {
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
         }

         String body = out.toString();
         response.setContentLength(body.length());
         response.setContentType(contentType);
         response.getOutputStream().write(body.getBytes());
         response.setStatus(200);
      }
   }

   public DataRecordFile<T> getRecords() {
      return this.records;
   }

   /** The {@code data} format: one {@code <data>} element holding every wanted record. */
   private void appendDataFormat(StringBuffer out, RowFilter filter, String[] valueColumns, String[] valueExtraColumns, String format) {
      out.append(XML_DECLARATION).append("<data>");

      for (DataRecord record : this.records.getRecords()) {
         if (filter.keeps(record)) {
            record.toString(out, valueColumns, valueExtraColumns, format);
            out.append("\n");
         }
      }

      out.append("</data>");
   }

   /** The {@code csv} format: a header of the real column names, then one line per wanted record. */
   private void appendCsvFormat(StringBuffer out, RowFilter filter, String[] columns, String[] valueColumns, String format) {
      boolean firstColumn = true;

      for (String column : columns) {
         if (!firstColumn) {
            out.append(",");
         }

         firstColumn = false;
         out.append(column);
      }

      out.append("\n");

      for (DataRecord record : this.records.getRecords()) {
         if (filter.keeps(record)) {
            record.toString(out, valueColumns, null, format);
            out.append("\n");
         }
      }
   }

   /** The default {@code rows} format: one {@code <row>} per wanted record, with a {@code <cell>} per column. */
   private void appendRowsFormat(StringBuffer out, RowFilter filter, String[] valueColumns, String[] valueExtraColumns) {
      out.append(XML_DECLARATION).append("<rows>");

      for (DataRecord record : this.records.getRecords()) {
         if (filter.keeps(record)) {
            record.toCells(out, valueColumns, valueExtraColumns);
            out.append("\n");
         }
      }

      out.append("</rows>");
   }

   /**
    * The optional {@code filter} parameter: a column name and the values to
    * keep, joined by {@code ~} (or {@code #}). When the parameter is missing or
    * is not exactly those two parts, every record is kept.
    */
   private static final class RowFilter {
      private final String column;
      private final String values;

      private RowFilter(String column, String values) {
         this.column = column;
         this.values = values;
      }

      static RowFilter parse(String filterParam) {
         if (filterParam != null) {
            StringTokenizer filterTokens = new StringTokenizer(filterParam, "~#", false);
            if (filterTokens.countTokens() == 2) {
               return new RowFilter(filterTokens.nextToken().trim(), filterTokens.nextToken().trim());
            }
         }
         return new RowFilter(null, null);
      }

      /** The same filter, aimed at an empty column when the one asked for is withheld (D-7, D-8). */
      RowFilter withholding(boolean youthFile) {
         if (this.column == null) {
            return this;
         }
         String[] withheldBirthdate = CheckInApi.withholdBirthdate(new String[]{this.column});
         String[] valueColumn = youthFile ? CheckInApi.withholdYouthPhone(withheldBirthdate) : withheldBirthdate;
         return new RowFilter(valueColumn[0], this.values);
      }

      /**
       * Whether the record passes. With a filter, the record's value in the
       * filter column must be non-empty and appear within the filter values
       * (a substring match, as it always was).
       */
      boolean keeps(DataRecord record) {
         if (this.values == null || this.column == null) {
            return true;
         }
         String cellValue = record.get(this.column);
         return cellValue != null && cellValue.length() > 0 && this.values.indexOf(cellValue) >= 0;
      }
   }
}
