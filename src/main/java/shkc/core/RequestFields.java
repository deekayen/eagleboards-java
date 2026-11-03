package shkc.core;

import java.util.StringTokenizer;

/** Reads the list of column names a page sends in a {@code cols} or {@code data} request parameter. */
public final class RequestFields {
   private RequestFields() {
   }

   /**
    * Splits a list of column names such as {@code "First,Last"} (commas, plus
    * signs, spaces and square brackets all separate names).
    *
    * @param csvList  the request parameter, which may be null or empty
    * @param defaults what to return when {@code csvList} is null or empty
    * @return the column names, or {@code defaults} itself when none were given
    */
   public static String[] columns(String csvList, String[] defaults) {
      if (csvList != null && csvList.length() != 0) {
         StringTokenizer tokens = new StringTokenizer(csvList, ",+ []", false);
         String[] fields = new String[tokens.countTokens()];

         for (int i = 0; i < fields.length; i++) {
            fields[i] = tokens.nextToken();
         }

         return fields;
      } else {
         return defaults;
      }
   }
}
