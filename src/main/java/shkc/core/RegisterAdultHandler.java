package shkc.core;

import java.io.IOException;
import java.time.LocalDate;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code /register-adult}: an adult signs in on the tablet.
 *
 * <p>Reads one request parameter per column of {@link AdultRecord} (name, unit,
 * contact, the {@code ProjectReview} and {@code FinalBoard} roles,
 * {@code WoodBadge} and {@code Supporting}); the ID identifies the adult. The
 * adult is added to today's adults file, or updated if already there, and is
 * then copied into the adult history so that next month's form can pre-fill.
 * Answers with status 200 and an empty body.
 *
 * <p>Every sign-in writes one line to the event log: who, whether today's
 * record was new or updated, whether the adult history knew them, the role for
 * each kind of board, and the Wood Badge and Supporting answers (a count, not
 * the scouts). Contact details appear only as a column name when they changed.
 */
public class RegisterAdultHandler implements WebServer.WebHandler {
   /** Marks an adult who was not in the adult history before today's sign-in. */
   private static final String FLAG_NEW_TO_HISTORY = "W";

   /** Marks an adult who was already in the adult history. */
   private static final String FLAG_IN_HISTORY = "P";

   private static final String WOOD_BADGE = "WoodBadge";
   private static final String SUPPORTING = "Supporting";
   private static final String YES = "Y";

   /** Supporting scouts are listed with this between their IDs, since commas become tildes on disk. */
   private static final String SUPPORTING_SEPARATOR = "|";

   private final EventData data;

   public RegisterAdultHandler(EventData data) {
      this.data = data;
   }

   @Override
   public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      synchronized (this.data.getLock()) {
         Log.verbose(request);
         String suppliedId = request.getParameter("ID");
         AdultRecord adult = new AdultRecord(request.getParameterMap());
         String sentWoodBadge = adult.getValue(WOOD_BADGE);
         adult.setRoom("");
         adult.setValue(WOOD_BADGE, YES.equals(sentWoodBadge) ? YES : "");
         adult.setValue(SUPPORTING, adult.getValue(SUPPORTING).trim());

         if (adult.getLast().trim().isEmpty() || adult.getFirst().trim().isEmpty()) {
            Log.warn("register-adult: adult=" + adult.getID() + " signed in with a blank first or last name, so its ID is built from empty parts");
         }

         AdultRecord before = this.data.getAdults().get(adult.getID());
         String changes = before == null ? "" : changedColumns(before, adult);
         String previousRoom = before == null ? "" : before.getRoom();

         AdultRecord signedIn = this.addOrUpdateToday(adult);
         String history = this.copyIntoHistory(signedIn);

         StringBuilder line = new StringBuilder("register-adult: adult=").append(signedIn.getID());
         if (before == null) {
            line.append(" record=new");
         } else {
            line.append(" record=updated changed=[").append(changes).append(']');
            if (!previousRoom.isEmpty() && !"N/A".equals(previousRoom)) {
               line.append(" NOTE already holding room ").append(Log.safeValue("Room", previousRoom)).append(" before this sign-in");
            }
         }
         line.append(" history=").append(history)
            .append(" idsource=").append(suppliedId == null || suppliedId.isEmpty() ? "built-from-name" : "request")
            .append(" unit=").append(Log.safeValue("UnitType", signedIn.getUnitType())).append(' ').append(Log.safeValue("Unit", signedIn.getUnit()))
            .append(" project-review=").append(describeRole(signedIn.getProjectReviewRole()))
            .append(" final-board=").append(describeRole(signedIn.getFinalBoardRole()))
            .append(" woodbadge=").append(YES.equals(signedIn.getValue(WOOD_BADGE)) ? "Y" : "no");
         if (!sentWoodBadge.isEmpty() && !YES.equals(sentWoodBadge)) {
            line.append(" (WoodBadge sent as ").append(Log.safeValue(WOOD_BADGE, sentWoodBadge)).append(", treated as no)");
         }
         line.append(" supporting=").append(countSupporting(signedIn.getValue(SUPPORTING))).append(" -> signed in");
         Log.info(line.toString());

         this.data.getAdults().store();
         this.data.getAdultHistory().store();
         response.setStatus(200);
      }
   }

   /** A role as the log shows it: the word, "(none)" when blank, and a flag when it is not one the form offers. */
   private static String describeRole(String role) {
      if (role.isEmpty()) {
         return "(none)";
      }
      boolean known = AdultRole.MEMBER.equals(role) || AdultRole.CHAIR.equals(role) || AdultRole.UNAVAILABLE.equals(role);
      return Log.safeValue("FinalBoard", role) + (known ? "" : " (UNRECOGNIZED role)");
   }

   /** How many scouts the adult said they will introduce. */
   private static int countSupporting(String supporting) {
      int count = 0;
      for (String id : supporting.split("\\" + SUPPORTING_SEPARATOR)) {
         if (!id.trim().isEmpty()) {
            count++;
         }
      }
      return count;
   }

   /**
    * The sign-in columns whose new value differs from the file's, as column
    * names only. A name or a role is shown as {@code old -> new}; an email
    * address or phone number is only named.
    */
   private static String changedColumns(AdultRecord existing, AdultRecord incoming) {
      StringBuilder changes = new StringBuilder();
      for (String column : RegistrationFields.ADULT_FIELDS) {
         String newValue = incoming.get(column);
         if (newValue != null && newValue.length() > 0 && !newValue.equals(existing.get(column))) {
            changes.append(changes.length() == 0 ? "" : ", ").append(column);
            if (!"Email".equals(column) && !"Phone".equals(column)) {
               changes.append(' ').append(Log.safeValue(column, existing.getValue(column)))
                  .append(" -> ").append(Log.safeValue(column, newValue));
            }
         }
      }
      return changes.length() == 0 ? "none" : changes.toString();
   }

   /**
    * Puts the adult into today's adults file.
    *
    * @param adult the record built from this sign-in
    * @return the record now held in the file: the existing one, brought up to
    *         date, or {@code adult} itself when this is the first sign-in today
    */
   private AdultRecord addOrUpdateToday(AdultRecord adult) {
      AdultRecord existing = this.data.getAdults().get(adult.getID());
      if (existing == null) {
         Log.verbose("NEW ADULT RECORD: " + adult);
         this.data.getAdults().add(adult, false);
         return adult;
      }

      existing.updateFrom(adult, RegistrationFields.ADULT_FIELDS);
      // Answers for this event only: the latest sign-in says what is true now.
      existing.setValue(WOOD_BADGE, adult.getValue(WOOD_BADGE));
      existing.setValue(SUPPORTING, adult.getValue(SUPPORTING));
      return existing;
   }

   /**
    * Merges today's sign-in into the adult history and flags the adult by
    * whether the history already knew them. Today's date is appended to the
    * history's list of the dates the adult served.
    *
    * @return what happened to the history, for the log: a new record, or a
    *         match with any change to the roles it keeps
    */
   private String copyIntoHistory(AdultRecord adult) {
      AdultRecord historyRecord = this.data.getAdultHistory().get(adult.getID());
      String outcome;
      if (historyRecord == null) {
         historyRecord = adult.clone();
         // The history pre-fills next month's form. Whom someone
         // introduces (D-23), and whether it counted toward a Wood Badge
         // ticket, are answers for this event only, so they are not kept.
         historyRecord.setValue(WOOD_BADGE, "");
         historyRecord.setValue(SUPPORTING, "");
         Log.verbose("Adding new Adult History Record: " + historyRecord);
         this.data.getAdultHistory().add(historyRecord, false);
         adult.setFlags(FLAG_NEW_TO_HISTORY);
         outcome = "new record created";
      } else {
         String roleBefore = "project-review " + describeRole(historyRecord.getProjectReviewRole())
            + ", final-board " + describeRole(historyRecord.getFinalBoardRole());
         promoteToChair(historyRecord, adult);
         historyRecord.updateFrom(adult, RegistrationFields.ADULT_FIELDS);
         adult.setFlags(FLAG_IN_HISTORY);
         String roleAfter = "project-review " + describeRole(historyRecord.getProjectReviewRole())
            + ", final-board " + describeRole(historyRecord.getFinalBoardRole());
         outcome = roleBefore.equals(roleAfter)
            ? "matched and refreshed (roles on file unchanged: " + roleAfter + ")"
            : "matched and refreshed (roles on file " + roleBefore + " -> " + roleAfter + ")";
      }

      historyRecord.setBoardHistory(historyRecord.getBoardHistory() + "(" + LocalDate.now() + ")");
      return outcome;
   }

   /**
    * Lets a Member on file become a Chair when today's sign-in says Chair. The
    * history never goes the other way here: someone qualified to chair who
    * signs in as a Member today stays a Chair on file.
    */
   private static void promoteToChair(AdultRecord historyRecord, AdultRecord signedIn) {
      if (AdultRole.MEMBER.equals(historyRecord.getFinalBoardRole()) && AdultRole.CHAIR.equals(signedIn.getFinalBoardRole())) {
         historyRecord.setValue("FinalBoard", AdultRole.CHAIR);
      }

      if (AdultRole.MEMBER.equals(historyRecord.getProjectReviewRole()) && AdultRole.CHAIR.equals(signedIn.getProjectReviewRole())) {
         historyRecord.setValue("ProjectReview", AdultRole.CHAIR);
      }
   }
}
