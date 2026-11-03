package shkc.core;

/**
 * The values a youth's {@code Status} column can hold, in the order a youth
 * normally moves through them: registered at sign-in, seated with a board,
 * in progress, then completed. The same words are written to the CSV file and
 * are read by the browser pages, so they must not be changed.
 *
 * <p>Only the constants and {@link #holdsRoom(String)} live here; the status
 * itself is stored on the {@link ScoutRecord}.
 */
public final class ScoutStatus {
   /** Signed in and waiting for a board. */
   public static final String REGISTERED = "Registered";

   /** Survives on legacy records only; nothing sets it any more. */
   public static final String VERIFIED = "Verified";

   /** The board has the room and is going over the paperwork; the youth is still outside. */
   public static final String SEATED = "Seated";

   /** The youth has been brought in and the review is under way. */
   public static final String IN_PROGRESS = "InProgress";

   /** The board has recorded its decision (see {@link ScoutResult}). */
   public static final String COMPLETED = "Completed";

   /** Arrived unprepared and was sent away without ever seeing a board. */
   public static final String POSTPONED = "Postponed";

   private ScoutStatus() {
   }

   /**
    * Whether a board is sitting for a youth with this status, which means the
    * youth holds a room and its members are committed to it.
    */
   public static boolean holdsRoom(String status) {
      return SEATED.equals(status) || IN_PROGRESS.equals(status);
   }
}
