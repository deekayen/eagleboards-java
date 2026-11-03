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
 */
public class RegisterAdultHandler implements WebServer.WebHandler {
   /** Marks an adult who was not in the adult history before today's sign-in. */
   private static final String FLAG_NEW_TO_HISTORY = "W";

   /** Marks an adult who was already in the adult history. */
   private static final String FLAG_IN_HISTORY = "P";

   private static final String WOOD_BADGE = "WoodBadge";
   private static final String SUPPORTING = "Supporting";
   private static final String YES = "Y";

   private final EventData data;

   public RegisterAdultHandler(EventData data) {
      this.data = data;
   }

   @Override
   public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      synchronized (this.data.getLock()) {
         Log.verbose(request);
         AdultRecord adult = new AdultRecord(request.getParameterMap());
         adult.setRoom("");
         adult.setValue(WOOD_BADGE, YES.equals(adult.getValue(WOOD_BADGE)) ? YES : "");
         adult.setValue(SUPPORTING, adult.getValue(SUPPORTING).trim());

         AdultRecord signedIn = this.addOrUpdateToday(adult);
         this.copyIntoHistory(signedIn);

         this.data.getAdults().store();
         this.data.getAdultHistory().store();
         response.setStatus(200);
      }
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
    */
   private void copyIntoHistory(AdultRecord adult) {
      AdultRecord historyRecord = this.data.getAdultHistory().get(adult.getID());
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
      } else {
         promoteToChair(historyRecord, adult);
         historyRecord.updateFrom(adult, RegistrationFields.ADULT_FIELDS);
         adult.setFlags(FLAG_IN_HISTORY);
      }

      historyRecord.setBoardHistory(historyRecord.getBoardHistory() + "(" + LocalDate.now() + ")");
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
