package shkc.core;

/**
 * Serves the pre-registered youth (scouts_scheduled.csv), as the
 * {@code /youth-scheduled-cells} endpoint. See {@link RecordCellsHandler} for
 * the request parameters; a birthdate and a youth's phone number are withheld.
 */
public class ScoutsScheduledCellsHandler extends RecordCellsHandler<ScoutRecord> {
   public ScoutsScheduledCellsHandler(EventData data) {
      super(data, data.getScoutsScheduled());
   }
}
