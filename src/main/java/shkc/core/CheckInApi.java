package shkc.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The calls the check-in pages make, the same in every version of Eagle
 * Boards: the pages are shared (eagleboards-shared/checkin), and this is the
 * API they speak, the Mac version's.
 *
 * <pre>
 *   GET  /api/checked-in     who has signed in, names and units only
 *   GET  /api/scout-choices  the youth an adult may say they will introduce (D-23)
 *   POST /api/youth-lookup   email=... -> the pre-registration it matches, or {}
 *   POST /api/adult-lookup   email=... -> the adult history it matches, or {}
 * </pre>
 *
 * <p>Each answer carries only what its page shows, so a tablet at the door never
 * holds a phone number or an email that is not the signer's own, no birthdate
 * at all (SPEC.md D-7), and no youth's phone number (D-8). The older -cells
 * and -autofill endpoints stay for the operator pages and older cached copies.
 *
 * <p>The static {@code withhold...} / {@code without...} helpers are also used
 * by the operator endpoints that serve whole records.
 */
public class CheckInApi {
   /** What the youth pre-registration lookup answers with. */
   private static final String[] YOUTH_PREFILL = {"ID", "Last", "First", "UnitType", "Unit", "BoardType", "Leader"};
   /** What the adult history lookup answers with. */
   private static final String[] ADULT_PREFILL = {"ID", "Last", "First", "Phone", "UnitType", "Unit", "FinalBoard", "ProjectReview"};

   private static final String BIRTHDATE_COLUMN = "DOB";
   private static final String PHONE_COLUMN = "Phone";
   /** Added to a withheld column's name; no record has a column by that name, so it reads as blank. */
   private static final String WITHHELD_SUFFIX = " (withheld)";

   /** The ID of the one configuration record. */
   private static final String CONFIG_ID = "DEFAULT";
   /** How often, in seconds, the page was told to refresh if the setting is missing or unreadable. */
   private static final int DEFAULT_REFRESH_SECONDS = 30;
   /** Typed in the lookup form as the email of someone who has none; it matches nobody. */
   private static final String NO_EMAIL = "none";

   private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

   /** Held while the data files are read, so a request never sees a half-finished change. */
   private final Object lock;
   private final DataRecordFile<ScoutRecord> scouts;
   private final DataRecordFile<ScoutRecord> scoutsScheduled;
   private final DataRecordFile<AdultRecord> adults;
   private final DataRecordFile<AdultRecord> adultHistory;
   private final DataRecordFile<ConfigRecord> config;
   private final ObjectMapper json = new ObjectMapper();

   /**
    * @param lock the lock every handler that touches the data files holds
    * @param scouts youth signed in at this event
    * @param scoutsScheduled youth who RSVP'd (the pre-registrations)
    * @param adults adults signed in at this event
    * @param adultHistory every adult who has signed in at any event
    * @param config the single configuration record
    */
   public CheckInApi(Object lock, DataRecordFile<ScoutRecord> scouts, DataRecordFile<ScoutRecord> scoutsScheduled,
         DataRecordFile<AdultRecord> adults, DataRecordFile<AdultRecord> adultHistory, DataRecordFile<ConfigRecord> config) {
      this.lock = lock;
      this.scouts = scouts;
      this.scoutsScheduled = scoutsScheduled;
      this.adults = adults;
      this.adultHistory = adultHistory;
      this.config = config;
   }

   /** Adds the four {@code /api/...} endpoints to the web server. */
   public void register(WebServer server) {
      server.addHandler("/api/checked-in", (target, request, response) -> this.send(response, this.checkedIn()));
      server.addHandler("/api/scout-choices", (target, request, response) -> this.send(response, this.scoutChoices()));
      server.addHandler("/api/youth-lookup", (target, request, response) ->
         this.send(response, this.lookUp(this.scoutsScheduled, request.getParameter("email"), YOUTH_PREFILL)));
      server.addHandler("/api/adult-lookup", (target, request, response) ->
         this.send(response, this.lookUp(this.adultHistory, request.getParameter("email"), ADULT_PREFILL)));
   }

   // A birthdate already on file is kept (SPEC.md O-5) but never served
   // (D-7). The first form swaps DOB for a column that holds nothing, so rows
   // keep their shape; the second drops it, for answers keyed by name.
   static String[] withholdBirthdate(String[] columns) {
      return withhold(columns, BIRTHDATE_COLUMN);
   }

   static String[] withoutBirthdate(String[] columns) {
      return without(columns, BIRTHDATE_COLUMN);
   }

   // A youth's phone number goes the same way (SPEC.md D-8): one already on
   // file stays there but is never served, in the same two forms. Only for
   // the youth files; an adult's number is still served.
   static String[] withholdYouthPhone(String[] columns) {
      return withhold(columns, PHONE_COLUMN);
   }

   static String[] withoutYouthPhone(String[] columns) {
      return without(columns, PHONE_COLUMN);
   }

   /** A copy of the columns with {@code withheld} renamed, keeping every column's position. */
   private static String[] withhold(String[] columns, String withheld) {
      if (columns == null) {
         return null;
      }
      String[] result = columns.clone();
      for (int i = 0; i < result.length; i++) {
         if (withheld.equals(result[i])) {
            result[i] = withheld + WITHHELD_SUFFIX;
         }
      }
      return result;
   }

   /** A copy of the columns with {@code withheld} left out. */
   private static String[] without(String[] columns, String withheld) {
      List<String> kept = new ArrayList<>();
      for (String column : columns) {
         if (!withheld.equals(column)) {
            kept.add(column);
         }
      }
      return kept.toArray(new String[0]);
   }

   // An email as the lookups compare it: trimmed, any case; NONE and blank
   // match nobody. The same rule as the Mac version's matchableEmail.
   static String matchable(String email) {
      String cleaned = email == null ? "" : email.trim().toLowerCase();
      return cleaned.isEmpty() || cleaned.equals(NO_EMAIL) ? null : cleaned;
   }

   /** The answer to {@code /api/checked-in}: how often to refresh, and who is here. */
   private Map<String, Object> checkedIn() {
      synchronized (this.lock) {
         Map<String, Object> answer = new LinkedHashMap<>();
         answer.put("refreshSeconds", this.refreshSeconds());
         answer.put("youth", this.checkedInYouth());
         answer.put("adults", this.checkedInAdults());
         return answer;
      }
   }

   /** The configured refresh interval, or the default if it is missing or not a number. */
   private int refreshSeconds() {
      ConfigRecord configRecord = this.config.get(CONFIG_ID);
      if (configRecord != null) {
         try {
            return Integer.parseInt(configRecord.getValue("RefreshTimeSecs").trim());
         } catch (NumberFormatException notANumber) {
            // fall through to the default
         }
      }
      return DEFAULT_REFRESH_SECONDS;
   }

   private List<Map<String, String>> checkedInYouth() {
      List<Map<String, String>> rows = new ArrayList<>();
      for (ScoutRecord scout : this.scouts.getRecords()) {
         Map<String, String> row = new LinkedHashMap<>();
         row.put("time", scout.getRegTimeHM());
         row.put("last", scout.getLast());
         row.put("first", scout.getFirst());
         row.put("unitType", scout.getUnitType());
         row.put("unit", scout.getUnit());
         rows.add(row);
      }
      return rows;
   }

   private List<Map<String, String>> checkedInAdults() {
      List<Map<String, String>> rows = new ArrayList<>();
      for (AdultRecord adult : this.adults.getRecords()) {
         Map<String, String> row = new LinkedHashMap<>();
         row.put("last", adult.getLast());
         row.put("first", adult.getFirst());
         row.put("unitType", adult.getUnitType());
         row.put("unit", adult.getUnit());
         rows.add(row);
      }
      return rows;
   }

   // Everyone who RSVP'd, plus the event's walk-ins, leaving out anyone
   // already finished (Completed or Postponed) and anyone here for a project
   // proposal, which has no introduction (SPEC.md D-23). The sign-in's record
   // wins over the RSVP with the same ID; sorted by last name, then first.
   private List<Map<String, String>> scoutChoices() {
      synchronized (this.lock) {
         Set<String> finishedIds = this.finishedScoutIds();

         // RSVPs first, then the sign-ins: a later record replaces an earlier one with the same ID.
         List<ScoutRecord> everyone = new ArrayList<>(this.scoutsScheduled.getRecords());
         everyone.addAll(this.scouts.getRecords());
         Map<String, ScoutRecord> byId = new LinkedHashMap<>();
         for (ScoutRecord scout : everyone) {
            if (!scout.getLast().isEmpty() && !finishedIds.contains(scout.getID())) {
               byId.put(scout.getID(), scout);
            }
         }

         List<ScoutRecord> candidates = new ArrayList<>();
         for (ScoutRecord scout : byId.values()) {
            if (!BoardType.PROJECT.equals(scout.getBoardType())) {
               candidates.add(scout);
            }
         }
         candidates.sort((a, b) -> {
            int byLast = a.getLast().compareToIgnoreCase(b.getLast());
            return byLast != 0 ? byLast : a.getFirst().compareToIgnoreCase(b.getFirst());
         });

         List<Map<String, String>> choices = new ArrayList<>();
         for (ScoutRecord scout : candidates) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", scout.getID());
            row.put("first", scout.getFirst());
            row.put("last", scout.getLast());
            row.put("unitType", scout.getUnitType());
            row.put("unit", scout.getUnit());
            choices.add(row);
         }
         return choices;
      }
   }

   /** The IDs of the youth whose board is over: completed, or sent away unprepared. */
   private Set<String> finishedScoutIds() {
      Set<String> finished = new HashSet<>();
      for (ScoutRecord scout : this.scouts.getRecords()) {
         String status = scout.getStatus();
         if (ScoutStatus.COMPLETED.equals(status) || ScoutStatus.POSTPONED.equals(status)) {
            finished.add(scout.getID());
         }
      }
      return finished;
   }

   /**
    * The first record whose email matches, as a map of just the requested
    * columns; an empty map if the email is blank, "none" or matches nobody.
    */
   private <T extends DataRecord> Map<String, String> lookUp(DataRecordFile<T> records, String email, String[] columns) {
      Map<String, String> found = new LinkedHashMap<>();
      String wanted = matchable(email);
      if (wanted == null) {
         return found;
      }
      synchronized (this.lock) {
         for (T record : records.getRecords()) {
            if (wanted.equals(matchable(record.getValue("Email")))) {
               for (String column : columns) {
                  found.put(column, record.getValue(column));
               }
               break;
            }
         }
      }
      return found;
   }

   /** Writes {@code value} as the JSON body of a 200 response that must not be cached. */
   private void send(HttpServletResponse response, Object value) throws IOException {
      byte[] body = this.json.writeValueAsBytes(value);
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType(JSON_CONTENT_TYPE);
      response.setHeader("Cache-Control", "no-store");
      response.setContentLength(body.length);
      response.getOutputStream().write(body);
   }
}
