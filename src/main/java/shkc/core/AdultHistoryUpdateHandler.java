package shkc.core;

import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code /adult-history-update}: answers an edit made on the Admin page's
 * Adult history tab. That tab is read-only, so every edit is refused with the
 * reason.
 */
public class AdultHistoryUpdateHandler extends RecordUpdateHandler<AdultRecord> {
   public AdultHistoryUpdateHandler(EventData data) {
      super(data, data.getAdultHistory());
   }

   // SPEC.md P-6: the Adult history CSV is read-only. A sign-in writes it,
   // and an edit on the Adults tab reaches it (AdultUpdateHandler).
   @Override
   protected String refusal(DataRecord record, HttpServletRequest request, String editStatus) {
      return "The adult history is read-only. Change an adult on the Adults tab, and the history follows.";
   }
}
