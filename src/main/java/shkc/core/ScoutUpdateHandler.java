package shkc.core;

import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code /youth-update}: applies an edit made on the Admin page's Youth tab,
 * and refuses the edits that would leave a board without its room or members.
 *
 * <p>Reads the base class's parameters (see {@link RecordUpdateHandler}); the
 * ones that matter here are {@code Status} and the row's {@code Room}.
 */
public class ScoutUpdateHandler extends RecordUpdateHandler<ScoutRecord> {
   public ScoutUpdateHandler(EventData data) {
      super(data, data.getScouts());
   }

   // SPEC.md P-6: a table corrects a status, but a board is seated,
   // started, reset, and completed only through the Event page's steps,
   // which give the scout a room and members and take them back. An admin
   // edit never sets Seated or InProgress, and never changes the status of
   // a board that is sitting. The rest of the record still corrects.
   // Nor does a table delete a scout whose board is sitting (amended
   // 2026-10-24): a deleted scout had left the room held by nobody.
   @Override
   protected String refusal(DataRecord record, HttpServletRequest request, String editStatus) {
      String newStatus = request.getParameter("Status");
      String oldStatus = record.getValue("Status");
      String name = record.getValue("First") + " " + record.getValue("Last");
      if (EDIT_DELETED.equals(editStatus)) {
         return ScoutStatus.holdsRoom(oldStatus)
            ? name + "'s board is in room " + record.getValue("Room") + ". Reset it or complete it on the Event page first."
            : null;
      }
      if (newStatus == null || newStatus.equals(oldStatus)) {
         return null;
      }
      if (ScoutStatus.holdsRoom(newStatus)) {
         return name + " can be seated, or their review started, only on the Event page, where the board gets its room and members.";
      }
      if (ScoutStatus.holdsRoom(oldStatus)) {
         return name + "'s board is in room " + record.getValue("Room")
            + ". Reset it or complete it on the Event page, which frees the room and its members.";
      }
      return null;
   }
}
