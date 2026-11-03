package shkc.core;

/**
 * Serves the adult history (every adult who has ever signed in), as the
 * {@code /adult-history-cells} endpoint. See {@link RecordCellsHandler} for
 * the request parameters.
 */
public class AdultHistoryCellsHandler extends RecordCellsHandler<AdultRecord> {
   public AdultHistoryCellsHandler(EventData data) {
      super(data, data.getAdultHistory());
   }
}
