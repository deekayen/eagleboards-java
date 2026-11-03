package shkc.core;

/**
 * Applies an edit made in the Admin page's Rooms table, as the
 * {@code /room-update} endpoint. See {@link RecordUpdateHandler} for the
 * request and the answer. Field edits go on the Undo stack as {@code Room}
 * records.
 */
public class RoomUpdateHandler extends RecordUpdateHandler<RoomRecord> {
   public RoomUpdateHandler(EventData data) {
      super(data, data.getRooms(), "Room");
   }
}
