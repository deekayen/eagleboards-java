package shkc.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

public class NameUtil {
   private static final int NORM = 1;
   private static final int WAIT_START = 2;
   private static final int WAIT_END = 3;
   private static SimpleDateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");
   private Map _props = new Hashtable();

   public NameUtil(Map var1) {
      this.addAll(var1);
   }

   public NameUtil() {
   }

   public void addAll(Map var1) {
      for (Object var3Obj : var1.keySet()) {
         String var3 = (String)var3Obj;
         Object var4 = var1.get(var3);
         this._props.put(var3.toLowerCase(), var4);
      }
   }

   public String resolveVars(String var1) {
      String var2 = var1;
      int var3 = 0;

      for (int var4 = 1; var4 > 0 && var3 < 5; var3++) {
         var4 = 0;
         StringBuffer var5 = new StringBuffer();
         StringBuffer var6 = new StringBuffer();
         byte var7 = 1;

         for (int var8 = 0; var8 < var2.length(); var8++) {
            char var9 = var2.charAt(var8);
            switch (var7) {
               case 1:
                  if (var9 == '$') {
                     var7 = 2;
                  } else {
                     var5.append(var9);
                  }
                  break;
               case 2:
                  if (var9 == '{') {
                     var6 = new StringBuffer();
                     var7 = 3;
                  } else {
                     var5.append("$").append(var9);
                     var7 = 1;
                  }
                  break;
               case 3:
                  if (var9 == '}') {
                     var4++;
                     this.processVar(var5, var6.toString());
                     var7 = 1;
                  } else {
                     var6.append(var9);
                  }
            }
         }

         var2 = var5.toString();
      }

      return var2;
   }

   private void processVar(StringBuffer var1, String var2) {
      if (var2.toLowerCase().equals("date")) {
         this.processDate(var1, new Date(), DEFAULT_DATE_FORMAT);
      } else if (var2.toLowerCase().startsWith("convertdate(")) {
         this.processConvertDate(var1, var2, DEFAULT_DATE_FORMAT);
      } else if (var2.toLowerCase().startsWith("numeric(")) {
         this.processNumeric(var1, var2);
      } else if (var2.toLowerCase().startsWith("date:")) {
         String var3 = var2.substring(5);
         SimpleDateFormat var4 = new SimpleDateFormat(var3);
         this.processDate(var1, new Date(), var4);
      } else if (var2.toLowerCase().startsWith("date(")) {
         int var11 = var2.indexOf(40);
         int var13 = var2.indexOf(41);
         if (var13 > var11) {
            String var5 = var2.substring(var11 + 1, var13);
            Object var6 = this._props.get(var5.toLowerCase());
            if (var6 == null) {
               var6 = new Date();
            }

            if (var6 instanceof Date) {
               Date var7 = (Date)var6;
               int var8 = var2.indexOf(58);
               if (var8 > var13) {
                  String var9 = var2.substring(var8 + 1);
                  SimpleDateFormat var10 = new SimpleDateFormat(var9);
                  this.processDate(var1, var7, var10);
               } else {
                  this.processDate(var1, var7, DEFAULT_DATE_FORMAT);
               }
            } else {
               var1.append(var6.toString());
            }
         }
      } else {
         String var12 = "";
         int var14 = var2.indexOf("=");
         if (var14 > 0) {
            var12 = var2.substring(var14 + 1);
            var2 = var2.substring(0, var14);
         }

         Object var15 = this._props.get(var2.toLowerCase());
         if (var15 == null) {
            var15 = System.getProperty(var2, var12);
         }

         var1.append(var15);
      }
   }

   private void processDate(StringBuffer var1, Date var2, SimpleDateFormat var3) {
      var1.append(var3.format(var2));
   }

   private void processNumeric(StringBuffer var1, String var2) {
      int var3 = var2.indexOf(40);
      int var4 = var2.indexOf(41);
      if (var3 > 0 && var4 > var3) {
         String var5 = var2.substring(var3 + 1, var4);

         for (int var6 = 0; var6 < var5.length(); var6++) {
            char var7 = var5.charAt(var6);
            if (Character.isDigit(var7)) {
               var1.append(var7);
            }
         }
      } else {
         var1.append("bad format: numeric(string)");
      }
   }

   private void processConvertDate(StringBuffer var1, String var2, SimpleDateFormat var3) {
      int var4 = var2.indexOf(40);
      int var5 = var2.indexOf(41);
      if (var4 > 0 && var5 > var4) {
         String var6 = var2.substring(var4 + 1, var5);
         StringTokenizer var7 = new StringTokenizer(var6, ",", false);
         if (var7.countTokens() == 0) {
            var1.append("bad format: convertDate(date,from-fmt[,to-fmt])");
         } else if (var7.countTokens() >= 3) {
            String var8 = var7.nextToken();
            String var9 = var7.nextToken();
            SimpleDateFormat var10 = new SimpleDateFormat(var9);
            SimpleDateFormat var11 = var3;
            if (var7.hasMoreTokens()) {
               String var12 = var7.nextToken();
               var11 = new SimpleDateFormat(var12);
            }

            try {
               Date var15 = var10.parse(var8);
               String var13 = var11.format(var15);
               var1.append(var13);
            } catch (Exception var14) {
               var1.append(var14.toString());
            }
         }
      } else {
         var1.append("bad format: convertDate(date,from-fmt, to-fmt)");
      }
   }

   public void setVariable(String var1, String var2) {
      if (var1 != null && var2 != null) {
         this._props.put(var1.toLowerCase(), var2);
      }
   }

   public void setVariable(String var1, Date var2) {
      if (var1 != null && var2 != null) {
         this._props.put(var1.toLowerCase(), var2);
      }
   }

   public void unsetVariable(String var1) {
      if (var1 != null) {
         this._props.remove(var1.toLowerCase());
      }
   }

   public String getVariable(String var1) {
      Object var2 = this._props.get(var1.toLowerCase());
      if (var2 == null) {
         return null;
      } else {
         return var2 instanceof String ? (String)var2 : var2.toString();
      }
   }

   public Iterator getVariableNames() {
      return this._props.keySet().iterator();
   }
}
