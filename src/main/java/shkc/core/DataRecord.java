package shkc.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class DataRecord extends HashMap<String, String> {
   public static final String TYPE = "Type";
   public static final String ID = "ID";
   public static final String REG_TIME = "RegTime";
   public static final String LAST_UPDATE_TIME = "LastUpdateTime";
   public static final String T_MINS_SINCE_LAST_UPDATE = "MinsSinceLastUpdate";
   public static final String T_REG_TIME_HM = "RegTimeHM";
   private String[] _columns;
   private static SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH:mmZ");

   public DataRecord(String var1, String[] var2, Map var3) {
      this._columns = var2;
      this.put("Type", var1);
      if (var3 != null) {
         for (Object var6Obj : var3.keySet()) {
            String var6 = (String)var6Obj;
            Object var7 = var3.get(var6);
            if (var7 instanceof String[]) {
               try {
                  this.put(var6, ((String[])var7)[0]);
               } catch (Exception var10) {
               }
            } else if (var7 instanceof String) {
               try {
                  this.put(var6, (String)var7);
               } catch (Exception var9) {
               }
            }
         }
      }

      this.put("LastUpdateTime", TIME_FORMAT.format(new Date()));
      this.put("RegTime", TIME_FORMAT.format(new Date()));

      for (String var14 : this._columns) {
         if (this.get(var14) == null) {
            this.put(var14, "");
         }
      }
   }

   public void postLoadUpdate() {
      int var1 = getMinsSinceTime(this.getValue("LastUpdateTime"));
      if (var1 < 0 || var1 > 1440) {
         this.setValue("LastUpdateTime", TIME_FORMAT.format(new Date()));
      }

      var1 = getMinsSinceTime(this.getValue("RegTime"));
      if (var1 < 0 || var1 > 1440) {
         this.setValue("RegTime", TIME_FORMAT.format(new Date()));
      }
   }

   public void updateFields(boolean var1) {
      if (var1) {
         String var2 = TIME_FORMAT.format(new Date());
         this.setValue("LastUpdateTime", var2);
      }
   }

   public String getMinsSinceLastUpdate() {
      String var1 = this.getLastUpdateTime();
      int var2 = getMinsSinceTime(var1);
      return var2 < 0 ? "" : "" + var2;
   }

   private static int getMinsSinceTime(String var0) {
      try {
         Date var1 = TIME_FORMAT.parse(var0);
         Date var2 = new Date();
         long var3 = var2.getTime() - var1.getTime();
         return (int)(var3 / 60000L);
      } catch (Exception var6) {
         return -1;
      }
   }

   public String getValue(String var1) {
      if (var1.equals("MinsSinceLastUpdate")) {
         return this.getMinsSinceLastUpdate();
      }

      if (var1.equals("RegTimeHM")) {
         return this.getRegTimeHM();
      }

      String var2 = this.get(var1);
      return var2 == null ? "" : var2;
   }

   public void setValue(String var1, String var2) {
      if (var2 == null) {
         this.put(var1, "");
      } else {
         this.put(var1, var2);
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
      } catch (Exception var2) {
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
      StringBuffer var1 = new StringBuffer();
      this.toString(var1);
      return var1.toString();
   }

   public void toString(StringBuffer var1) {
      this.toCSV(var1, ',');
   }

   public void fromCSV(String var1, char var2) {
      this.fromCSV(var1, var2, this._columns);
   }

   public boolean isColumn(String var1) {
      for (String var5 : this._columns) {
         if (var1.equals(var5)) {
            return true;
         }
      }

      return false;
   }

   public void fromCSV(String var1, char var2, String[] var3) {
      int var4 = 0;

      for (int var5 = 0; var5 < var3.length; var5++) {
         int var6 = var1.indexOf(var2, var4);
         if (var6 < var4) {
            String var8 = var1.substring(var4);
            if (this.isColumn(var3[var5])) {
               this.put(var3[var5], var8);
            }
            break;
         }

         String var7 = var1.substring(var4, var6);
         if (this.isColumn(var3[var5])) {
            this.put(var3[var5], var7);
         }

         var4 = var6 + 1;
      }

      this.setDefaults();
      this.updateFields(false);
      this.postLoadUpdate();
   }

   public void toCSV(StringBuffer var1, char var2) {
      this.toCSV(var1, var2, this._columns);
   }

   public void toCSV(StringBuffer var1, char var2, String[] var3) {
      boolean var4 = true;

      for (String var8 : var3) {
         if (!var4) {
            var1.append(var2);
         }

         var4 = false;
         String var9 = this.getValue(var8);
         if (var9 == null) {
            var9 = "";
         } else {
            var9 = var9.replace(var2, '~').replace('\n', '+').replace('\r', '+');
         }

         var1.append(var9);
      }
   }

   public void toJSON(StringBuffer var1) {
      this.toJSON(var1, this._columns);
   }

   public void toJSON(StringBuffer var1, String[] var2) {
      boolean var3 = true;
      var1.append("{");

      for (String var7 : var2) {
         if (!var3) {
            var1.append(",");
         }

         var3 = false;
         String var8 = this.getValue(var7);
         if (var8 == null) {
            var8 = "";
         } else {
            var8 = var8.replace('"', '~').replace('\n', '+').replace('\r', '+');
         }

         var1.append(var7).append(": \"");
         var1.append(var8).append("\"");
      }

      var1.append("}");
   }

   public void toCells(StringBuffer var1) {
      this.toCells(var1, this._columns, null);
   }

   public void toDataView(StringBuffer var1, String[] var2, String[] var3) {
      boolean var4 = true;
      var1.append("<item id=\"").append(this.getID()).append("\">");

      for (String var8 : var2) {
         var1.append("<").append(var8).append(">");
         String var9 = this.getValue(var8);
         if (var9 == null) {
            var9 = "";
         }

         htmlify(var1, var9);
         var1.append("</").append(var8).append(">");
      }

      if (var3 != null) {
         for (String var13 : var3) {
            var1.append("<").append(var13).append(">");
            String var14 = this.getValue(var13);
            if (var14 == null) {
               var14 = "";
            }

            htmlify(var1, var14);
            var1.append("</").append(var13).append(">");
         }
      }

      var1.append("</item>");
   }

   public void toString(StringBuffer var1, String[] var2, String[] var3, String var4) {
      if (var4.equalsIgnoreCase("rows")) {
         this.toCells(var1, var2, var3);
      } else if (var4.equalsIgnoreCase("data")) {
         this.toDataView(var1, var2, var3);
      } else {
         this.toCSV(var1, ',', var2);
      }
   }

   public void toDataView(StringBuffer var1) {
      this.toCells(var1, this._columns, null);
   }

   public void toCells(StringBuffer var1, String[] var2, String[] var3) {
      boolean var4 = true;
      var1.append("<row id=\"").append(this.getID()).append("\">");

      for (String var8 : var2) {
         var1.append("<cell>");
         String var9 = this.getValue(var8);
         if (var9 == null) {
            var9 = "";
         }

         htmlify(var1, var9);
         var1.append("</cell>");
      }

      if (var3 != null) {
         for (String var13 : var3) {
            var1.append("<userdata name=\"").append(var13).append("\">");
            String var14 = this.getValue(var13);
            if (var14 == null) {
               var14 = "";
            }

            htmlify(var1, var14);
            var1.append("</userdata>");
         }
      }

      var1.append("</row>");
   }

   private static void htmlify(StringBuffer var0, String var1) {
      for (int var2 = 0; var2 < var1.length(); var2++) {
         char var3 = var1.charAt(var2);
         switch (var3) {
            case '\n':
               var0.append(" ");
               break;
            case '"':
               var0.append("&quot;");
               break;
            case '&':
               var0.append("&amp;");
               break;
            case '\'':
               var0.append("&apos;");
               break;
            case '<':
               var0.append("&lt;");
               break;
            case '>':
               var0.append("&gt;");
               break;
            default:
               var0.append(var3);
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
