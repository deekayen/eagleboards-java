package shkc.core;

import java.io.IOException;
import java.util.Arrays;
import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code /adult-update}: applies an edit made on the Admin page's Adults tab.
 *
 * <p>Reads the base class's parameters (see {@link RecordUpdateHandler}). An
 * adult sitting on a board cannot be deleted, and an edit of the adult's
 * sign-in facts is carried to the same adult in the adult history.
 */
public class AdultUpdateHandler extends RecordUpdateHandler<AdultRecord> {
   private final EventData data;

   public AdultUpdateHandler(EventData data) {
      super(data, data.getAdults(), "Adult");
      this.data = data;
   }

   // Disable/Enable (Room) and Link (Supporting) are Event page steps and
   // keep Undo (O-2). An admin edit of the facts below stays off the Undo
   // stack (SPEC.md P-6): undoing it here alone would leave the adult
   // history disagreeing with the event's record.
   @Override
   protected boolean keepsOffUndo(String column) {
      return super.keepsOffUndo(column) || Arrays.asList(RegistrationFields.ADULT_FIELDS).contains(column);
   }

   // SPEC.md P-6, amended 2026-10-24: a table never deletes an adult
   // sitting on a board (a Room, other than the Disable button's N/A).
   // A deleted member had left the board a member short.
   @Override
   protected String refusal(DataRecord record, HttpServletRequest request, String editStatus) {
      String room = record.getValue("Room");
      if (!EDIT_DELETED.equals(editStatus) || room.length() == 0 || RoomMarker.NO_ROOM.equals(room)) {
         return null;
      }
      return record.getValue("First") + " " + record.getValue("Last") + " is on the board in room " + room
         + ". Reset or complete that board on the Event page first.";
   }

   @Override
   protected void afterEdit(DataRecord record, HttpServletRequest request, String editStatus) throws IOException {
      this.shareAdultFacts(record, request, editStatus, this.data.getAdultHistory());
   }

   /**
    * An adult's name, unit, contact and roles are one set of facts in
    * the event's adults and the adult history, as a sign-in carries them
    * between the two (SPEC.md P-6): an admin edit on the Adults tab is made
    * to the same adult in the read-only history, so someone promoted to
    * chair today is a chair the next time they sign in. Wood Badge, whom
    * they introduce (D-23) and their room belong to this event alone.
    */
   private void shareAdultFacts(DataRecord edited, HttpServletRequest request, String editStatus, DataRecordFile<AdultRecord> adultHistory) throws IOException {
      if (!EDIT_UPDATED.equals(editStatus)) {
         return;
      }
      boolean factChanged = false;
      for (String column : RegistrationFields.ADULT_FIELDS) {
         factChanged |= request.getParameter(column) != null;
      }
      AdultRecord historyRecord = adultHistory.get(edited.getID());
      if (!factChanged || historyRecord == null) {
         return;
      }
      for (String column : RegistrationFields.ADULT_FIELDS) {
         historyRecord.put(column, edited.getValue(column));
      }
      historyRecord.updateFields(false);
      adultHistory.store();
   }
}
