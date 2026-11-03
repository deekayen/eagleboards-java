package shkc.core;

/**
 * Serves today's adult records (adults.csv), as the {@code /adult-cells}
 * endpoint. See {@link RecordCellsHandler} for the request parameters.
 */
public class AdultCellsHandler extends RecordCellsHandler<AdultRecord> {
   public AdultCellsHandler(EventData data) {
      super(data, data.getAdults());
   }
}
