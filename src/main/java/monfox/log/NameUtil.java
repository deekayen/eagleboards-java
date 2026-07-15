package monfox.log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

class NameUtil {
   private static final int NORM = 1;
   private static final int WAIT_START = 2;
   private static final int WAIT_END = 3;
   private static SimpleDateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
   private Properties _props = new Properties();

   public NameUtil() {
   }

   public String resolveVars(String var1) {
      StringBuffer var2 = new StringBuffer();
      StringBuffer var3 = new StringBuffer();
      byte var4 = 1;

      for (int var5 = 0; var5 < var1.length(); var5++) {
         char var6 = var1.charAt(var5);
         switch (var4) {
            case 1:
               if (var6 == '$') {
                  var4 = 2;
               } else {
                  var2.append(var6);
               }
               break;
            case 2:
               if (var6 == '{') {
                  var3 = new StringBuffer();
                  var4 = 3;
               } else {
                  var2.append("$").append(var6);
                  var4 = 1;
               }
               break;
            case 3:
               if (var6 == '}') {
                  this.processVar(var2, var3.toString());
                  var4 = 1;
               } else {
                  var3.append(var6);
               }
         }
      }

      return var2.toString();
   }

   private void processVar(StringBuffer var1, String var2) {
      if (var2.equals("date")) {
         this.processDate(var1, DEFAULT_DATE_FORMAT);
      } else if (var2.startsWith("date:")) {
         String var3 = var2.substring(5);
         SimpleDateFormat var4 = new SimpleDateFormat(var3);
         this.processDate(var1, var4);
      } else {
         String var5 = this._props.getProperty(var2);
         if (var5 == null) {
            var5 = System.getProperty(var2, "");
         }

         var1.append(var5);
      }
   }

   private void processDate(StringBuffer var1, SimpleDateFormat var2) {
      var1.append(var2.format(new Date()));
   }

   public void setProperty(String var1, String var2) {
      if (var1 != null && var2 != null) {
         this._props.setProperty(var1, var2);
      }
   }
}
