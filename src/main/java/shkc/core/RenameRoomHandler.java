package shkc.core;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Renames a room from its card on the Event page, as the {@code /rename-room}
 * endpoint, not only in the Admin tables. The room keeps its ID; the youth and
 * adults in it move to the new name with it, so a board in progress is not
 * stranded looking for a room that no longer matches, which is what an
 * Admin-table edit of the Room column does (section 13 of the event test).
 * The Windows and Mac versions rename the same way.
 *
 * <p>Request parameters: {@code RoomID} (the room's ID) and {@code Room} (the
 * new name).
 *
 * <p>Answers {@code OK.} when the room was renamed (or already had that name),
 * or the reason it was not (see {@link BoardHandler}). The rename goes on the
 * Undo stack.
 */
public class RenameRoomHandler extends BoardHandler {
   private static final String UNDO_ROOM = "Room";
   private static final String UNDO_SCOUT = "Scout";
   private static final String UNDO_ADULT = "Adult";
   private static final String COLUMN_ROOM = "Room";

   public RenameRoomHandler(EventData data) {
      super(data);
   }

   @Override
   public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      synchronized (this.data.getLock()) {
         Log.verbose(request.getParameterMap());
         String roomId = request.getParameter("RoomID");
         String newName = request.getParameter("Room");
         RoomRecord room = this.data.getRooms().get(roomId);
         if (room == null) {
            this.sendError("ERROR: No Such Room " + roomId, response);
            return;
         }

         newName = newName == null ? "" : newName.trim();
         String badName = this.refusalForNameItself(newName);
         if (badName != null) {
            this.sendError(badName, response);
            return;
         }

         String oldName = room.getRoom();
         if (newName.equals(oldName)) {
            this.sendSuccess(response);
            return;
         }

         if (this.isNameOfAnotherRoom(newName, room)) {
            this.sendError("ERROR: Room " + newName + " already exists", response);
            return;
         }

         this.rename(room, oldName, newName);
         this.sendSuccess(response);
      }
   }

   /** Why {@code newName} could never be any room's name, or {@code null} if it could. */
   private String refusalForNameItself(String newName) {
      if (newName.length() == 0) {
         return "ERROR: Give the room a name or number";
      }

      // "N/A" is the Disable marker for an adult who has gone home (and what
      // Complete leaves in a youth's Room): a room by that name would
      // make everyone in it look gone. A comma would be written to the CSV
      // as "~" and read back as a different name. The Mac and Windows
      // versions refuse both too.
      if (RoomMarker.NO_ROOM.equalsIgnoreCase(newName)) {
         return "ERROR: N/A marks adults who have gone home; choose another name";
      }
      if (newName.contains(",")) {
         return "ERROR: A room name cannot contain a comma";
      }
      return null;
   }

   /** Whether some room other than {@code room} already has this name. */
   private boolean isNameOfAnotherRoom(String name, RoomRecord room) {
      for (RoomRecord other : this.data.getRooms().getRecords()) {
         if (other != room && name.equals(other.getRoom())) {
            return true;
         }
      }
      return false;
   }

   /** Gives the room its new name, moves its youth and adults along with it, and records the change for Undo. */
   private void rename(RoomRecord room, String oldName, String newName) throws IOException {
      UndoLog.Builder undo = this.data.getUndoLog().begin();
      room.setValue(COLUMN_ROOM, newName);
      undo.field(UNDO_ROOM, room, COLUMN_ROOM, oldName);
      for (ScoutRecord scout : this.data.getScouts().get(COLUMN_ROOM, oldName)) {
         scout.setRoom(newName);
         undo.field(UNDO_SCOUT, scout, COLUMN_ROOM, oldName);
      }
      for (AdultRecord adult : this.data.getAdults().get(COLUMN_ROOM, oldName)) {
         adult.setRoom(newName);
         undo.field(UNDO_ADULT, adult, COLUMN_ROOM, oldName);
      }

      this.data.getRooms().store();
      this.data.getScouts().store();
      this.data.getAdults().store();
      undo.commit("Rename room " + oldName + " to " + newName);
   }
}
