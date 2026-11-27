package shkc.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Applies an edit made in an Admin table, as the {@code /xxx-update} endpoints.
 *
 * <p>The request is the grid's: {@code !nativeeditor_status} says whether the
 * row was {@code inserted}, {@code updated} or {@code deleted}, {@code gr_id}
 * is the row's ID, and every column of the record may arrive as a parameter of
 * its own name. The answer is a small XML {@code <action>} element naming the
 * status back, or {@code invalid} when the edit was not made.
 *
 * <p>Subclasses change what is allowed or what follows an edit through three
 * hooks: {@link #refusal}, {@link #keepsOffUndo} and {@link #afterEdit}.
 *
 * @param <T> the kind of record in the file
 */
public class RecordUpdateHandler<T extends DataRecord> implements WebServer.WebHandler {
   /** The grid's status for a row the operator added. */
   protected static final String EDIT_INSERTED = "inserted";

   /** The grid's status for a row the operator changed. */
   protected static final String EDIT_UPDATED = "updated";

   /** The grid's status for a row the operator removed. */
   protected static final String EDIT_DELETED = "deleted";

   private static final String EDIT_INVALID = "invalid";

   /** Every line this handler writes to the event log starts with this, so one grep finds the Admin edits. */
   private static final String LOG_OPERATION = "admin-edit";

   private static final String HANDLER_SUFFIX = "UpdateHandler";

   /** The record kind in log lines: the handler's name without "UpdateHandler" (Scout, Adult, Room...). */
   private final String kind;

   private final EventData data;
   private final DataRecordFile<T> records;
   // Non-null only for the handlers O-2 covers (adult field edits,
   // that is Disable/Enable/Link, and room rename); null everywhere else
   // (scout/room-admin edits, adult/scout-scheduled history), which
   // offer no Undo. See UndoLog.
   private final String undoRecordType;

   /** A handler whose edits are not put on the Undo stack. */
   public RecordUpdateHandler(EventData data, DataRecordFile<T> records) {
      this(data, records, null);
   }

   /**
    * @param undoRecordType {@code "Scout"}, {@code "Room"} or {@code "Adult"} to
    *                       put this handler's field edits on the Undo stack,
    *                       or {@code null} to keep them off it
    */
   public RecordUpdateHandler(EventData data, DataRecordFile<T> records, String undoRecordType) {
      this.data = data;
      this.records = records;
      this.undoRecordType = undoRecordType;
      this.kind = kindOf(this.getClass());
   }

   private static String kindOf(Class<?> handlerClass) {
      String name = handlerClass.getSimpleName();
      if (name.isEmpty()) {
         return "record";
      }
      return name.endsWith(HANDLER_SUFFIX) && name.length() > HANDLER_SUFFIX.length()
         ? name.substring(0, name.length() - HANDLER_SUFFIX.length()) : name;
   }

   @Override
   public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      synchronized (this.data.getLock()) {
         Log.request(request);
         String status = request.getParameter("!nativeeditor_status");
         String rowId = request.getParameter("gr_id");
         if (rowId == null) {
            Log.info(this.describe(status, rowId) + " REFUSED: the request has no gr_id (the row's ID)");
            this.sendResponse(EDIT_INVALID, rowId, response);
            return;
         }

         DataRecord record = this.findOrInsert(rowId, status);
         if (record == null) {
            this.sendResponse(EDIT_INVALID, rowId, response);
            return;
         }

         String refused = this.refusal(record, request, status);
         if (refused != null) {
            if (EDIT_INSERTED.equals(status)) {
               this.records.remove(rowId);
            }
            Log.verbose("refused: " + refused);
            Log.info(this.describe(status, rowId) + holding(record) + " REFUSED: " + Log.clean(refused)
               + (EDIT_INSERTED.equals(status) ? " (the new row was discarded)" : ""));
            this.sendResponse(EDIT_INVALID, rowId, response, refused);
            return;
         }

         UndoLog.Builder undo = this.data.getUndoLog().begin();
         String changes = "";
         String heldBeforeDelete = "";
         // What a room card held before the edit: its Scout column is free text in the log, so the
         // occupant's ID is what says whose board an edit or a delete touched.
         String heldBeforeEdit = record instanceof RoomRecord && !EDIT_INSERTED.equals(status) ? holding(record) : "";
         if (EDIT_DELETED.equals(status)) {
            heldBeforeDelete = holding(record);
            this.records.remove(rowId);
         } else {
            boolean isEdit = this.undoRecordType != null && !EDIT_INSERTED.equals(status);
            changes = this.copyColumnsFromRequest(record, request, undo, isEdit);
         }

         record.updateFields(false);
         this.records.store();
         boolean onUndoStack = this.undoRecordType != null && !EDIT_INSERTED.equals(status) && !EDIT_DELETED.equals(status) && !undo.isEmpty();
         this.logApplied(status, rowId, record, changes, heldBeforeDelete, heldBeforeEdit, onUndoStack);
         this.afterEdit(record, request, status);
         if (onUndoStack) {
            undo.commit(this.undoRecordType + " " + rowId + " updated");
         }
         this.sendResponse(status, rowId, response);
      }
   }

   /** The start of every log line about this edit: what kind of record, which action, which row. */
   private String describe(String status, String rowId) {
      return LOG_OPERATION + ": kind=" + this.kind + " action=" + Log.safeValue("!nativeeditor_status", status)
         + " id=" + Log.safeValue("ID", rowId);
   }

   /**
    * A record's status and room, for the kinds that have them, as {@code " status=Seated room=101"}.
    * A room card also says whether a board is in it and whose, by ID, since an Admin edit or delete
    * of an occupied card is how a board gets stranded.
    */
   private String holding(DataRecord record) {
      StringBuilder held = new StringBuilder();
      if (record.isColumn("Status")) {
         held.append(" status=").append(shown("Status", record.getValue("Status")));
      }
      if (record.isColumn("Room")) {
         held.append(" room=").append(shown("Room", record.getValue("Room")));
      }
      if (record instanceof RoomRecord) {
         RoomRecord card = (RoomRecord) record;
         held.append(card.getScout().isEmpty() ? " occupied=no" : " occupied=yes by " + this.occupantOf(card));
      }
      return held.toString();
   }

   /**
    * The ID of the scout whose board sits in this room card, found by the room's name and then by the name on
    * the card; "unknown" when no board matches and "ambiguous" when several do, so the log never guesses.
    */
   private String occupantOf(RoomRecord card) {
      List<String> byRoom = new ArrayList<>();
      List<String> byName = new ArrayList<>();
      for (ScoutRecord scout : this.data.getScouts().getRecords()) {
         if (!ScoutStatus.holdsRoom(scout.getStatus())) {
            continue;
         }
         if (scout.getRoom().equals(card.getRoom())) {
            byRoom.add(Log.clean(scout.getID()));
         } else if (!card.getScout().isEmpty() && card.getScout().equals(scout.getFullName())) {
            byName.add(Log.clean(scout.getID()));
         }
      }
      List<String> matches = byRoom.isEmpty() ? byName : byRoom;
      if (matches.isEmpty()) {
         return "unknown (no seated board names this room or this card's scout)";
      }
      return matches.size() == 1 ? matches.get(0) : "ambiguous " + matches;
   }

   /**
    * A column value as it may be logged: {@link Log#safeValue} (an identifier or
    * state in full, free text as its length), never empty text, and the
    * {@code Sel} checkbox's 0 or 1 in full since it holds nothing personal.
    */
   private static String shown(String column, String value) {
      if ("Sel".equals(column) && value != null && value.length() <= 8) {
         return value.isEmpty() ? "<empty>" : Log.clean(value);
      }
      String safe = Log.safeValue(column, value);
      return safe.isEmpty() ? "<empty>" : safe;
   }

   /** One line saying what an accepted edit did, after it has been saved. */
   private void logApplied(String status, String rowId, DataRecord record, String changes, String heldBeforeDelete, String heldBeforeEdit, boolean onUndoStack) {
      int rows = this.records.getRecords().size();
      StringBuilder line = new StringBuilder(this.describe(status, rowId));
      if (EDIT_DELETED.equals(status)) {
         line.append(heldBeforeDelete).append(" -> deleted (rows now ").append(rows).append(")");
      } else if (EDIT_INSERTED.equals(status)) {
         line.append(" set: ").append(changes.isEmpty() ? "(no columns sent)" : changes)
            .append(" -> inserted (rows now ").append(rows).append(")");
      } else {
         line.append(changes.isEmpty() ? " no column changed" : " " + changes).append(" -> updated");
         if (!heldBeforeEdit.isEmpty()) {
            line.append(" (before the edit:").append(heldBeforeEdit).append(")");
         }
         if (!EDIT_UPDATED.equals(status)) {
            line.append(" (the action is not inserted, updated, or deleted, so it was applied as an update)");
         }
         if (this.undoRecordType != null) {
            line.append(onUndoStack ? " (on the Undo stack)" : " (not on the Undo stack)");
         }
      }
      if (!EDIT_DELETED.equals(status) && !rowId.equals(record.getID())) {
         line.append(" WARNING: the record's ID column is now ").append(Log.safeValue("ID", record.getID()))
            .append(" but the row is still filed under ").append(Log.safeValue("ID", rowId));
      }
      Log.info(line.toString());
   }

   /**
    * The record the edit is about: the existing one, or a new one for an insert.
    *
    * @return the record, or {@code null} when the edit cannot go ahead (an edit
    *         of a row that does not exist, or an insert of one that does)
    */
   private DataRecord findOrInsert(String rowId, String status) {
      DataRecord record = this.records.get(rowId);
      if (record == null) {
         if (!EDIT_INSERTED.equals(status)) {
            Log.verbose("no such record: " + rowId);
            Log.info(this.describe(status, rowId) + " REFUSED: no such record (the row is not in the file, so the table may be out of date)");
            return null;
         }

         return this.records.addNew(rowId, false);
      } else if (EDIT_INSERTED.equals(status)) {
         Log.verbose("record already exists: " + rowId);
         Log.info(this.describe(status, rowId) + holding(record) + " REFUSED: a record with this ID already exists");
         return null;
      }
      return record;
   }

   /**
    * Copies each column the request carries onto the record, noting the old
    * value for Undo when the edit is one Undo covers.
    *
    * @return the columns whose value changed, written {@code Column old -> new}
    *         and separated by commas, for the log; empty when none changed
    */
   private String copyColumnsFromRequest(DataRecord record, HttpServletRequest request, UndoLog.Builder undo, boolean isEdit) {
      StringBuilder changes = new StringBuilder();
      for (String column : this.records.getColumns()) {
         String value = request.getParameter(column);
         Log.verbose("CHECKING: " + column + " => " + value);
         if (value != null) {
            String oldValue = record.getValue(column);
            Log.verbose("UPDATING: " + column + " => " + value);
            if (!value.equals(oldValue)) {
               if (changes.length() > 0) {
                  changes.append(", ");
               }
               changes.append(column).append(' ').append(shown(column, oldValue)).append(" -> ").append(shown(column, value));
            }
            record.put(column, value);
            // "Sel" is the operator's in-progress checkbox pick,
            // saved on every click (including auto-select's own
            // programmatic checks) so it survives a refresh. It is
            // not a reversible action in the O-2 sense, and an
            // auto-select's Sel writes can land after the seat
            // they preceded, which would otherwise clobber that
            // seat's own undo snapshot.
            if (isEdit && !this.keepsOffUndo(column)) {
               undo.field(this.undoRecordType, record, column, oldValue);
            }
         }
      }
      return changes.toString();
   }

   /** Why this edit is refused, in words for the operator; null to allow it. */
   protected String refusal(DataRecord record, HttpServletRequest request, String editStatus) {
      return null;
   }

   /** A column whose edits are never put on the Undo stack. */
   protected boolean keepsOffUndo(String column) {
      return "Sel".equals(column);
   }

   /** Called once an edit is stored. */
   protected void afterEdit(DataRecord record, HttpServletRequest request, String editStatus) throws IOException {
   }

   private void sendResponse(String status, String rowId, HttpServletResponse response) throws IOException {
      this.sendResponse(status, rowId, response, null);
   }

   // A refusal carries its reason as the action's text, which the Admin
   // page shows; a grid that only reads the type still sees "invalid".
   // A reason can name someone with an accent, so the length is the
   // UTF-8 bytes written, not the characters.
   private void sendResponse(String status, String rowId, HttpServletResponse response, String reason) throws IOException {
      StringBuilder out = new StringBuilder();
      out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><data>");
      out.append("<action type=\"").append(status).append("\" sid=\"").append(rowId);
      out.append("\" tid=\"").append(rowId).append("\"");
      if (reason == null) {
         out.append(" />");
      } else {
         out.append(">").append(XmlText.escapeText(reason)).append("</action>");
      }
      out.append("</data>");
      byte[] body = out.toString().getBytes(StandardCharsets.UTF_8);
      response.setContentLength(body.length);
      response.setContentType("text/xml");
      response.getOutputStream().write(body);
      response.setStatus(200);
   }
}
