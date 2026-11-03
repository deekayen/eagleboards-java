package shkc.core;

/**
 * Serves today's youth records (scouts.csv), as the {@code /youth-cells}
 * endpoint. See {@link RecordCellsHandler} for the request parameters; a
 * birthdate and a youth's phone number are withheld.
 */
public class ScoutCellsHandler extends RecordCellsHandler<ScoutRecord> {
   public ScoutCellsHandler(EventData data) {
      super(data, data.getScouts());
   }
}
