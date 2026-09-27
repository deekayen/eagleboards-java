package shkc.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The calls the check-in pages make, the same in every version of Eagle
// Boards: the pages are shared (eagleboards-shared/checkin), and this is the
// API they speak, the Mac version's.
//
//   GET  /api/checked-in     who has signed in, names and units only
//   GET  /api/scout-choices  the youth an adult may say they came to support
//   POST /api/youth-lookup   email=... -> the pre-registration it matches, or {}
//   POST /api/adult-lookup   email=... -> the adult history it matches, or {}
//
// Each answer carries only what its page shows, so a tablet at the door never
// holds a phone number or an email that is not the signer's own, no birthdate
// at all (SPEC.md D-7), and no youth's phone number (D-8). The older -cells
// and -autofill endpoints stay for the operator pages and older cached copies.
public class CheckInApi {
   private static final String[] YOUTH_PREFILL = {"ID", "Last", "First", "UnitType", "Unit", "BoardType", "Leader"};
   private static final String[] ADULT_PREFILL = {"ID", "Last", "First", "Phone", "UnitType", "Unit", "FinalBoard", "ProjectReview"};

   private final Object _lock;
   private final DataRecordFile<ScoutRecord> _scouts;
   private final DataRecordFile<ScoutRecord> _scoutsScheduled;
   private final DataRecordFile<AdultRecord> _adults;
   private final DataRecordFile<AdultRecord> _adultHistory;
   private final DataRecordFile<ConfigRecord> _config;
   private final ObjectMapper _json = new ObjectMapper();

   public CheckInApi(Object lock, DataRecordFile<ScoutRecord> scouts, DataRecordFile<ScoutRecord> scoutsScheduled,
         DataRecordFile<AdultRecord> adults, DataRecordFile<AdultRecord> adultHistory, DataRecordFile<ConfigRecord> config) {
      this._lock = lock;
      this._scouts = scouts;
      this._scoutsScheduled = scoutsScheduled;
      this._adults = adults;
      this._adultHistory = adultHistory;
      this._config = config;
   }

   public void register(WebServer server) {
      server.addHandler("/api/checked-in", (target, request, response) -> this.send(response, this.checkedIn()));
      server.addHandler("/api/scout-choices", (target, request, response) -> this.send(response, this.scoutChoices()));
      server.addHandler("/api/youth-lookup", (target, request, response) ->
         this.send(response, this.lookUp(this._scoutsScheduled, request.getParameter("email"), YOUTH_PREFILL)));
      server.addHandler("/api/adult-lookup", (target, request, response) ->
         this.send(response, this.lookUp(this._adultHistory, request.getParameter("email"), ADULT_PREFILL)));
   }

   // A birthdate already on file is kept (SPEC.md O-5) but never served
   // (D-7). The first form swaps DOB for a column that holds nothing, so rows
   // keep their shape; the second drops it, for answers keyed by name.
   static String[] withholdBirthdate(String[] columns) {
      return withhold(columns, "DOB");
   }

   static String[] withoutBirthdate(String[] columns) {
      return without(columns, "DOB");
   }

   // A youth's phone number goes the same way (SPEC.md D-8): one already on
   // file stays there but is never served, in the same two forms. Only for
   // the youth files; an adult's number is still served.
   static String[] withholdYouthPhone(String[] columns) {
      return withhold(columns, "Phone");
   }

   static String[] withoutYouthPhone(String[] columns) {
      return without(columns, "Phone");
   }

   private static String[] withhold(String[] columns, String withheld) {
      if (columns == null) {
         return null;
      }
      String[] out = columns.clone();
      for (int i = 0; i < out.length; i++) {
         if (withheld.equals(out[i])) {
            out[i] = withheld + " (withheld)";
         }
      }
      return out;
   }

   private static String[] without(String[] columns, String withheld) {
      List<String> out = new ArrayList<>();
      for (String column : columns) {
         if (!withheld.equals(column)) {
            out.add(column);
         }
      }
      return out.toArray(new String[0]);
   }

   // An email as the lookups compare it: trimmed, any case; NONE and blank
   // match nobody. The same rule as the Mac version's matchableEmail.
   static String matchable(String email) {
      String cleaned = email == null ? "" : email.trim().toLowerCase();
      return cleaned.isEmpty() || cleaned.equals("none") ? null : cleaned;
   }

   private Map<String, Object> checkedIn() {
      synchronized (this._lock) {
         Map<String, Object> out = new LinkedHashMap<>();
         int refreshSeconds = 30;
         ConfigRecord config = this._config.get("DEFAULT");
         if (config != null) {
            try {
               refreshSeconds = Integer.parseInt(config.getValue("RefreshTimeSecs").trim());
            } catch (NumberFormatException notANumber) {
               // keep the default
            }
         }
         out.put("refreshSeconds", refreshSeconds);

         List<Map<String, String>> youth = new ArrayList<>();
         for (ScoutRecord scout : this._scouts.getRecords()) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("time", scout.getRegTimeHM());
            row.put("last", scout.getLast());
            row.put("first", scout.getFirst());
            row.put("unitType", scout.getUnitType());
            row.put("unit", scout.getUnit());
            youth.add(row);
         }
         out.put("youth", youth);

         List<Map<String, String>> adults = new ArrayList<>();
         for (AdultRecord adult : this._adults.getRecords()) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("last", adult.getLast());
            row.put("first", adult.getFirst());
            row.put("unitType", adult.getUnitType());
            row.put("unit", adult.getUnit());
            adults.add(row);
         }
         out.put("adults", adults);
         return out;
      }
   }

   // Everyone who RSVP'd, plus tonight's walk-ins, leaving out anyone whose
   // evening is over (Completed or Postponed). Tonight's record wins over the
   // RSVP with the same ID; sorted by last name, then first.
   private List<Map<String, String>> scoutChoices() {
      synchronized (this._lock) {
         List<String> done = new ArrayList<>();
         for (ScoutRecord scout : this._scouts.getRecords()) {
            if ("Completed".equals(scout.getStatus()) || "Postponed".equals(scout.getStatus())) {
               done.add(scout.getID());
            }
         }
         Map<String, ScoutRecord> byId = new LinkedHashMap<>();
         List<ScoutRecord> everyone = new ArrayList<>(this._scoutsScheduled.getRecords());
         everyone.addAll(this._scouts.getRecords());
         for (ScoutRecord scout : everyone) {
            if (scout.getLast().length() > 0 && !done.contains(scout.getID())) {
               byId.put(scout.getID(), scout);
            }
         }
         List<ScoutRecord> sorted = new ArrayList<>(byId.values());
         sorted.sort((a, b) -> {
            int byLast = a.getLast().compareToIgnoreCase(b.getLast());
            return byLast != 0 ? byLast : a.getFirst().compareToIgnoreCase(b.getFirst());
         });
         List<Map<String, String>> out = new ArrayList<>();
         for (ScoutRecord scout : sorted) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", scout.getID());
            row.put("first", scout.getFirst());
            row.put("last", scout.getLast());
            row.put("unitType", scout.getUnitType());
            row.put("unit", scout.getUnit());
            out.add(row);
         }
         return out;
      }
   }

   private <T extends DataRecord> Map<String, String> lookUp(DataRecordFile<T> records, String email, String[] columns) {
      Map<String, String> out = new LinkedHashMap<>();
      String wanted = matchable(email);
      if (wanted == null) {
         return out;
      }
      synchronized (this._lock) {
         for (T record : records.getRecords()) {
            if (wanted.equals(matchable(record.getValue("Email")))) {
               for (String column : columns) {
                  out.put(column, record.getValue(column));
               }
               break;
            }
         }
      }
      return out;
   }

   private void send(HttpServletResponse response, Object value) throws IOException {
      byte[] body = this._json.writeValueAsBytes(value);
      response.setStatus(200);
      response.setContentType("application/json; charset=utf-8");
      response.setHeader("Cache-Control", "no-store");
      response.setContentLength(body.length);
      response.getOutputStream().write(body);
   }
}
