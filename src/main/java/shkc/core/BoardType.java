package shkc.core;

/**
 * The values of a youth's {@code BoardType} column. A Life-to-Eagle youth
 * whose project proposal is being reviewed has {@link #PROJECT}; a board of
 * review has {@link #FINAL_BOARD}. The two have different size and chair rules
 * (see {@link BoardCompositionChecker}).
 */
public final class BoardType {
   /** A project proposal review (Guide to Advancement 9.0.2.4). */
   public static final String PROJECT = "Project";

   /** The Eagle Scout board of review itself (Guide to Advancement 8.0.0.3). */
   public static final String FINAL_BOARD = "Final";

   private BoardType() {
   }
}
