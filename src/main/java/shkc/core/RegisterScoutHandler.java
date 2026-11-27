package shkc.core;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code /register-youth}: a youth signs in on the tablet.
 *
 * <p>Reads one request parameter per column of {@link ScoutRecord} (name, unit,
 * email, leader); the ID identifies the youth. The youth is added to today's
 * scouts file, or updated if already there, with status
 * {@link ScoutStatus#REGISTERED} and a registration number: {@code P#} for a
 * youth who reserved a slot beforehand, {@code W#} for a walk-in. Answers with
 * status 200 and an empty body.
 *
 * <p>Every sign-in writes one line to the event log: who, which number, whether
 * an RSVP matched and by what rule, whether the record was new or updated, and
 * what an old cached page sent that was discarded (the column names, never the
 * values). A walk-in whose name or email nearly matches an RSVP is called out,
 * because that is the usual reason a scout who did reserve gets a {@code W#}.
 */
public class RegisterScoutHandler implements WebServer.WebHandler {
   /** Registration numbers of youth who reserved a slot beforehand begin with this. */
   private static final String PRE_REGISTERED_PREFIX = "P";

   /** Registration numbers of walk-ins begin with this. */
   private static final String WALK_IN_PREFIX = "W";

   private final EventData data;

   public RegisterScoutHandler(EventData data) {
      this.data = data;
   }

   @Override
   public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      synchronized (this.data.getLock()) {
         Log.verbose(request);
         // Counted before this youth is added, so the next number is count + 1.
         int preRegisteredCount = this.countRegistrationsWithPrefix(PRE_REGISTERED_PREFIX);
         int walkInCount = this.countRegistrationsWithPrefix(WALK_IN_PREFIX);

         String suppliedId = request.getParameter("ID");
         ScoutRecord scout = new ScoutRecord(request.getParameterMap());
         // SPEC.md D-7: no birthdate is asked for or kept. An older cached
         // sign-in page may still send one; it is discarded here.
         // SPEC.md D-8: nor a youth's phone number, discarded the same way.
         String discarded = this.discardedColumns(scout);
         scout.setValue("DOB", "");
         scout.setValue("Phone", "");

         if (scout.getLast().trim().isEmpty() || scout.getFirst().trim().isEmpty()) {
            Log.warn("register-youth: scout=" + scout.getID() + " signed in with a blank first or last name, so its ID is built from empty parts");
         }

         // What the file held for this ID before the sign-in, for the log line.
         ScoutRecord before = this.data.getScouts().get(scout.getID());
         String previousStatus = before == null ? null : before.getStatus();
         String previousRegNum = before == null ? null : before.getRegNum();
         String changes = before == null ? "" : changedColumns(before, scout);

         scout = this.addOrUpdateToday(scout);

         String reservation = this.findReservation(scout);
         String regNum;
         if (reservation != null) {
            regNum = PRE_REGISTERED_PREFIX + (preRegisteredCount + 1);
         } else {
            regNum = WALK_IN_PREFIX + (walkInCount + 1);
         }
         scout.setRegNum(regNum);

         StringBuilder line = new StringBuilder("register-youth: scout=").append(scout.getID());
         if (before == null) {
            line.append(" regnum=").append(regNum).append(" record=new");
         } else {
            line.append(" regnum ").append(Log.safeValue("RegNum", previousRegNum)).append(" -> ").append(regNum)
               .append(" record=updated changed=[").append(changes).append(']');
         }
         line.append(" reservation=").append(reservation == null ? this.describeMissedReservation(scout) : reservation)
            .append(" idsource=").append(suppliedId == null || suppliedId.isEmpty() ? "built-from-name" : "request")
            .append(" boardtype=").append(Log.safeValue("BoardType", scout.getBoardType()))
            .append(" unit=").append(Log.safeValue("UnitType", scout.getUnitType())).append(' ').append(Log.safeValue("Unit", scout.getUnit()));
         if (before == null) {
            line.append(" status=").append(Log.safeValue("Status", scout.getStatus()));
         } else {
            line.append(" status ").append(Log.safeValue("Status", previousStatus)).append(" -> ").append(Log.safeValue("Status", scout.getStatus()));
            if (!previousStatus.isEmpty() && !ScoutStatus.REGISTERED.equals(previousStatus)) {
               line.append(" NOTE this youth had already moved past Registered, and the sign-in gave a new number");
            }
         }
         if (!discarded.isEmpty()) {
            line.append(" discarded=").append(discarded);
         }
         line.append(" counts(P=").append(preRegisteredCount).append(" W=").append(walkInCount).append(") -> signed in");
         Log.info(line.toString());

         this.data.getScouts().store();
         response.setStatus(200);
      }
   }

   /** How many youth signed in today already hold a registration number beginning with {@code prefix}. */
   private int countRegistrationsWithPrefix(String prefix) {
      int count = 0;
      for (ScoutRecord signedIn : this.data.getScouts().getRecords()) {
         if (signedIn.getRegNum().startsWith(prefix)) {
            count++;
         }
      }
      return count;
   }

   /** The names of the columns an old page sent that this sign-in throws away (never their values), comma separated. */
   private String discardedColumns(ScoutRecord scout) {
      StringBuilder names = new StringBuilder();
      for (String column : new String[]{"DOB", "Phone"}) {
         if (!scout.getValue(column).isEmpty()) {
            names.append(names.length() == 0 ? "" : ",").append(column);
         }
      }
      return names.toString();
   }

   /**
    * The sign-in columns whose new value differs from the file's, as
    * {@code Column old -> new}. Only the safe columns show their values; an
    * email address or a leader's name is a length.
    */
   private static String changedColumns(ScoutRecord existing, ScoutRecord incoming) {
      StringBuilder changes = new StringBuilder();
      for (String column : RegistrationFields.SCOUT_FIELDS) {
         String newValue = incoming.get(column);
         if (newValue != null && newValue.length() > 0 && !newValue.equals(existing.get(column))) {
            changes.append(changes.length() == 0 ? "" : ", ")
               .append(column).append(' ').append(Log.safeValue(column, existing.getValue(column)))
               .append(" -> ").append(Log.safeValue(column, newValue));
         }
      }
      return changes.length() == 0 ? "none" : changes.toString();
   }

   /**
    * Puts the youth into today's scouts file.
    *
    * @param scout the record built from this sign-in
    * @return the record now held in the file: the existing one, brought up to
    *         date, or {@code scout} itself when this is the first sign-in today
    */
   private ScoutRecord addOrUpdateToday(ScoutRecord scout) {
      ScoutRecord existingScout = this.data.getScouts().get(scout.getID());
      if (existingScout == null) {
         Log.verbose("NEW SCOUT RECORD: " + scout);
         this.data.getScouts().add(scout, false);
         scout.setStatus(ScoutStatus.REGISTERED);
         return scout;
      }

      Log.verbose("UPDATING EXISTING SCOUT RECORD: " + existingScout);
      existingScout.updateFrom(scout, RegistrationFields.SCOUT_FIELDS);
      if (existingScout.getStatus().length() == 0) {
         existingScout.setStatus(ScoutStatus.REGISTERED);
      }
      return existingScout;
   }

   /**
    * How the youth is on the list of those who reserved a slot, by ID or by
    * email address; null when they are not. The answer names the rule and the
    * reservation it found, and says so when the email was blank on both sides
    * (which counts as a match, as it always has).
    */
   private String findReservation(ScoutRecord scout) {
      DataRecordFile<ScoutRecord> reservations = this.data.getScoutsScheduled();
      if (reservations.get(scout.getID()) != null) {
         return "id (" + scout.getID() + ")";
      }
      List<ScoutRecord> sameEmail = reservations.get("Email", scout.getEmail());
      if (sameEmail.size() != 0) {
         String shared = sameEmail.size() > 1 ? ", " + sameEmail.size() + " reservations share it" : "";
         return (scout.getEmail().isEmpty() ? "email-blank-on-both" : "email") + " (" + sameEmail.get(0).getID() + shared + ")";
      }
      return null;
   }

   /**
    * For a youth who matched no reservation: "none", plus any reservation that
    * nearly matches (the same email apart from case or spaces, or the same
    * name under another unit), since that is how an RSVP'd scout ends up a
    * walk-in. Walk-ins are few, so the scan of the reservations is cheap.
    */
   private String describeMissedReservation(ScoutRecord scout) {
      String wantedEmail = CheckInApi.matchable(scout.getEmail());
      for (ScoutRecord reservation : this.data.getScoutsScheduled().getRecords()) {
         if (wantedEmail != null && wantedEmail.equals(CheckInApi.matchable(reservation.getEmail()))) {
            return "none (NEAR MISS: the email matches " + reservation.getID() + " ignoring case or spaces, but not exactly)";
         }
         if (!scout.getLast().trim().isEmpty() && scout.getLast().trim().equalsIgnoreCase(reservation.getLast().trim())
               && scout.getFirst().trim().equalsIgnoreCase(reservation.getFirst().trim())) {
            return "none (NEAR MISS: same name as " + reservation.getID() + " under a different ID or email)";
         }
      }
      return "none";
   }
}
