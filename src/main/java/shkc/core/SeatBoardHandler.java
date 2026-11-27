package shkc.core;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles {@code /seat-board}: convenes a board for a waiting youth.
 *
 * <p>Request parameters: {@code RoomID} (the room card to use), {@code ScoutID},
 * {@code ChairID} and {@code MemberIDs} (comma separated, the chair among them).
 *
 * <p>Answers {@code OK.} (status 200) once the youth is {@code Seated}, or the
 * reason for the refusal (status 304). The board's composition is checked by
 * {@link BoardCompositionChecker}, so a request that never went through the
 * Event page cannot seat a board the rules forbid.
 *
 * <p>Seating convenes the board only: the members get the room to go over the
 * application, references, and project workbook BEFORE the youth is called in.
 * {@link InProgressBoardHandler} ("Start Review") admits the youth and makes the
 * board active.
 */
public class SeatBoardHandler extends BoardHandler {
   public SeatBoardHandler(EventData data) {
      super(data);
   }

   @Override
   public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      synchronized (this.data.getLock()) {
         this.logRequest(request);
         String roomId = request.getParameter("RoomID");
         String scoutId = request.getParameter("ScoutID");
         String chairId = request.getParameter("ChairID");
         String memberIds = request.getParameter("MemberIDs");
         Log.verbose("RoomID: " + roomId);
         Log.verbose("ScoutID: " + scoutId);
         Log.verbose("ChairID: " + chairId);
         Log.verbose("MemberIDs: " + memberIds);
         Log.info("seat-board: attempt scout=" + Log.safeValue("ScoutID", scoutId) + " roomid=" + Log.safeValue("RoomID", roomId)
            + " chair=" + Log.safeValue("ChairID", chairId) + " members=" + Log.clean(memberIds));

         RoomRecord room = this.data.getRooms().get(roomId);
         if (room == null) {
            Log.info("seat-board: room card not found, roomid=" + Log.safeValue("RoomID", roomId)
               + " rooms on file=" + this.data.getRooms().getRecords().size());
            this.sendError("ERROR: Invalid Room ID" + roomId, response);
            return;
         }
         if (room.getScout().length() > 0) {
            Log.info("seat-board: room=" + room.getRoom() + " is occupied by scout='" + room.getScout()
               + "' leaders='" + room.getLeaders() + "'");
            this.sendError("ERROR: Room " + room.getRoom() + "is already assigned to " + room.getScout(), response);
            return;
         }

         ScoutRecord scout = this.data.getScouts().get(scoutId);
         if (scout == null) {
            Log.info("seat-board: scout not found, scout=" + Log.safeValue("ScoutID", scoutId) + " room=" + room.getRoom()
               + " scouts on file=" + this.data.getScouts().getRecords().size());
            this.sendError("ERROR: Invalid Scout ID" + scoutId, response);
            return;
         }
         String scoutRefusal = this.refusalForScout(scout, room);
         if (scoutRefusal != null) {
            Log.info("seat-board: scout=" + logScout(scout) + " type=" + scout.getBoardType() + " status=" + scout.getStatus()
               + " scoutroom=" + scout.getRoom() + " wanted room=" + room.getRoom());
            this.sendError(scoutRefusal, response);
            return;
         }

         BoardComposition board = new BoardCompositionChecker(this.data.getAdults()).check(scout, chairId, memberIds, null);
         if (board.isRefused()) {
            this.sendError(board.getError(), response);
            return;
         }

         this.seat(room, scout, board, memberIds);
         this.sendSuccess(response);
      }
   }

   /**
    * Why this youth may not be seated in this room, or {@code null} when the
    * youth may be.
    */
   private String refusalForScout(ScoutRecord scout, RoomRecord room) {
      // Verify was removed, so a Registered scout is seated directly.
      // "Verified" is still accepted for legacy records carried over
      // from a run made before that change.
      if (!ScoutStatus.REGISTERED.equals(scout.getStatus()) && !ScoutStatus.VERIFIED.equals(scout.getStatus())) {
         return "ERROR: Invalid Status '" + scout.getStatus() + "', expected '" + ScoutStatus.REGISTERED + "'";
      }

      // "N/A" is what Complete leaves in a scout's Room. A Registered
      // scout holding it is one whose result was recorded against them
      // by mistake and set back to Registered on the Admin page; they
      // have no room, and must be seatable for their real board.
      String scoutRoom = scout.getRoom();
      if (!"".equals(scoutRoom) && !RoomMarker.NO_ROOM.equals(scoutRoom) && !scoutRoom.equals(room.getRoom())) {
         return "ERROR: Scout Already Assigned Room: " + scoutRoom;
      }
      return null;
   }

   /**
    * Puts the accepted board in the room: the room card, the youth's record
    * and each member are updated and saved, and the change is committed to
    * the Undo log.
    */
   private void seat(RoomRecord room, ScoutRecord scout, BoardComposition board, String memberIds) throws IOException {
      AdultRecord chair = board.getChair();
      List<AdultRecord> members = board.getMembers();
      String memberNameList = board.getMemberNames();
      String oldRoomScout = room.getScout();
      String oldRoomLeaders = room.getLeaders();
      String oldScoutRoom = scout.getRoom();
      String oldScoutStatus = scout.getStatus();
      String oldBoardMembers = scout.getValue("BoardMembers");
      String oldBoardMemberIDs = scout.getValue("BoardMembersIDs");
      String oldBoardChair = scout.getValue("BoardChair");
      String oldBoardChairID = scout.getValue("BoardChairID");

      room.setScout(scout.getFullName());
      room.setLeaders(memberNameList);
      scout.setRoom(room.getRoom());
      // Seating convenes the board only: the members get the room
      // to go over the application, references, and project workbook
      // BEFORE the scout is called in. "Start Review" (the
      // /inprogress-board endpoint) is what admits the scout and
      // makes the board active. Seat and Start were merged once, on
      // the view that the second step was redundant; separating
      // them again is what lets the two phases be timed apart:
      // the preview is capped, the interview has its own window.
      scout.setStatus(ScoutStatus.SEATED);
      scout.setBoardMembers(memberNameList);
      scout.setBoardMemberIDs(memberIds);
      if (chair != null) {
         scout.setBoardChair(chair.getFullName());
         scout.setBoardChairID(chair.getID());
      }

      UndoLog.Builder undo = this.data.getUndoLog().begin();
      undo.field("Room", room, "Scout", oldRoomScout);
      undo.field("Room", room, "Leaders", oldRoomLeaders);
      undo.field("Scout", scout, "Room", oldScoutRoom);
      undo.field("Scout", scout, "Status", oldScoutStatus);
      undo.field("Scout", scout, "BoardMembers", oldBoardMembers);
      undo.field("Scout", scout, "BoardMembersIDs", oldBoardMemberIDs);
      undo.field("Scout", scout, "BoardChair", oldBoardChair);
      undo.field("Scout", scout, "BoardChairID", oldBoardChairID);

      for (AdultRecord seatedMember : members) {
         String oldMemberRoom = seatedMember.getRoom();
         seatedMember.setRoom(room.getRoom());
         undo.field("Adult", seatedMember, "Room", oldMemberRoom);
      }

      scout.updateFields(true);
      this.data.getScouts().store();
      this.data.getRooms().store();
      this.data.getAdults().store();
      undo.commit("Seat board for " + scout.getFullName());

      boolean isProjectBoard = BoardType.PROJECT.equals(scout.getBoardType());
      Log.info("seat-board: scout=" + logScout(scout) + " room=" + room.getRoom() + " type=" + scout.getBoardType()
         + " (" + (isProjectBoard ? "project proposal" : "final board") + ")"
         + " chair=" + (chair == null ? "(none)" : Log.clean(chair.getID())) + " members=" + logAdultIds(members)
         + " -> seated: status " + oldScoutStatus + " -> " + scout.getStatus()
         + ", scout room '" + oldScoutRoom + "' -> " + scout.getRoom()
         + ", room " + room.getRoom() + ": " + (oldRoomScout.isEmpty() ? "free" : "held by '" + oldRoomScout + "'") + " -> occupied"
         + ", committed " + members.size() + " adult(s) to the room");
   }
}
