package shkc.core;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Swaps two rooms' boards, as the {@code /room-change} endpoint. The operator
 * uses it from the Event page to move a board to a different room: whatever
 * sits in the first room goes to the second, and whatever sat in the second
 * goes to the first (so moving into an empty room is just a swap with it).
 *
 * <p>Request parameters: {@code RmID1} and {@code RmID2}, the IDs of the two
 * rooms.
 *
 * <p>Answers {@code OK.} when the boards were swapped, or the reason they were
 * not (see {@link BoardHandler}). A room that already holds more than one
 * scout is refused, since there would be no telling which board is meant.
 * The change goes on the Undo stack.
 */
public class RoomChangeHandler extends BoardHandler {
   private static final String UNDO_SCOUT = "Scout";
   private static final String UNDO_ADULT = "Adult";
   private static final String UNDO_ROOM = "Room";
   private static final String COLUMN_ROOM = "Room";

   public RoomChangeHandler(EventData data) {
      super(data);
   }

   @Override
   public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      synchronized (this.data.getLock()) {
         Log.verbose("RoomChangeHandler: " + request);
         this.logRequest(request);
         String roomId1 = request.getParameter("RmID1");
         String roomId2 = request.getParameter("RmID2");
         if (roomId1 == null || roomId1.equals("")) {
            this.sendError("ERROR: Invalid Room ID" + roomId1, response);
            return;
         }
         if (roomId2 == null || roomId2.equals("")) {
            this.sendError("ERROR: Invalid Room ID" + roomId2, response);
            return;
         }

         Log.verbose("ChangeRoom: RmID1=" + roomId1 + ", RmID2=" + roomId2);
         RoomRecord room1 = this.data.getRooms().get(roomId1);
         RoomRecord room2 = this.data.getRooms().get(roomId2);
         if (room1 == null) {
            this.sendError("ERROR: No Such Room" + roomId1, response);
            return;
         }
         if (room2 == null) {
            this.sendError("ERROR: No Such Room" + roomId2, response);
            return;
         }

         List<ScoutRecord> scoutsInRoom1 = this.data.getScouts().get(COLUMN_ROOM, room1.getRoom());
         List<ScoutRecord> scoutsInRoom2 = this.data.getScouts().get(COLUMN_ROOM, room2.getRoom());
         if (scoutsInRoom1.size() > 1) {
            this.sendError("ERROR: Room assigned to multiple scouts: " + room1.getRoom(), response);
            return;
         }
         if (scoutsInRoom2.size() > 1) {
            this.sendError("ERROR: Room assigned to multiple scouts: " + room2.getRoom(), response);
            return;
         }

         this.swapBoards(room1, room2, scoutsInRoom1, scoutsInRoom2);
         this.sendSuccess(response);
      }
   }

   /**
    * Trades the two room cards' leaders and scout, moves the youth and adults
    * of each room into the other, stores all three files and records the
    * change for Undo.
    */
   private void swapBoards(RoomRecord room1, RoomRecord room2, List<ScoutRecord> scoutsInRoom1, List<ScoutRecord> scoutsInRoom2) throws IOException {
      List<AdultRecord> adultsInRoom1 = this.data.getAdults().get(COLUMN_ROOM, room1.getRoom());
      List<AdultRecord> adultsInRoom2 = this.data.getAdults().get(COLUMN_ROOM, room2.getRoom());
      String room1Name = room1.getRoom();
      String room2Name = room2.getRoom();
      String leaders1 = room1.getLeaders();
      String leaders2 = room2.getLeaders();
      String scout1 = room1.getScout();
      String scout2 = room2.getScout();

      room1.setLeaders(leaders2);
      room2.setLeaders(leaders1);
      room1.setScout(scout2);
      room2.setScout(scout1);

      UndoLog.Builder undo = this.data.getUndoLog().begin();
      undo.field(UNDO_ROOM, room1, "Leaders", leaders1);
      undo.field(UNDO_ROOM, room2, "Leaders", leaders2);
      undo.field(UNDO_ROOM, room1, "Scout", scout1);
      undo.field(UNDO_ROOM, room2, "Scout", scout2);

      for (ScoutRecord scoutMovingTo2 : scoutsInRoom1) {
         scoutMovingTo2.setRoom(room2Name);
         undo.field(UNDO_SCOUT, scoutMovingTo2, COLUMN_ROOM, room1Name);
      }

      for (ScoutRecord scoutMovingTo1 : scoutsInRoom2) {
         scoutMovingTo1.setRoom(room1Name);
         undo.field(UNDO_SCOUT, scoutMovingTo1, COLUMN_ROOM, room2Name);
      }

      for (AdultRecord adultMovingTo2 : adultsInRoom1) {
         adultMovingTo2.setRoom(room2Name);
         undo.field(UNDO_ADULT, adultMovingTo2, COLUMN_ROOM, room1Name);
      }

      for (AdultRecord adultMovingTo1 : adultsInRoom2) {
         adultMovingTo1.setRoom(room1Name);
         undo.field(UNDO_ADULT, adultMovingTo1, COLUMN_ROOM, room2Name);
      }

      this.data.getRooms().store();
      this.data.getScouts().store();
      this.data.getAdults().store();
      undo.commit("Change room " + room1Name + " <-> " + room2Name);
   }
}
