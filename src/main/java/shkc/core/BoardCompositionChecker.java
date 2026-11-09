package shkc.core;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Enforces the board composition rules on the server.
 *
 * <p>The rules are enforced here as well as in {@code process_seat.js}. The
 * browser is the normal way in, not the only one, and a board seated past the
 * UI is a board that never met the rule, which is only discovered later, from
 * the record of a review that should not have happened. The two must agree.
 *
 * <p>The checker needs only the adults file, so it can be tested without a web
 * server.
 */
public class BoardCompositionChecker {
   /**
    * A board of review has no fewer than three members (Guide to Advancement
    * 8.0.0.3). Keep in step with {@code checkBoardSize()} in process_seat.js.
    */
   public static final int MIN_BOARD_OF_REVIEW_MEMBERS = 3;

   /**
    * A project proposal review is not a board of review (Guide to Advancement
    * 9.0.2.4) and this district runs it with two. Keep in step with
    * {@code checkProjectSize()} in process_seat.js.
    */
   public static final int MIN_PROJECT_PROPOSAL_MEMBERS = 2;

   /**
    * No board has more than six members (Guide to Advancement 8.0.0.3); a
    * project proposal review stays under the same ceiling.
    */
   public static final int MAX_BOARD_MEMBERS = 6;

   private final DataRecordFile<AdultRecord> adults;

   public BoardCompositionChecker(DataRecordFile<AdultRecord> adults) {
      this.adults = adults;
   }

   /**
    * Checks the board a request asks for.
    *
    * @param scout     the youth the board is for
    * @param chairId   the ID of the adult who will chair
    * @param memberIds the IDs of the adults who will sit, comma separated
    * @param boardRoom the room this board already sits in, when changing the
    *                  members of a seated board (adults already in it may stay);
    *                  {@code null} when seating a new board, where every member must be free
    * @return the accepted board, or a refusal carrying the message to show
    */
   public BoardComposition check(ScoutRecord scout, String chairId, String memberIds, String boardRoom) {
      if (memberIds == null || memberIds.trim().length() == 0) {
         // No board members: reject cleanly instead of crashing on
         // StringTokenizer(null) or silently seating an empty board.
         return BoardComposition.refuse("ERROR: No board members selected");
      }

      AdultRecord chair = this.adults.get(chairId);
      List<AdultRecord> members = new ArrayList<>();
      StringBuilder memberNames = new StringBuilder();
      StringTokenizer memberIdTokens = new StringTokenizer(memberIds, ",", false);

      while (memberIdTokens.hasMoreTokens()) {
         String memberId = memberIdTokens.nextToken().trim();
         AdultRecord member = this.adults.get(memberId);
         if (member == null) {
            return BoardComposition.refuse("ERROR: Invalid Member ID " + memberId);
         }

         String memberRefusal = this.refusalForMember(member, scout, boardRoom, members);
         if (memberRefusal != null) {
            return BoardComposition.refuse(memberRefusal);
         }

         if (memberNames.length() > 0) {
            memberNames.append(",");
         }

         // Full name, not getShortName()'s "F. Last": the room
         // card is how someone looks up which room an adult is
         // in, and initial-only made that lookup by first name
         // impossible without already knowing their last name.
         memberNames.append(member.getFullName());
         members.add(member);
      }

      boolean isProjectBoard = BoardType.PROJECT.equals(scout.getBoardType());

      String sizeRefusal = this.refusalForSize(members.size(), isProjectBoard, scout.getBoardType());
      if (sizeRefusal != null) {
         return BoardComposition.refuse(sizeRefusal);
      }

      String chairRefusal = this.refusalForChair(chair, chairId, members, isProjectBoard, scout.getBoardType());
      if (chairRefusal != null) {
         return BoardComposition.refuse(chairRefusal);
      }

      String unitRefusal = this.refusalForUnit(scout, members, boardRoom != null);
      if (unitRefusal != null) {
         return BoardComposition.refuse(unitRefusal);
      }

      return BoardComposition.accepted(members, chair, memberNames.toString());
   }

   /**
    * Why a board made only of the youth's own unit is refused, or {@code null}
    * when someone on it is from outside the unit.
    *
    * <p>Guide to Advancement 8.0.3.0 #2 (SPEC.md D-4): the council's
    * no-same-unit rule is only a warning the operator may override, but a board
    * still needs one member who is not from the youth's unit. The Event page
    * blocks it as the board is built ({@code hasNonUnitMember} in
    * process_seat.js); this answers a request that never went through the
    * page. The units are compared as the page compares them, by the stored
    * {@code UnitName}, and a blank unit counts as outside: it is unknown, not
    * proof of affiliation.
    *
    * @param changing true when the members of a seated board are being changed,
    *                 so the refusal says who is left on the board
    */
   private String refusalForUnit(ScoutRecord scout, List<AdultRecord> members, boolean changing) {
      String scoutUnit = scout.getValue("UnitName");

      if (scoutUnit.isEmpty()) {
         return null;
      }

      for (AdultRecord member : members) {
         String memberUnit = member.getValue("UnitName");

         if (memberUnit.isEmpty() || !memberUnit.equals(scoutUnit)) {
            return null;
         }
      }

      return "ERROR: Everyone " + (changing ? "left on" : "on") + " the board is from the youth's unit, "
         + "and a board needs at least one member from outside it (Guide to Advancement 8.0.3.0). "
         + "Add someone from another unit.";
   }

   /**
    * Why this one member cannot sit, or {@code null} if they can.
    *
    * @param listedSoFar the members already accepted for this board
    */
   private String refusalForMember(AdultRecord member, ScoutRecord scout, String boardRoom, List<AdultRecord> listedSoFar) {
      // "N/A" is the Disable button's marker for an adult who
      // has gone home, not a room anyone can be sent to, so it
      // gets its own wording instead of "in room N/A".
      if (RoomMarker.NO_ROOM.equals(member.getRoom())) {
         return "ERROR: Member " + member.getFullName() + " has been disabled for today";
      }

      if (member.getRoom().length() > 0 && !member.getRoom().equals(boardRoom)) {
         return "ERROR: Member " + member.getFullName() + " already assigned to a board in room " + member.getRoom();
      }

      // "No thanks" to this kind of board at sign-in is stored as
      // Unavailable for it. Auto-select and the grid skip them, but
      // a hand-picked or hand-built board must not seat them either.
      boolean isProjectBoard = BoardType.PROJECT.equals(scout.getBoardType());
      if (AdultRole.UNAVAILABLE.equals(roleForBoard(member, isProjectBoard))) {
         return "ERROR: Member " + member.getFullName() + " is Unavailable for " + scout.getBoardType() + " boards";
      }

      // The size rules count entries, so the same adult
      // listed twice would let two people pass as a board of
      // three. The UI's checkboxes cannot produce this; a
      // hand-built or replayed request can.
      if (listedSoFar.contains(member)) {
         return "ERROR: Member " + member.getFullName() + " is listed more than once";
      }
      return null;
   }

   /**
    * Why this many members is the wrong size, or {@code null} if it is fine.
    *
    * <p>Guide to Advancement 8.0.0.3: a board of review has no fewer than three
    * and no more than six members. A project proposal review is not a board of
    * review (GTA 9.0.2.4) and this district runs it with two, under the same
    * ceiling.
    */
   private String refusalForSize(int memberCount, boolean isProjectBoard, String boardType) {
      int minMembers = isProjectBoard ? MIN_PROJECT_PROPOSAL_MEMBERS : MIN_BOARD_OF_REVIEW_MEMBERS;

      if (memberCount < minMembers) {
         return "ERROR: Only " + memberCount + " board member(s) selected; "
            + minMembers + " required for " + boardType + " boards";
      }

      if (memberCount > MAX_BOARD_MEMBERS) {
         return "ERROR: " + memberCount
            + " board members selected; no more than " + MAX_BOARD_MEMBERS + " permitted (Guide to Advancement 8.0.0.3)";
      }
      return null;
   }

   /**
    * Why this chair is not acceptable, or {@code null} if they are.
    *
    * <p>The Chair designation is binding. Promoting someone from
    * Member to Chair is a deliberate edit on the Admin page; it
    * must never happen as a side effect of seating a board
    * because the qualified chairs were all busy.
    */
   private String refusalForChair(AdultRecord chair, String chairId, List<AdultRecord> members, boolean isProjectBoard,
      String boardType) {
      if (chair == null) {
         return "ERROR: Invalid Chair ID " + chairId;
      }

      boolean chairIsMember = false;

      for (AdultRecord candidate : members) {
         if (candidate.getID().equals(chair.getID())) {
            chairIsMember = true;
            break;
         }
      }

      if (!chairIsMember) {
         return "ERROR: Chair " + chair.getFullName() + " is not one of the board members";
      }

      String chairRole = roleForBoard(chair, isProjectBoard);

      if (!AdultRole.CHAIR.equals(chairRole)) {
         return "ERROR: " + chair.getFullName() + " is not qualified to chair a "
            + boardType + " board (role: "
            + (chairRole == null || chairRole.length() == 0 ? "none" : chairRole) + ")";
      }
      return null;
   }

   /** The adult's sign-in answer for the kind of board being seated. */
   private static String roleForBoard(AdultRecord adult, boolean isProjectBoard) {
      return isProjectBoard ? adult.getProjectReviewRole() : adult.getFinalBoardRole();
   }
}
