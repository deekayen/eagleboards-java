package shkc.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The answer to "may this board be seated?" for a /seat-board or
 * /change-board-members request, produced by {@link BoardCompositionChecker}.
 *
 * <p>Either the request is refused, in which case {@link #getError()} holds the
 * message to show the operator, or it is accepted and {@link #getMembers()},
 * {@link #getChair()} and {@link #getMemberNames()} describe the board. Build
 * one with {@link #refuse(String)} or {@link #accepted(List, AdultRecord, String)}.
 */
public final class BoardComposition {
   private final String error;
   private final List<AdultRecord> members;
   private final AdultRecord chair;
   private final String memberNames;

   private BoardComposition(String error, List<AdultRecord> members, AdultRecord chair, String memberNames) {
      this.error = error;
      this.members = members;
      this.chair = chair;
      this.memberNames = memberNames;
   }

   /** A refused board: only {@link #getError()} is meaningful. */
   public static BoardComposition refuse(String message) {
      return new BoardComposition(message, Collections.emptyList(), null, null);
   }

   /**
    * An accepted board.
    *
    * @param members     the adults sitting on it, the chair among them
    * @param chair       the adult who chairs it
    * @param memberNames the members' full names, comma separated, as the room card and the youth's record show them
    */
   public static BoardComposition accepted(List<AdultRecord> members, AdultRecord chair, String memberNames) {
      return new BoardComposition(null, new ArrayList<>(members), chair, memberNames);
   }

   /** Why the board was refused, or {@code null} when it was accepted. */
   public String getError() {
      return this.error;
   }

   /** Whether the board was refused. */
   public boolean isRefused() {
      return this.error != null;
   }

   /** The adults on the board; empty when refused. */
   public List<AdultRecord> getMembers() {
      return this.members;
   }

   /** The chair; {@code null} when refused. */
   public AdultRecord getChair() {
      return this.chair;
   }

   /** The members' full names, comma separated; {@code null} when refused. */
   public String getMemberNames() {
      return this.memberNames;
   }
}
