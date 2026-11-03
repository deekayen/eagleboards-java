package shkc.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * One row of one of the program's CSV data files: a scout, an adult, a room or
 * the configuration.
 *
 * <p>A record is a {@link HashMap} from column name to text value, so any
 * column can be read with {@code get} and written with {@code put}. On top of
 * that it knows which columns its file has, keeps the {@code RegTime} and
 * {@code LastUpdateTime} stamps, and can write itself out as CSV, JSON or the
 * XML the browser tables read.
 *
 * <p>Subclasses ({@link ScoutRecord}, {@link AdultRecord}, {@link RoomRecord},
 * {@link ConfigRecord}) supply the column list and typed accessors.
 */
public class DataRecord extends HashMap<String, String> {
   static final String COLUMN_TYPE = "Type";
   static final String COLUMN_ID = "ID";
   static final String COLUMN_REG_TIME = "RegTime";
   static final String COLUMN_LAST_UPDATE_TIME = "LastUpdateTime";

   /** Not stored in the file: computed on request by {@link #getValue}. */
   private static final String COLUMN_MINS_SINCE_LAST_UPDATE = "MinsSinceLastUpdate";
   /** Not stored in the file: computed on request by {@link #getValue}. */
   private static final String COLUMN_REG_TIME_HM = "RegTimeHM";

   /** Timestamp text such as {@code 2026-11-02_14:05-0500}. */
   private static final String TIME_FORMAT_PATTERN = "yyyy-MM-dd_HH:mmZ";
   private static final long MILLIS_PER_MINUTE = 60000L;
   private static final int MINUTES_PER_DAY = 1440;

   /** Where the "HH:mm" part sits inside a {@link #TIME_FORMAT_PATTERN} timestamp. */
   private static final int HOUR_MINUTE_START = 11;
   private static final int HOUR_MINUTE_END = 16;

   private final String[] recordColumns;

   /**
    * Creates a record of the given kind with every one of its columns present.
    *
    * @param recordType the value of the {@code Type} column, such as {@code SCOUT}
    * @param columns the columns of this kind of record, in file order
    * @param values starting values, such as a request's parameter map; may be
    *               null. Entries whose value is a {@code String} or a
    *               {@code String[]} (the first element is taken) are copied;
    *               anything else is ignored. Columns left unset become "".
    */
   public DataRecord(String recordType, String[] columns, Map<String, ?> values) {
      this.recordColumns = columns;
      this.put(COLUMN_TYPE, recordType);
      if (values != null) {
         for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String[]) {
               String[] parameterValues = (String[])value;
               if (parameterValues.length > 0) {
                  this.put(entry.getKey(), parameterValues[0]);
               }
            } else if (value instanceof String) {
               this.put(entry.getKey(), (String)value);
            }
         }
      }

      this.put(COLUMN_LAST_UPDATE_TIME, currentTimestamp());
      this.put(COLUMN_REG_TIME, currentTimestamp());

      for (String column : this.recordColumns) {
         if (this.get(column) == null) {
            this.put(column, "");
         }
      }
   }

   private static String currentTimestamp() {
      return newTimeFormat().format(new Date());
   }

   /**
    * A fresh formatter for every use. {@link SimpleDateFormat} keeps working
    * state inside itself, so one shared instance can produce garbled
    * timestamps when the web server's request threads use it at the same time.
    */
   private static SimpleDateFormat newTimeFormat() {
      return new SimpleDateFormat(TIME_FORMAT_PATTERN);
   }

   /**
    * Called after a record is read from a file. A timestamp from an earlier
    * day (or from the future) is replaced with the current time, because the
    * room timers only make sense within one event day.
    */
   public void postLoadUpdate() {
      restampIfNotFromToday(COLUMN_LAST_UPDATE_TIME);
      restampIfNotFromToday(COLUMN_REG_TIME);
   }

   private void restampIfNotFromToday(String timeColumn) {
      int minsSince = getMinsSinceTime(this.getValue(timeColumn));
      if (minsSince < 0 || minsSince > MINUTES_PER_DAY) {
         this.setValue(timeColumn, currentTimestamp());
      }
   }

   /**
    * Refreshes the columns that are worked out from other columns. Subclasses
    * add their own; this base version only stamps {@code LastUpdateTime}.
    *
    * @param markUpdated true when the record has just been changed, so its
    *                    update stamp (and the room timer built on it) restarts
    */
   public void updateFields(boolean markUpdated) {
      if (markUpdated) {
         this.setValue(COLUMN_LAST_UPDATE_TIME, currentTimestamp());
      }
   }

   /** Whole minutes since the last update, as text; "" when the stamp cannot be read. */
   public String getMinsSinceLastUpdate() {
      String lastUpdate = this.getLastUpdateTime();
      int mins = getMinsSinceTime(lastUpdate);
      return mins < 0 ? "" : "" + mins;
   }

   /** Whole minutes since {@code timestamp}, or -1 when it is not in {@link #TIME_FORMAT_PATTERN}. */
   private static int getMinsSinceTime(String timestamp) {
      try {
         Date then = newTimeFormat().parse(timestamp);
         Date now = new Date();
         long elapsedMillis = now.getTime() - then.getTime();
         return (int)(elapsedMillis / MILLIS_PER_MINUTE);
      } catch (Exception unparsable) {
         // Any unreadable stamp (blank, hand-edited) simply counts as "unknown".
         return -1;
      }
   }

   /**
    * Reads a column, never returning null: a missing column is "". Also answers
    * two computed pseudo-columns, {@code MinsSinceLastUpdate} and
    * {@code RegTimeHM}, so they can be served like stored ones.
    */
   public String getValue(String column) {
      if (column.equals(COLUMN_MINS_SINCE_LAST_UPDATE)) {
         return this.getMinsSinceLastUpdate();
      }

      if (column.equals(COLUMN_REG_TIME_HM)) {
         return this.getRegTimeHM();
      }

      String value = this.get(column);
      return value == null ? "" : value;
   }

   /** Writes a column; a null value is stored as "". */
   public void setValue(String column, String value) {
      if (value == null) {
         this.put(column, "");
      } else {
         this.put(column, value);
      }
   }

   /**
    * Hook for subclasses to fill in columns a file left blank. Does nothing
    * here; {@link ScoutRecord} overrides it.
    */
   protected void setDefaults() {
   }

   /**
    * Copies each of {@code columns} from {@code source} into this record,
    * skipping columns the source leaves empty so a partial update never blanks
    * what is already here.
    */
   public void updateFrom(DataRecord source, String[] columns) {
      for (String column : columns) {
         String value = source.get(column);
         if (value != null && value.length() > 0) {
            this.put(column, value);
         }
      }
   }

   public String getID() {
      return this.getValue(COLUMN_ID);
   }

   public String getType() {
      return this.getValue(COLUMN_TYPE);
   }

   /** The hour and minute the record was created, as {@code HH:mm}; "" if unreadable. */
   public String getRegTimeHM() {
      try {
         return this.getRegTime().substring(HOUR_MINUTE_START, HOUR_MINUTE_END);
      } catch (StringIndexOutOfBoundsException shortTimestamp) {
         return "";
      }
   }

   public String getRegTime() {
      return this.getValue(COLUMN_REG_TIME);
   }

   public String getLastUpdateTime() {
      return this.getValue(COLUMN_LAST_UPDATE_TIME);
   }

   /** The record as one CSV line (no trailing newline). */
   @Override
   public String toString() {
      StringBuffer out = new StringBuffer();
      this.toString(out);
      return out.toString();
   }

   public void toString(StringBuffer out) {
      this.toCSV(out, ',');
   }

   /** Whether {@code name} is one of this kind of record's columns. */
   public boolean isColumn(String name) {
      for (String column : this.recordColumns) {
         if (name.equals(column)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Fills this record from one line of a CSV file.
    *
    * @param line the text of the line
    * @param delimiter the character between fields
    * @param columns the file's header: the column each field belongs to, in order.
    *                Fields for columns this kind of record does not have are
    *                dropped, and a line with fewer fields than the header
    *                leaves the rest empty.
    */
   public void fromCSV(String line, char delimiter, String[] columns) {
      int fieldStart = 0;

      for (int columnIndex = 0; columnIndex < columns.length; columnIndex++) {
         int delimiterPos = line.indexOf(delimiter, fieldStart);
         if (delimiterPos < fieldStart) {
            String lastField = line.substring(fieldStart);
            if (this.isColumn(columns[columnIndex])) {
               this.put(columns[columnIndex], lastField);
            }
            break;
         }

         String field = line.substring(fieldStart, delimiterPos);
         if (this.isColumn(columns[columnIndex])) {
            this.put(columns[columnIndex], field);
         }

         fieldStart = delimiterPos + 1;
      }

      this.setDefaults();
      this.updateFields(false);
      this.postLoadUpdate();
   }

   public void toCSV(StringBuffer out, char delimiter) {
      this.toCSV(out, delimiter, this.recordColumns);
   }

   /**
    * Appends the values of {@code columns} separated by {@code delimiter}.
    * There is no quoting in this file format, so a delimiter inside a value is
    * written as {@code ~} and a line break as {@code +}.
    */
   public void toCSV(StringBuffer out, char delimiter, String[] columns) {
      boolean first = true;

      for (String column : columns) {
         if (!first) {
            out.append(delimiter);
         }

         first = false;
         String value = this.getValue(column);
         out.append(value.replace(delimiter, '~').replace('\n', '+').replace('\r', '+'));
      }
   }

   public void toJSON(StringBuffer out) {
      this.toJSON(out, this.recordColumns);
   }

   /**
    * Appends a JSON object of {@code columns}. A double quote or line break
    * inside a value is replaced ({@code ~} and {@code +}) instead of escaped.
    */
   public void toJSON(StringBuffer out, String[] columns) {
      boolean first = true;
      out.append("{");

      for (String column : columns) {
         if (!first) {
            out.append(",");
         }

         first = false;
         String value = this.getValue(column);
         value = value.replace('"', '~').replace('\n', '+').replace('\r', '+');

         out.append(column).append(": \"");
         out.append(value).append("\"");
      }

      out.append("}");
   }

   /**
    * Appends this record as an {@code <item>} element with one child element
    * per column, in the form the browser's data views read.
    *
    * @param extraColumns more columns to append after {@code columns}; may be null
    */
   public void toDataView(StringBuffer out, String[] columns, String[] extraColumns) {
      out.append("<item id=\"").append(this.getID()).append("\">");

      for (String column : columns) {
         this.appendElement(out, column);
      }

      if (extraColumns != null) {
         for (String extraColumn : extraColumns) {
            this.appendElement(out, extraColumn);
         }
      }

      out.append("</item>");
   }

   private void appendElement(StringBuffer out, String column) {
      out.append("<").append(column).append(">");
      escapeForXml(out, this.getValue(column));
      out.append("</").append(column).append(">");
   }

   /**
    * Appends this record in the layout named by {@code format}:
    * {@code rows} for {@link #toCells}, {@code data} for {@link #toDataView},
    * and anything else for a plain CSV line of {@code columns}.
    */
   public void toString(StringBuffer out, String[] columns, String[] extraColumns, String format) {
      if (format.equalsIgnoreCase("rows")) {
         this.toCells(out, columns, extraColumns);
      } else if (format.equalsIgnoreCase("data")) {
         this.toDataView(out, columns, extraColumns);
      } else {
         this.toCSV(out, ',', columns);
      }
   }

   /**
    * Appends this record as a {@code <row>} element: one {@code <cell>} per
    * column, then each extra column as a {@code <userdata>} element the table
    * keeps but does not show.
    *
    * @param extraColumns may be null
    */
   public void toCells(StringBuffer out, String[] columns, String[] extraColumns) {
      out.append("<row id=\"").append(this.getID()).append("\">");

      for (String column : columns) {
         out.append("<cell>");
         escapeForXml(out, this.getValue(column));
         out.append("</cell>");
      }

      if (extraColumns != null) {
         for (String extraColumn : extraColumns) {
            out.append("<userdata name=\"").append(extraColumn).append("\">");
            escapeForXml(out, this.getValue(extraColumn));
            out.append("</userdata>");
         }
      }

      out.append("</row>");
   }

   /** Appends {@code text} with the characters XML reserves replaced; a newline becomes a space. */
   private static void escapeForXml(StringBuffer out, String text) {
      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         switch (c) {
            case '\n':
               out.append(" ");
               break;
            case '"':
               out.append("&quot;");
               break;
            case '&':
               out.append("&amp;");
               break;
            case '\'':
               out.append("&apos;");
               break;
            case '<':
               out.append("&lt;");
               break;
            case '>':
               out.append("&gt;");
               break;
            default:
               out.append(c);
         }
      }
   }

   /**
    * Makes new, empty records of one kind and lists that kind's columns, so a
    * {@link DataRecordFile} can load any kind of record without knowing which.
    */
   public interface Factory {
      DataRecord newInstance();

      String[] getColumns();
   }
}
