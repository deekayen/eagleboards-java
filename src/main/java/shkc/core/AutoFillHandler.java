package shkc.core;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Looks one record up by a field's value, so a form can fill itself in. It
 * serves {@code /adult-autofill}, {@code /youth-autofill} and
 * {@code /config-autofill}.
 *
 * <p>Request parameters: {@code op} ({@code list} asks for the list of values),
 * {@code fmt} ({@code json}, otherwise XML) and one parameter named after the
 * lookup field (for example {@code Email}) holding the value to find.
 *
 * <p>Answers one of two ways:
 * <ul>
 * <li>A list ({@code op=list}, or no lookup value given): a JSON-like
 *     {@code options} array of the lookup field's values. It lists them only
 *     when this handler was built with {@code listsValues}; otherwise it
 *     answers in the same format with no options, so a form can never fetch
 *     everyone's email address (only the settings page lists its names).</li>
 * <li>A record: the first record whose lookup field equals the value, as JSON
 *     or as a {@code <data>} XML element. A birthdate is never served (SPEC.md
 *     D-7), nor a youth's phone number (D-8).</li>
 * </ul>
 *
 * @param <T> the kind of record in the file
 */
public class AutoFillHandler<T extends DataRecord> implements WebServer.WebHandler {
   private static final String CONTENT_TYPE_JSON = "text/json";
   private static final String CONTENT_TYPE_XML = "text/xml";

   /** A value of this length or shorter is not listed (an empty or placeholder value). */
   private static final int MIN_LISTED_VALUE_LENGTH = 5;

   private final EventData data;
   private final DataRecordFile<T> records;
   private final String lookupField;
   // Whether the list mode lists the lookup field's values; when it
   // doesn't, it answers in the same format with none.
   private final boolean listsValues;

   public AutoFillHandler(EventData data, DataRecordFile<T> records, String lookupField, boolean listsValues) {
      this.data = data;
      this.records = records;
      this.lookupField = lookupField;
      this.listsValues = listsValues;
   }

   // Locks this handler, not the event lock (as it always has).
   @Override
   public synchronized void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      Log.verbose(request);
      Log.verbose(request.getParameterMap());
      String op = request.getParameter("op");
      String format = request.getParameter("fmt");
      String lookupValue = request.getParameter(this.lookupField);

      String contentType;
      String body;
      if ("list".equals(op) || lookupValue == null) {
         contentType = CONTENT_TYPE_JSON;
         body = this.listOfValues();
      } else if ("json".equals(format)) {
         contentType = CONTENT_TYPE_JSON;
         body = this.recordAsJson(lookupValue);
      } else {
         contentType = CONTENT_TYPE_XML;
         body = this.recordAsXml(lookupValue);
      }

      response.setContentLength(body.length());
      response.setContentType(contentType);
      response.getOutputStream().write(body.getBytes());
      response.setStatus(200);
   }

   /** The columns a record is served with: never the birthdate (D-7), nor a youth's phone number (D-8). */
   private String[] servedColumns() {
      String[] servedColumns = CheckInApi.withoutBirthdate(this.records.getColumns());
      if (this.data.isYouthFile(this.records)) {
         servedColumns = CheckInApi.withoutYouthPhone(servedColumns);
      }
      return servedColumns;
   }

   /** The {@code options} list, or an empty one when this handler does not list values. */
   private String listOfValues() {
      StringBuilder out = new StringBuilder();
      out.append("{ options: [\n");
      boolean first = true;

      for (T record : this.listsValues ? this.records.getRecords() : List.<T>of()) {
         String value = record.get(this.lookupField);
         if (value.length() >= MIN_LISTED_VALUE_LENGTH) {
            if (!first) {
               out.append(",\n");
            }

            first = false;
            out.append("   { value: \"").append(value);
            out.append("\", text:\"").append(value).append("\"}");
         }
      }

      out.append("\n]\n}\n");
      return out.toString();
   }

   /** The first matching record as a JSON object, or nothing at all when none matches. */
   private String recordAsJson(String lookupValue) {
      // DataRecord.toJSON wants a StringBuffer.
      StringBuffer out = new StringBuffer();
      List<T> matches = this.records.get(this.lookupField, lookupValue);
      if (matches.size() > 0) {
         matches.get(0).toJSON(out, this.servedColumns());
      }
      return out.toString();
   }

   /** The first matching record as a {@code <data>} element (empty when none matches). */
   private String recordAsXml(String lookupValue) {
      StringBuilder out = new StringBuilder();
      out.append("<data>");
      List<T> matches = this.records.get(this.lookupField, lookupValue);
      if (matches.size() > 0) {
         T record = matches.get(0);
         boolean firstColumn = true;

         for (String column : this.servedColumns()) {
            if (!firstColumn) {
               out.append("\n");
            }

            firstColumn = false;
            out.append("<").append(column).append(">");
            out.append(record.getValue(column));
            out.append("</").append(column).append(">");
         }
      }

      out.append("</data>");
      return out.toString();
   }
}
