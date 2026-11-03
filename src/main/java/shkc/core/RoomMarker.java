package shkc.core;

/**
 * The marker {@code "N/A"} that the {@code Room} column holds when a person has
 * no room to be in.
 *
 * <p>For an adult it is the Disable button's mark for someone who has gone
 * home, so they are skipped by auto-select and refused by Seat Board. For a
 * youth it is what Complete leaves in {@code Room} once the review is over.
 * Because it means "nobody", no real room may be named this, and renaming a
 * room to it is refused.
 */
public final class RoomMarker {
   /** The {@code Room} value meaning "gone home" (adult) or "review finished" (youth). */
   public static final String NO_ROOM = "N/A";

   private RoomMarker() {
   }
}
