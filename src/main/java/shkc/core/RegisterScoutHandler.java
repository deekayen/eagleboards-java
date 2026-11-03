package shkc.core;

import java.io.IOException;
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

         ScoutRecord scout = new ScoutRecord(request.getParameterMap());
         // SPEC.md D-7: no birthdate is asked for or kept. An older cached
         // sign-in page may still send one; it is discarded here.
         scout.setValue("DOB", "");
         // SPEC.md D-8: nor a youth's phone number, discarded the same way.
         scout.setValue("Phone", "");
         scout = this.addOrUpdateToday(scout);

         if (this.reservedBeforehand(scout)) {
            scout.setRegNum(PRE_REGISTERED_PREFIX + (preRegisteredCount + 1));
         } else {
            scout.setRegNum(WALK_IN_PREFIX + (walkInCount + 1));
         }

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

   /** Whether the youth is on the list of those who reserved a slot, by ID or by email address. */
   private boolean reservedBeforehand(ScoutRecord scout) {
      DataRecordFile<ScoutRecord> reservations = this.data.getScoutsScheduled();
      return reservations.get(scout.getID()) != null || reservations.get("Email", scout.getEmail()).size() != 0;
   }
}
