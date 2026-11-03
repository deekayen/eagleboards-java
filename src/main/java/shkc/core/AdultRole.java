package shkc.core;

/**
 * What an adult says at sign-in about each kind of board, stored in the
 * {@code ProjectReview} and {@code FinalBoard} columns of {@link AdultRecord}.
 */
public final class AdultRole {
   /** Willing to sit on this kind of board. */
   public static final String MEMBER = "Member";

   /** Qualified and willing to chair this kind of board. */
   public static final String CHAIR = "Chair";

   /** Answered "No thanks" to this kind of board; Seat Board refuses them for it. */
   public static final String UNAVAILABLE = "Unavailable";

   private AdultRole() {
   }
}
