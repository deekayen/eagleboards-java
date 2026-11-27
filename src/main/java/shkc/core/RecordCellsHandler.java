package shkc.core;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
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

   private static final String CELLS_SUFFIX = "CellsHandler";

   /** The columns {@link DataRecord#getValue} works out itself, so a page may ask for them. */
   private static final Set<String> COMPUTED_COLUMNS = Set.of("MinsSinceLastUpdate", "RegTimeHM");

   /** Past this many noted oddities nothing more is noted, so a page that asks for endless made-up names cannot grow the set. */
   private static final int MAX_NOTED = 500;

   /** The oddities already logged since the program started: each is logged once, not on every read. */
   private static final Set<String> NOTED = ConcurrentHashMap.newKeySet();

   private final EventData data;
   private final DataRecordFile<T> records;
   private final String kind;

   public RecordCellsHandler(EventData data, DataRecordFile<T> records) {
      this.data = data;
      this.records = records;
      String handlerName = this.getClass().getSimpleName();
      this.kind = handlerName.endsWith(CELLS_SUFFIX) && handlerName.length() > CELLS_SUFFIX.length()
         ? handlerName.substring(0, handlerName.length() - CELLS_SUFFIX.length()) : (handlerName.isEmpty() ? "record" : handlerName);
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
         this.noteUnusualRequest(request.getParameter("filter"), filter, format, columns, extraColumns, youthFile);
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
         ResponseText.write(response, contentType, body);
         response.setStatus(200);
      }
   }

   public DataRecordFile<T> getRecords() {
      return this.records;
   }

   /**
    * Logs what is odd about this read, and nothing about an ordinary one: the
    * pages call these endpoints after every change, so a line per read would
    * bury the log. Each oddity is logged the first time it is seen since the
    * program started. Nothing here changes what is served.
    */
   private void noteUnusualRequest(String filterParam, RowFilter filter, String format, String[] columns, String[] extraColumns, boolean youthFile) {
      if (!FORMAT_ROWS.equals(format) && !FORMAT_DATA.equals(format) && !FORMAT_CSV.equals(format)) {
         this.noteOnce("fmt:" + word(format), "cells: kind=" + this.kind + " fmt=" + word(format)
            + " is not rows, data, or csv, so the rows format was served");
      }
      if (filterParam != null && !filterParam.isEmpty() && filter.column == null) {
         int parts = new StringTokenizer(filterParam, "~#", false).countTokens();
         this.noteOnce("filter-malformed", "cells: kind=" + this.kind + " filter " + Log.safeValue("filter", filterParam)
            + " has " + parts + " part(s), not exactly Column and one value, so it was ignored and every row was served");
      }
      if (filter.column != null) {
         String column = filter.column;
         boolean withheld = !column.equals(withheldName(column, youthFile));
         if (withheld) {
            this.noteOnce("filter-withheld:" + word(column), "cells: kind=" + this.kind + " filter on " + word(column)
               + ", a column that is never served, so no row can match it");
         } else if (!this.servesColumn(column)) {
            this.noteOnce("filter-unknown:" + word(column), "cells: kind=" + this.kind + " filter on " + word(column)
               + ", which is not a column of this file, so no row matches");
         }
      }
      this.noteColumns(columns, "cols", youthFile);
      this.noteColumns(extraColumns, "data", youthFile);
   }

   private void noteColumns(String[] asked, String parameter, boolean youthFile) {
      if (asked == null) {
         return;
      }
      for (String column : asked) {
         if (!column.equals(withheldName(column, youthFile))) {
            this.noteOnce("withheld:" + word(column), "cells: kind=" + this.kind + " " + parameter + " asks for " + word(column)
               + ", which is never served (SPEC.md D-7, D-8), so it came back empty");
         } else if (!this.servesColumn(column)) {
            this.noteOnce("unknown:" + word(column), "cells: kind=" + this.kind + " " + parameter + " asks for " + word(column)
               + ", which is not a column of this file, so it came back empty");
         }
      }
   }

   /** What {@code column} is read as: itself, or the empty stand-in for a birthdate (and a youth's phone number). */
   private static String withheldName(String column, boolean youthFile) {
      String[] withheld = CheckInApi.withholdBirthdate(new String[]{column});
      return (youthFile ? CheckInApi.withholdYouthPhone(withheld) : withheld)[0];
   }

   private boolean servesColumn(String column) {
      return COMPUTED_COLUMNS.contains(column) || Arrays.asList(this.records.getColumns()).contains(column);
   }

   /** A page-supplied word, cut short and with control characters removed, safe to put in a line. */
   private static String word(String text) {
      String cleaned = Log.clean(text);
      return cleaned.length() > 40 ? cleaned.substring(0, 40) + "..." : cleaned;
   }

   private void noteOnce(String key, String message) {
      if (NOTED.size() < MAX_NOTED && NOTED.add(this.kind + "|" + key)) {
         Log.info(message + " (logged once; it is not repeated on later reads)");
      }
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
