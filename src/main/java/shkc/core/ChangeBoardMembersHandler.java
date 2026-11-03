package shkc.core;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles {@code /change-board-members}: swaps who sits on a board that is
 * already seated or in review: someone has to leave, or the chair changes
 * hands.
 *
 * <p>Request parameters: {@code ScoutID}, {@code ChairID} and {@code MemberIDs}
 * (comma separated, the chair among them). The youth must be {@code Seated} or
 * {@code InProgress}. Answers {@code OK.} (status 200), or the reason for the
 * refusal (status 304). The change is undoable.
 *
 * <p>The same composition rules as seating apply, except that the adults
 * already in the room may stay. Those who leave are freed; those who join are
 * put in the room. The timer keeps running (LastUpdateTime is not touched): it
 * is the same board, and the Windows and Mac versions agree.
 */
public class ChangeBoardMembersHandler extends BoardHandler {
   public ChangeBoardMembersHandler(EventData data) {
      super(data);
   }

   @Override
   public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      synchronized (this.data.getLock()) {
         this.logRequest(request);
         String scoutId = request.getParameter("ScoutID");
         String chairId = request.getParameter("ChairID");
         String memberIds = request.getParameter("MemberIDs");

         ScoutRecord scout = this.data.getScouts().get(scoutId);
         if (scout == null) {
            this.sendError("ERROR: Invalid Scout ID" + scoutId, response);
            return;
         }

         if (!ScoutStatus.holdsRoom(scout.getStatus())) {
            this.sendError("ERROR: " + scout.getFullName() + " has no board seated or in review to change", response);
            return;
         }

         // By name, or (for a room renamed under the board) the card
         // that still names the scout (see CompleteBoardHandler).
         RoomRecord room = this.data.findRoomOfBoard(scout);
         if (room == null) {
            this.sendError("ERROR: The room for " + scout.getFullName() + "'s board is gone; Reset the board and seat it again", response);
            return;
         }

         String boardRoom = scout.getRoom();
         BoardComposition board = new BoardCompositionChecker(this.data.getAdults()).check(scout, chairId, memberIds, boardRoom);
         if (board.isRefused()) {
            this.sendError(board.getError(), response);
            return;
         }

         this.changeMembers(scout, room, boardRoom, board);
         this.sendSuccess(response);
      }
   }

   /**
    * Frees the adults who left, puts the new ones in the room, rewrites the
    * room card and the youth's board columns, and commits the change to the
    * Undo log.
    */
   private void changeMembers(ScoutRecord scout, RoomRecord room, String boardRoom, BoardComposition board) throws IOException {
      UndoLog.Builder undo = this.data.getUndoLog().begin();
      for (AdultRecord adult : this.data.getAdults().getRecords()) {
         if (adult.getRoom().equals(boardRoom) && !board.getMembers().contains(adult)) {
            String oldAdultRoom = adult.getRoom();
            adult.setRoom("");
            undo.field("Adult", adult, "Room", oldAdultRoom);
         }
      }

      StringBuilder memberIdList = new StringBuilder();
      for (AdultRecord member : board.getMembers()) {
         if (!boardRoom.equals(member.getRoom())) {
            String oldMemberRoom = member.getRoom();
            member.setRoom(boardRoom);
            undo.field("Adult", member, "Room", oldMemberRoom);
         }
         if (memberIdList.length() > 0) {
            memberIdList.append(",");
         }
         memberIdList.append(member.getID());
      }

      String oldRoomLeaders = room.getLeaders();
      String oldBoardMembers = scout.getValue("BoardMembers");
      String oldBoardMemberIDs = scout.getValue("BoardMembersIDs");
      String oldBoardChair = scout.getValue("BoardChair");
      String oldBoardChairID = scout.getValue("BoardChairID");
      room.setLeaders(board.getMemberNames());
      scout.setBoardMembers(board.getMemberNames());
      scout.setBoardMemberIDs(memberIdList.toString());
      scout.setBoardChair(board.getChair().getFullName());
      scout.setBoardChairID(board.getChair().getID());
      undo.field("Room", room, "Leaders", oldRoomLeaders);
      undo.field("Scout", scout, "BoardMembers", oldBoardMembers);
      undo.field("Scout", scout, "BoardMembersIDs", oldBoardMemberIDs);
      undo.field("Scout", scout, "BoardChair", oldBoardChair);
      undo.field("Scout", scout, "BoardChairID", oldBoardChairID);

      // updateFields(false): refresh the derived columns without
      // stamping LastUpdateTime, which would restart the room timer.
      scout.updateFields(false);
      this.data.getScouts().store();
      this.data.getRooms().store();
      this.data.getAdults().store();
      undo.commit("Change board members for " + scout.getFullName());
   }
}
