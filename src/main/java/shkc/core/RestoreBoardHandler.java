package shkc.core;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles {@code /restore-board}: the Event page's Undo (SPEC.md O-2). It
 * reverses the last reversible action: Seat, Start Review, Complete, a room
 * change, or an adult Disable/Enable/Link. No request parameters.
 *
 * <p>Reset and Postpone never commit to the {@link UndoLog} (they stay
 * confirm-only), so there is nothing to check for those. This is a generic
 * "revert the last snapshot": it refuses if anything else has changed a value
 * since instead of guessing, and refuses the whole restore if any field
 * fails so a board is never left half-undone.
 *
 * <p>Answers {@code OK.} (status 200) once every field is put back, or the
 * reason for the refusal (status 304).
 */
public class RestoreBoardHandler extends BoardHandler {
   private static final String CANNOT_UNDO_MESSAGE = "Board changed since -- cannot undo automatically.";

   public RestoreBoardHandler(EventData data) {
      super(data);
   }

   @Override
   public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      synchronized (this.data.getLock()) {
         UndoLog undoLog = this.data.getUndoLog();
         List<UndoField> fields = undoLog.fields();
         if (fields == null || fields.isEmpty()) {
            this.sendError("Nothing to undo.", response);
            return;
         }

         Map<String, DataRecord> touched = this.findRecordsToRestore(fields);
         if (touched == null) {
            this.sendError(CANNOT_UNDO_MESSAGE, response);
            return;
         }

         // Nothing has been changed yet: every field passed its check above,
         // so putting the old values back cannot leave a board half-undone.
         Set<String> touchedRecordTypes = new HashSet<>();
         for (UndoField field : fields) {
            DataRecord record = touched.get(recordKey(field));
            record.setValue(field.column(), field.oldValue());
            touchedRecordTypes.add(field.recordType());
         }

         this.storeFilesOf(touchedRecordTypes);
         undoLog.clear();
         this.sendSuccess(response);
      }
   }

   /**
    * Looks up the record of every field and checks that it still holds the
    * value the undone action wrote.
    *
    * @return the records, keyed by {@link #recordKey(UndoField)}; {@code null}
    *         when a record is gone or a field has been changed since, in which
    *         case nothing may be restored
    */
   private Map<String, DataRecord> findRecordsToRestore(List<UndoField> fields) {
      Map<String, DataRecord> touched = new HashMap<>();
      for (UndoField field : fields) {
         DataRecordFile<? extends DataRecord> file = this.data.recordsFor(field.recordType());
         DataRecord record = file == null ? null : file.get(field.id());
         if (record == null) {
            return null;
         }

         // Something else (another request, or a hand-edit on the
         // Admin page) changed this field after this action wrote it:
         // restoring the OLD value now would silently clobber that
         // change, so refuse the whole restore instead.
         String current = record.getValue(field.column());
         if (!Objects.equals(current, field.newValue())) {
            return null;
         }

         touched.put(recordKey(field), record);
      }
      return touched;
   }

   /** A key that tells apart records of different files that share an ID. */
   private static String recordKey(UndoField field) {
      return field.recordType() + ":" + field.id();
   }

   /** Saves each file that holds a restored record, and no other. */
   private void storeFilesOf(Set<String> recordTypes) throws IOException {
      if (recordTypes.contains("Scout")) {
         this.data.getScouts().store();
      }
      if (recordTypes.contains("Room")) {
         this.data.getRooms().store();
      }
      if (recordTypes.contains("Adult")) {
         this.data.getAdults().store();
      }
   }
}
