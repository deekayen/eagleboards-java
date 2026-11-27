package shkc.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes a text body to a response with a content length that matches the bytes
 * sent.
 *
 * <p>The handlers once set the length to {@code String.length()}, which counts
 * characters, and then wrote the string's bytes. For plain ASCII the two agree.
 * With one accented letter (a surname such as Muñoz inside a refusal reason, a
 * room name, or a record) the body is longer in bytes than the declared length,
 * and Jetty 12 refuses the write with "too much content written". The request
 * then failed with a 500 and the operator saw the exception text, not the
 * reason. Encoding once, as UTF-8, and using that array's length for both avoids
 * it, and naming the charset keeps the browser from decoding the bytes as
 * ISO-8859-1.
 */
final class ResponseText {
   private ResponseText() {
   }

   /**
    * Sends {@code body} as UTF-8 with the given content type. A {@code text/}
    * type that names no charset gets {@code charset=UTF-8} added; any other type
    * is used as given.
    */
   static void write(HttpServletResponse response, String contentType, String body) throws IOException {
      byte[] bytes = String.valueOf(body).getBytes(StandardCharsets.UTF_8);
      response.setContentType(withCharset(contentType));
      response.setContentLength(bytes.length);
      response.getOutputStream().write(bytes);
   }

   static String withCharset(String contentType) {
      if (contentType != null && contentType.startsWith("text/") && !contentType.toLowerCase().contains("charset")) {
         return contentType + "; charset=UTF-8";
      }
      return contentType;
   }
}
