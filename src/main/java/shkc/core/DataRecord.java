package shkc.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class DataRecord extends HashMap<String, String> {
   private String[] _columns;
   private static SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH:mmZ");

   public DataRecord(String recordType, String[] columns, Map values) {
      this._columns = columns;
      this.put("Type", recordType);
      if (values != null) {
         for (Object keyObj : values.keySet()) {
            String key = (String)keyObj;
            Object value = values.get(key);
            if (value instanceof String[]) {
               try {
                  this.put(key, ((String[])value)[0]);
               } catch (Exception ignored) {
               }
            } else if (value instanceof String) {
               try {
                  this.put(key, (String)value);
               } catch (Exception ignored) {
               }
            }
         }
      }

      this.put("LastUpdateTime", TIME_FORMAT.format(new Date()));
      this.put("RegTime", TIME_FORMAT.format(new Date()));

      for (String column : this._columns) {
         if (this.get(column) == null) {
            this.put(column, "");
         }
      }
   }

   public void postLoadUpdate() {
      int minsSince = getMinsSinceTime(this.getValue("LastUpdateTime"));
      if (minsSince < 0 || minsSince > 1440) {
         this.setValue("LastUpdateTime", TIME_FORMAT.format(new Date()));
      }

      minsSince = getMinsSinceTime(this.getValue("RegTime"));
      if (minsSince < 0 || minsSince > 1440) {
         this.setValue("RegTime", TIME_FORMAT.format(new Date()));
      }
   }

   public void updateFields(boolean markUpdated) {
      if (markUpdated) {
         String now = TIME_FORMAT.format(new Date());
         this.setValue("LastUpdateTime", now);
      }
   }

   public String getMinsSinceLastUpdate() {
      String lastUpdate = this.getLastUpdateTime();
      int mins = getMinsSinceTime(lastUpdate);
      return mins < 0 ? "" : "" + mins;
   }

   private static int getMinsSinceTime(String timestamp) {
      try {
         Date then = TIME_FORMAT.parse(timestamp);
         Date now = new Date();
         long elapsedMillis = now.getTime() - then.getTime();
         return (int)(elapsedMillis / 60000L);
      } catch (Exception unparsable) {
         return -1;
      }
   }

   public String getValue(String column) {
      if (column.equals("MinsSinceLastUpdate")) {
         return this.getMinsSinceLastUpdate();
      }

      if (column.equals("RegTimeHM")) {
         return this.getRegTimeHM();
      }

      String value = this.get(column);
      return value == null ? "" : value;
   }

   public void setValue(String column, String value) {
      if (value == null) {
         this.put(column, "");
      } else {
         this.put(column, value);
      }
   }

   protected void setDefaults() {
   }

   public String getID() {
      return this.getValue("ID");
   }

   public String getType() {
      return this.getValue("Type");
   }

   public String getRegTimeHM() {
      try {
         return this.getRegTime().substring(11, 16);
      } catch (Exception ignored) {
         return "";
      }
   }

   public String getRegTime() {
      return this.getValue("RegTime");
   }

   public String getLastUpdateTime() {
      return this.getValue("LastUpdateTime");
   }

   @Override
   public String toString() {
      StringBuffer out = new StringBuffer();
      this.toString(out);
      return out.toString();
   }

   public void toString(StringBuffer out) {
      this.toCSV(out, ',');
   }

   public boolean isColumn(String name) {
      for (String column : this._columns) {
         if (name.equals(column)) {
            return true;
         }
      }

      return false;
   }

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
      this.toCSV(out, delimiter, this._columns);
   }

   public void toCSV(StringBuffer out, char delimiter, String[] columns) {
      boolean first = true;

      for (String column : columns) {
         if (!first) {
            out.append(delimiter);
         }

         first = false;
         String value = this.getValue(column);
         if (value == null) {
            value = "";
         } else {
            value = value.replace(delimiter, '~').replace('\n', '+').replace('\r', '+');
         }

         out.append(value);
      }
   }

   public void toJSON(StringBuffer out) {
      this.toJSON(out, this._columns);
   }

   public void toJSON(StringBuffer out, String[] columns) {
      boolean first = true;
      out.append("{");

      for (String column : columns) {
         if (!first) {
            out.append(",");
         }

         first = false;
         String value = this.getValue(column);
         if (value == null) {
            value = "";
         } else {
            value = value.replace('"', '~').replace('\n', '+').replace('\r', '+');
         }

         out.append(column).append(": \"");
         out.append(value).append("\"");
      }

      out.append("}");
   }

   public void toDataView(StringBuffer out, String[] columns, String[] extraColumns) {
      out.append("<item id=\"").append(this.getID()).append("\">");

      for (String column : columns) {
         out.append("<").append(column).append(">");
         String value = this.getValue(column);
         if (value == null) {
            value = "";
         }

         htmlify(out, value);
         out.append("</").append(column).append(">");
      }

      if (extraColumns != null) {
         for (String extraColumn : extraColumns) {
            out.append("<").append(extraColumn).append(">");
            String extraValue = this.getValue(extraColumn);
            if (extraValue == null) {
               extraValue = "";
            }

            htmlify(out, extraValue);
            out.append("</").append(extraColumn).append(">");
         }
      }

      out.append("</item>");
   }

   public void toString(StringBuffer out, String[] columns, String[] extraColumns, String format) {
      if (format.equalsIgnoreCase("rows")) {
         this.toCells(out, columns, extraColumns);
      } else if (format.equalsIgnoreCase("data")) {
         this.toDataView(out, columns, extraColumns);
      } else {
         this.toCSV(out, ',', columns);
      }
   }

   public void toCells(StringBuffer out, String[] columns, String[] extraColumns) {
      out.append("<row id=\"").append(this.getID()).append("\">");

      for (String column : columns) {
         out.append("<cell>");
         String value = this.getValue(column);
         if (value == null) {
            value = "";
         }

         htmlify(out, value);
         out.append("</cell>");
      }

      if (extraColumns != null) {
         for (String extraColumn : extraColumns) {
            out.append("<userdata name=\"").append(extraColumn).append("\">");
            String extraValue = this.getValue(extraColumn);
            if (extraValue == null) {
               extraValue = "";
            }

            htmlify(out, extraValue);
            out.append("</userdata>");
         }
      }

      out.append("</row>");
   }

   private static void htmlify(StringBuffer out, String text) {
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

   static {
      TIME_FORMAT.setTimeZone(TimeZone.getDefault());
   }

   public interface Factory {
      DataRecord newInstance();

      String[] getColumns();
   }
}
