package shkc.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;

public class NameUtil {
   private static final int NORM = 1;
   private static final int WAIT_START = 2;
   private static final int WAIT_END = 3;
   private static SimpleDateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");

   public String resolveVars(String template) {
      String text = template;
      int pass = 0;

      for (int substitutions = 1; substitutions > 0 && pass < 5; pass++) {
         substitutions = 0;
         StringBuffer out = new StringBuffer();
         StringBuffer varName = new StringBuffer();
         byte state = NORM;

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (state) {
               case NORM:
                  if (c == '$') {
                     state = WAIT_START;
                  } else {
                     out.append(c);
                  }
                  break;
               case WAIT_START:
                  if (c == '{') {
                     varName = new StringBuffer();
                     state = WAIT_END;
                  } else {
                     out.append("$").append(c);
                     state = NORM;
                  }
                  break;
               case WAIT_END:
                  if (c == '}') {
                     substitutions++;
                     this.processVar(out, varName.toString());
                     state = NORM;
                  } else {
                     varName.append(c);
                  }
            }
         }

         text = out.toString();
      }

      return text;
   }

   private void processVar(StringBuffer out, String expression) {
      if (expression.toLowerCase().equals("date")) {
         this.processDate(out, new Date(), DEFAULT_DATE_FORMAT);
      } else if (expression.toLowerCase().startsWith("convertdate(")) {
         this.processConvertDate(out, expression, DEFAULT_DATE_FORMAT);
      } else if (expression.toLowerCase().startsWith("numeric(")) {
         this.processNumeric(out, expression);
      } else if (expression.toLowerCase().startsWith("date:")) {
         String pattern = expression.substring(5);
         SimpleDateFormat format = new SimpleDateFormat(pattern);
         this.processDate(out, new Date(), format);
      } else if (expression.toLowerCase().startsWith("date(")) {
         int openParen = expression.indexOf(40);
         int closeParen = expression.indexOf(41);
         // ${date(name)} once looked "name" up in a caller-supplied property
         // map and formatted that value when it held a Date. Nothing ever
         // populated the map -- every setter that could have was dead code --
         // so the lookup always missed and the value was always "now". The
         // named property is accepted and ignored; only an optional
         // ":pattern" after the parentheses changes the output.
         if (closeParen > openParen) {
            int colonPos = expression.indexOf(58);
            if (colonPos > closeParen) {
               String datePattern = expression.substring(colonPos + 1);
               this.processDate(out, new Date(), new SimpleDateFormat(datePattern));
            } else {
               this.processDate(out, new Date(), DEFAULT_DATE_FORMAT);
            }
         }
      } else {
         String defaultValue = "";
         int equalsPos = expression.indexOf("=");
         if (equalsPos > 0) {
            defaultValue = expression.substring(equalsPos + 1);
            expression = expression.substring(0, equalsPos);
         }

         // Same story: the property-map lookup that used to come first could
         // never hit, so a name resolves from the system properties or falls
         // back to whatever followed the "=" in the expression.
         out.append(System.getProperty(expression, defaultValue));
      }
   }

   private void processDate(StringBuffer out, Date date, SimpleDateFormat format) {
      out.append(format.format(date));
   }

   private void processNumeric(StringBuffer out, String expression) {
      int openParen = expression.indexOf(40);
      int closeParen = expression.indexOf(41);
      if (openParen > 0 && closeParen > openParen) {
         String text = expression.substring(openParen + 1, closeParen);

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
               out.append(c);
            }
         }
      } else {
         out.append("bad format: numeric(string)");
      }
   }

   private void processConvertDate(StringBuffer out, String expression, SimpleDateFormat defaultFormat) {
      int openParen = expression.indexOf(40);
      int closeParen = expression.indexOf(41);
      if (openParen > 0 && closeParen > openParen) {
         String args = expression.substring(openParen + 1, closeParen);
         StringTokenizer tokens = new StringTokenizer(args, ",", false);
         if (tokens.countTokens() == 0) {
            out.append("bad format: convertDate(date,from-fmt[,to-fmt])");
         } else if (tokens.countTokens() >= 3) {
            String dateText = tokens.nextToken();
            String fromPattern = tokens.nextToken();
            SimpleDateFormat fromFormat = new SimpleDateFormat(fromPattern);
            SimpleDateFormat toFormat = defaultFormat;
            if (tokens.hasMoreTokens()) {
               String toPattern = tokens.nextToken();
               toFormat = new SimpleDateFormat(toPattern);
            }

            try {
               Date parsed = fromFormat.parse(dateText);
               String formatted = toFormat.format(parsed);
               out.append(formatted);
            } catch (Exception failure) {
               out.append(failure.toString());
            }
         }
      } else {
         out.append("bad format: convertDate(date,from-fmt, to-fmt)");
      }
   }

}
