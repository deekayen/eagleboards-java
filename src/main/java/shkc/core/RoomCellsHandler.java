package shkc.core;

/**
 * Serves the room records (rooms.csv) to the Admin and Event pages, as the
 * {@code /room-cells} endpoint. See {@link RecordCellsHandler} for the
 * request parameters.
 */
public class RoomCellsHandler extends RecordCellsHandler<RoomRecord> {
   public RoomCellsHandler(EventData data) {
      super(data, data.getRooms());
   }
}
