package shkc.core;

/**
 * Escaping for text placed inside the XML the server answers with.
 *
 * <p>There are two methods because two responses have always escaped
 * differently, and the bytes they send must not change: the update handler's
 * refusal reason is element text, which never escaped the double quote, while
 * the approved-proposals rows are written as attribute values and text and
 * escape it.
 */
public final class XmlText {
   private XmlText() {
   }

   /**
    * Escapes {@code &}, {@code <} and {@code >}, which is enough for text between tags.
    *
    * @return the escaped text; an empty string when {@code text} is null
    */
   public static String escapeText(String text) {
      if (text == null) {
         return "";
      }
      return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
   }

   /**
    * Escapes {@code &}, {@code <}, {@code >} and the double quote, so the result
    * is safe inside a double-quoted attribute value.
    *
    * @return the escaped text; an empty string when {@code text} is null
    */
   public static String escapeAttribute(String text) {
      return escapeText(text).replace("\"", "&quot;");
   }
}
