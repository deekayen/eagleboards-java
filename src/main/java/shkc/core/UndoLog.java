package shkc.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The single level of Undo for the Event page (SPEC.md O-2).
 *
 * <p>Each reversible handler calls {@link #begin()}, records the OLD value of
 * every field it is about to change, and calls {@link Builder#commit(String)}
 * (which captures the NEW value it just wrote) right before sending success.
 * A commit replaces whatever the previous action left, so there is only ever
 * one action's worth of undo. The restore handler re-checks each field's
 * current value against what the action wrote before touching anything: if
 * something else changed it since (another request, or a hand-edit on the
 * Admin page), that field, and the whole restore, is refused, not
 * guessed at.
 *
 * <p>This class is not thread-safe by itself. Callers hold the event lock
 * ({@link EventData#getLock()}) around every use, like everything else that
 * touches the event's records.
 */
public class UndoLog {
   private String description = null;
   private List<UndoField> fields = null;

   /** Starts recording an action. Nothing is kept until {@link Builder#commit(String)}. */
   public Builder begin() {
      return new Builder();
   }

   /** The fields the last committed action changed; {@code null} when there is nothing to undo. */
   public List<UndoField> fields() {
      return this.fields;
   }

   /** Whether there is nothing to undo, either because nothing was committed or it was cleared. */
   public boolean isEmpty() {
      return this.fields == null || this.fields.isEmpty();
   }

   /** A short description of the last committed action, such as "Seat board for Pat Smith"; {@code null} when empty. */
   public String description() {
      return this.description;
   }

   /** Forgets the last action, as after a successful restore. */
   public void clear() {
      this.fields = null;
      this.description = null;
   }

   /** Collects the fields one action changes, then hands them to the log on {@link #commit(String)}. */
   public final class Builder {
      private final List<UndoField> recorded = new ArrayList<>();

      /**
       * Records one changed field. Call this AFTER the record's setter has run,
       * so the new value read here is the one the handler just wrote.
       *
       * @param recordType {@code "Scout"}, {@code "Room"} or {@code "Adult"}
       * @param record     the record that was changed
       * @param column     the column that was changed
       * @param oldValue   the column's value before the change
       */
      public Builder field(String recordType, DataRecord record, String column, String oldValue) {
         this.recorded.add(new UndoField(recordType, record.getID(), column, oldValue, record.getValue(column)));
         return this;
      }

      /** Whether no field has been recorded yet. */
      public boolean isEmpty() {
         return this.recorded.isEmpty();
      }

      /** Makes this the action Undo reverses, replacing whatever was there. */
      public void commit(String description) {
         UndoLog.this.description = description;
         UndoLog.this.fields = this.recorded;
      }
   }
}
