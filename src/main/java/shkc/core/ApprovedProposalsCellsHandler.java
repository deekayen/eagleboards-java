package shkc.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * SPEC.md D-22: every project proposal approved at an earlier event, for a
 * youth who comes to their board of review without the signed page. Each
 * time it is asked it reads every dated folder beside this event's, dated
 * before it however long ago, and answers as /youth-cells does with only
 * COLUMNS, the Windows version's (EarlierEvents.ApprovalRecord), the
 * event's date as Event. Never a birthdate, phone number or email (D-7,
 * D-8). A folder with no youth file held no event, and none is created
 * there: nothing in an earlier folder is written. The rows element also
 * says which events were read (read, from, to) and names any that
 * couldn't be (unreadable, "|" separated).
 *
 * <p>Serves {@code /approved-proposals-cells}. The one request parameter is
 * {@code cols}, the columns wanted (default all of {@link #COLUMNS}).
 */
public class ApprovedProposalsCellsHandler implements WebServer.WebHandler {
   static final String[] COLUMNS = {"Event", "Last", "First", "UnitType", "Unit", "BoardChair", "BoardMembers", "Notes"};

   // Positions in COLUMNS that the sort looks at.
   private static final int EVENT_COLUMN = 0;
   private static final int LAST_COLUMN = 1;
   private static final int FIRST_COLUMN = 2;

   /** An event's folder is named for its date. */
   private static final Pattern EVENT_FOLDER_NAME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

   /** The youth file inside an event's folder. */
   private static final String YOUTH_FILE_NAME = "scouts.csv";

   private final EventData data;

   public ApprovedProposalsCellsHandler(EventData data) {
      this.data = data;
   }

   /**
    * One approved proposal: the row's ID (event and youth), then its cells in
    * the order of COLUMNS, as the file holds them.
    */
   private record Approval(String rowId, String[] cells) {
   }

   /** What reading the earlier events found. */
   private static final class EarlierEvents {
      final List<Approval> approvals = new ArrayList<>();
      /** The events whose youth file was read. */
      final List<String> read = new ArrayList<>();
      /** One "event: reason" for each youth file that could not be read. */
      final List<String> unreadable = new ArrayList<>();
   }

   @Override
   public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      File root = this.data.getDataRoot().getAbsoluteFile().getParentFile();
      EarlierEvents earlier = this.collectApprovals(root, this.readEarlierEventFolders(root));
      // By last name, then first, then the oldest approval first.
      earlier.approvals.sort((a, b) -> {
         int last = a.cells()[LAST_COLUMN].compareToIgnoreCase(b.cells()[LAST_COLUMN]);
         int first = a.cells()[FIRST_COLUMN].compareToIgnoreCase(b.cells()[FIRST_COLUMN]);
         return last != 0 ? last : (first != 0 ? first : a.cells()[EVENT_COLUMN].compareTo(b.cells()[EVENT_COLUMN]));
      });

      String[] columns = RequestFields.columns(request.getParameter("cols"), COLUMNS);
      byte[] body = this.writeRows(earlier, columns).getBytes(StandardCharsets.UTF_8);
      response.setContentType("text/xml; charset=UTF-8");
      response.setContentLength(body.length);
      response.getOutputStream().write(body);
      response.setStatus(200);
   }

   /**
    * The names of the dated folders beside this event's that are dated before
    * it, oldest first. When this event's own folder is not named for a date
    * (a test run, say), today is used instead.
    */
   private List<String> readEarlierEventFolders(File root) {
      String thisEvent = this.data.getDataRoot().getName();
      if (!EVENT_FOLDER_NAME.matcher(thisEvent).matches()) {
         thisEvent = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
      }
      List<String> events = new ArrayList<>();
      File[] folders = root == null ? null : root.listFiles();
      for (File folder : folders == null ? new File[0] : folders) {
         if (folder.isDirectory() && EVENT_FOLDER_NAME.matcher(folder.getName()).matches() && folder.getName().compareTo(thisEvent) < 0) {
            events.add(folder.getName());
         }
      }
      Collections.sort(events);
      return events;
   }

   /** Reads each event's youth file and keeps the youth whose project proposal was approved. */
   private EarlierEvents collectApprovals(File root, List<String> events) {
      EarlierEvents earlier = new EarlierEvents();
      for (String event : events) {
         File youthFile = new File(new File(root, event), YOUTH_FILE_NAME);
         if (!youthFile.exists()) {
            continue;
         }
         try {
            for (ScoutRecord youth : new DataRecordFile<ScoutRecord>(youthFile, new ScoutRecord.Factory()).getRecords()) {
               if (BoardType.PROJECT.equals(youth.getValue("BoardType")) && ScoutResult.APPROVED.equals(youth.getValue("Result"))) {
                  earlier.approvals.add(this.approvalOf(youth, event));
               }
            }
            earlier.read.add(event);
         } catch (IOException | RuntimeException cannotRead) {
            earlier.unreadable.add(event + ": " + cannotRead.getMessage());
         }
      }
      return earlier;
   }

   private Approval approvalOf(ScoutRecord youth, String event) {
      return new Approval(event + "|" + youth.getID(), new String[]{
         event, youth.getValue("Last"), youth.getValue("First"), youth.getValue("UnitType"),
         youth.getValue("Unit"), youth.getValue("BoardChair"), youth.getValue("BoardMembers"), youth.getValue("Notes")
      });
   }

   /** The {@code <rows>} document: a summary of what was read, then one row per approval. */
   private String writeRows(EarlierEvents earlier, String[] columns) {
      List<String> read = earlier.read;
      StringBuilder out = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><rows read=\"").append(read.size()).append("\"");
      if (!read.isEmpty()) {
         out.append(" from=\"").append(read.get(0)).append("\" to=\"").append(read.get(read.size() - 1)).append("\"");
      }
      out.append(" unreadable=\"").append(XmlText.escapeAttribute(String.join("|", earlier.unreadable))).append("\">");
      for (Approval approval : earlier.approvals) {
         out.append("<row id=\"").append(XmlText.escapeAttribute(approval.rowId())).append("\">");
         for (String column : columns) {
            int index = Arrays.asList(COLUMNS).indexOf(column);
            out.append("<cell>").append(index < 0 ? "" : XmlText.escapeAttribute(approval.cells()[index])).append("</cell>");
         }
         out.append("</row>\n");
      }
      out.append("</rows>");
      return out.toString();
   }
}
