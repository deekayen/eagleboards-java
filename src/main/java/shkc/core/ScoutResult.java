package shkc.core;

/**
 * The three decisions a board can record in a youth's {@code Result} column
 * when it completes a review. "Postponed" is deliberately not one of them: it
 * is a {@link ScoutStatus} for a youth who never sees a board.
 */
public final class ScoutResult {
   /** The board approved the youth. */
   public static final String APPROVED = "Approved";

   /** The board met the youth and postponed its decision; the youth may come back. */
   public static final String ADJOURNED = "Adjourned";

   /** The board denied the youth. */
   public static final String NOT_APPROVED = "NotApproved";

   private ScoutResult() {
   }
}
