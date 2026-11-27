package shkc.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;

/**
 * Expands {@code ${...}} placeholders in a file path or location, so a
 * search path such as {@code ${user.home}/data/${date}} can be written once.
 *
 * <p>The forms understood inside the braces are:
 * <ul>
 *   <li>{@code date} - now, as {@code yyyyMMdd_HHmmss}</li>
 *   <li>{@code date:pattern} - now, in the given {@link SimpleDateFormat} pattern</li>
 *   <li>{@code date(name)} or {@code date(name):pattern} - the same; the name is ignored</li>
 *   <li>{@code convertDate(text,fromPattern,toPattern)} - re-formats a date</li>
 *   <li>{@code numeric(text)} - only the digits of {@code text}</li>
 *   <li>anything else - the system property of that name, or the text after
 *       an {@code =} when there is no such property, as in
 *       {@code ${user.name=nobody}}</li>
 * </ul>
 */
public class NameUtil {
   /** Where the scanner is inside a template while it reads it one character at a time. */
   private enum ScanState {
      /** Ordinary text. */
      NORMAL,
      /** Just read a dollar sign; an opening brace next starts a placeholder. */
      AFTER_DOLLAR,
      /** Inside {@code ${...}}, collecting the placeholder's text. */
      IN_PLACEHOLDER
   }

   /**
    * A placeholder's result can itself contain placeholders, so the text is
    * scanned again until nothing is left to expand, but never more than this
    * many times (which also stops a placeholder that expands to itself).
    */
   private static final int MAX_PASSES = 5;

   private static final String DEFAULT_DATE_PATTERN = "yyyyMMdd_HHmmss";

   /**
    * A fresh formatter for every use: {@link SimpleDateFormat} is not safe to
    * share between threads.
    */
   private static SimpleDateFormat newDefaultDateFormat() {
      return new SimpleDateFormat(DEFAULT_DATE_PATTERN);
   }

   /** Returns {@code template} with every {@code ${...}} placeholder replaced. */
   public String resolveVars(String template) {
      String text = template;
      int pass = 0;

      for (int substitutions = 1; substitutions > 0 && pass < MAX_PASSES; pass++) {
         substitutions = 0;
         StringBuilder out = new StringBuilder();
         StringBuilder placeholder = new StringBuilder();
         ScanState state = ScanState.NORMAL;

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (state) {
               case NORMAL:
                  if (c == '$') {
                     state = ScanState.AFTER_DOLLAR;
                  } else {
                     out.append(c);
                  }
                  break;
               case AFTER_DOLLAR:
                  if (c == '{') {
                     placeholder = new StringBuilder();
                     state = ScanState.IN_PLACEHOLDER;
                  } else {
                     out.append("$").append(c);
                     state = ScanState.NORMAL;
                  }
                  break;
               case IN_PLACEHOLDER:
                  if (c == '}') {
                     substitutions++;
                     this.expandPlaceholder(out, placeholder.toString());
                     state = ScanState.NORMAL;
                  } else {
                     placeholder.append(c);
                  }
                  break;
            }
         }

         // Text the scanner was still holding when the template ended is dropped.
         if (state == ScanState.IN_PLACEHOLDER) {
            Log.info("path template " + Log.clean(template) + ": a ${ is never closed, so the text after it (" + placeholder.length()
               + " chars) is dropped");
         } else if (state == ScanState.AFTER_DOLLAR) {
            Log.info("path template " + Log.clean(template) + ": a trailing $ is dropped");
         }

         text = out.toString();
      }

      if (text.contains("${")) {
         Log.info("path template " + Log.clean(template) + " still holds a ${...} placeholder after " + MAX_PASSES
            + " passes (a placeholder that expands to itself?): " + Log.clean(text));
      }

      return text;
   }

   /** Appends the value of one placeholder; {@code expression} is the text between the braces. */
   private void expandPlaceholder(StringBuilder out, String expression) {
      String lowerCase = expression.toLowerCase();
      if (lowerCase.equals("date")) {
         out.append(newDefaultDateFormat().format(new Date()));
      } else if (lowerCase.startsWith("convertdate(")) {
         this.appendConvertedDate(out, expression);
      } else if (lowerCase.startsWith("numeric(")) {
         this.appendDigitsOnly(out, expression);
      } else if (lowerCase.startsWith("date:")) {
         String pattern = expression.substring("date:".length());
         out.append(new SimpleDateFormat(pattern).format(new Date()));
      } else if (lowerCase.startsWith("date(")) {
         int openParen = expression.indexOf('(');
         int closeParen = expression.indexOf(')');
         // ${date(name)} once looked "name" up in a caller-supplied property
         // map and formatted that value when it held a Date. Nothing ever
         // populated the map (every setter that could have was dead code),
         // so the lookup always missed and the value was always "now". The
         // named property is accepted and ignored; only an optional
         // ":pattern" after the parentheses changes the output.
         if (closeParen > openParen) {
            int colonPos = expression.indexOf(':');
            if (colonPos > closeParen) {
               String datePattern = expression.substring(colonPos + 1);
               out.append(new SimpleDateFormat(datePattern).format(new Date()));
            } else {
               out.append(newDefaultDateFormat().format(new Date()));
            }
         } else {
            Log.info("path placeholder ${" + Log.clean(expression) + "} has no closing parenthesis, so it expands to nothing");
         }
      } else {
         String propertyName = expression;
         String defaultValue = "";
         int equalsPos = expression.indexOf('=');
         if (equalsPos > 0) {
            defaultValue = expression.substring(equalsPos + 1);
            propertyName = expression.substring(0, equalsPos);
         }

         // Same story: the property-map lookup that used to come first could
         // never hit, so a name resolves from the system properties or falls
         // back to whatever followed the "=" in the expression.
         String resolved = System.getProperty(propertyName, defaultValue);
         if (resolved.isEmpty()) {
            Log.info("path placeholder ${" + Log.clean(expression) + "} is not a system property and has no default, so it expands to nothing");
         }
         out.append(resolved);
      }
   }

   /** Handles {@code numeric(text)}: appends only the digits found in {@code text}. */
   private void appendDigitsOnly(StringBuilder out, String expression) {
      int openParen = expression.indexOf('(');
      int closeParen = expression.indexOf(')');
      if (openParen > 0 && closeParen > openParen) {
         String text = expression.substring(openParen + 1, closeParen);

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
               out.append(c);
            }
         }
      } else {
         Log.info("path placeholder ${" + Log.clean(expression) + "} is not numeric(text), so it expands to the words \"bad format\"");
         out.append("bad format: numeric(string)");
      }
   }

   /**
    * Handles {@code convertDate(text,fromPattern,toPattern)}: parses {@code text}
    * with {@code fromPattern} and appends it formatted with {@code toPattern}.
    * A parse failure appends the exception's text instead.
    */
   private void appendConvertedDate(StringBuilder out, String expression) {
      int openParen = expression.indexOf('(');
      int closeParen = expression.indexOf(')');
      if (openParen > 0 && closeParen > openParen) {
         String args = expression.substring(openParen + 1, closeParen);
         StringTokenizer tokens = new StringTokenizer(args, ",", false);
         if (tokens.countTokens() == 0) {
            Log.info("path placeholder ${" + Log.clean(expression) + "} has no arguments, so it expands to the words \"bad format\"");
            out.append("bad format: convertDate(date,from-fmt[,to-fmt])");
         } else if (tokens.countTokens() < 3) {
            Log.info("path placeholder ${" + Log.clean(expression) + "} has fewer than three arguments, so it expands to nothing");
         } else if (tokens.countTokens() >= 3) {
            // Only three or more arguments are converted; one or two append
            // nothing at all, although the message above says the target
            // format is optional.
            String dateText = tokens.nextToken();
            String fromPattern = tokens.nextToken();
            SimpleDateFormat fromFormat = new SimpleDateFormat(fromPattern);
            SimpleDateFormat toFormat = newDefaultDateFormat();
            if (tokens.hasMoreTokens()) {
               String toPattern = tokens.nextToken();
               toFormat = new SimpleDateFormat(toPattern);
            }

            try {
               Date parsed = fromFormat.parse(dateText);
               String formatted = toFormat.format(parsed);
               out.append(formatted);
            } catch (Exception failure) {
               Log.warn("path placeholder ${" + Log.clean(expression) + "} could not convert the date, so the failure text is used in the path", failure);
               out.append(failure.toString());
            }
         }
      } else {
         Log.info("path placeholder ${" + Log.clean(expression) + "} is not convertDate(...), so it expands to the words \"bad format\"");
         out.append("bad format: convertDate(date,from-fmt, to-fmt)");
      }
   }

}
