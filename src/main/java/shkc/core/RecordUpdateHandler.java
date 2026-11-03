package shkc.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

   private final EventData data;
   private final DataRecordFile<T> records;
   // Non-null only for the handlers O-2 covers (adult field edits --
   // Disable/Enable/Link -- and room rename); null everywhere else
   // (scout/room-admin edits, adult/scout-scheduled history), which
   // just don't offer Undo. See UndoLog.
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
   }

   @Override
   public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      synchronized (this.data.getLock()) {
         Log.request(request);
         String status = request.getParameter("!nativeeditor_status");
         String rowId = request.getParameter("gr_id");
         if (rowId == null) {
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
            this.sendResponse(EDIT_INVALID, rowId, response, refused);
            return;
         }

         UndoLog.Builder undo = this.data.getUndoLog().begin();
         if (EDIT_DELETED.equals(status)) {
            this.records.remove(rowId);
         } else {
            boolean isEdit = this.undoRecordType != null && !EDIT_INSERTED.equals(status);
            this.copyColumnsFromRequest(record, request, undo, isEdit);
         }

         record.updateFields(false);
         this.records.store();
         this.afterEdit(record, request, status);
         if (this.undoRecordType != null && !EDIT_INSERTED.equals(status) && !EDIT_DELETED.equals(status) && !undo.isEmpty()) {
            undo.commit(this.undoRecordType + " " + rowId + " updated");
         }
         this.sendResponse(status, rowId, response);
      }
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
            return null;
         }

         return this.records.addNew(rowId, false);
      } else if (EDIT_INSERTED.equals(status)) {
         Log.verbose("record already exists: " + rowId);
         return null;
      }
      return record;
   }

   /**
    * Copies each column the request carries onto the record, noting the old
    * value for Undo when the edit is one Undo covers.
    */
   private void copyColumnsFromRequest(DataRecord record, HttpServletRequest request, UndoLog.Builder undo, boolean isEdit) {
      for (String column : this.records.getColumns()) {
         String value = request.getParameter(column);
         Log.verbose("CHECKING: " + column + " => " + value);
         if (value != null) {
            String oldValue = record.getValue(column);
            Log.verbose("UPDATING: " + column + " => " + value);
            record.put(column, value);
            // "Sel" is the operator's in-progress checkbox pick,
            // saved on every click (including auto-select's own
            // programmatic checks) so it survives a refresh --
            // not a reversible action in the O-2 sense, and an
            // auto-select's Sel writes can land after the seat
            // they preceded, which would otherwise clobber that
            // seat's own undo snapshot.
            if (isEdit && !this.keepsOffUndo(column)) {
               undo.field(this.undoRecordType, record, column, oldValue);
            }
         }
      }
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
