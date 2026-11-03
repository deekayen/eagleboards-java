package shkc.core;

/**
 * {@code /youth-scheduled-update}: applies an edit made on the Admin page's
 * tab of pre-registered youth (the ones who reserved a slot on SignUpGenius).
 * Nothing special applies to this file, so the base class does all the work.
 */
public class ScoutsScheduledUpdateHandler extends RecordUpdateHandler<ScoutRecord> {
   public ScoutsScheduledUpdateHandler(EventData data) {
      super(data, data.getScoutsScheduled());
   }
}
