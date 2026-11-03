package shkc.core;

import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.StringTokenizer;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * The small window shown at startup (only with {@code -w}) that lists the
 * addresses the check-in page can be reached at. For each web address it also
 * shows a clickable link to the scheduler, for the operator on this machine.
 * Closing the window ends the program.
 */
public class PopupDialog extends JFrame {
   private static final long serialVersionUID = 1L;

   private static final int MINIMUM_WIDTH = 320;
   private static final int MINIMUM_HEIGHT = 160;
   private static final int ROW_GAP = 4;
   private static final int LABEL_VERTICAL_PADDING = 4;
   private static final int LABEL_HORIZONTAL_PADDING = 16;
   private static final Color LINK_COLOR = new Color(0, 102, 204);

   /**
    * @param text what to show, one label per line; a line that is a web
    *             address also gets a link to {@code /scheduler} beneath it
    */
   public PopupDialog(String text) {
      super("InfoDialog");
      this.setDefaultCloseOperation(EXIT_ON_CLOSE);
      Container content = this.getContentPane();
      content.setLayout(new GridLayout(0, 1, 0, ROW_GAP));

      StringTokenizer lines = new StringTokenizer(text, "\n", false);
      while (lines.hasMoreTokens()) {
         String line = lines.nextToken().trim();
         if (line.length() == 0) {
            continue;
         }
         if (line.startsWith("http://") || line.startsWith("https://")) {
            // The base URL goes on the check-in station; center it for reading.
            content.add(centeredLabel(line));
            // Convenience link for this (admin) machine: open the scheduler.
            content.add(linkLabel(line + "/scheduler"));
         } else {
            content.add(centeredLabel(line));
         }
      }

      this.pack();
      this.growToMinimumSizeAndCenter();
   }

   /** Makes the window at least the minimum size, then puts it in the middle of the screen. */
   private void growToMinimumSizeAndCenter() {
      Dimension packedSize = this.getSize();
      int width = Math.max(packedSize.width, MINIMUM_WIDTH);
      int height = Math.max(packedSize.height, MINIMUM_HEIGHT);
      this.setSize(width, height);
      Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
      this.setLocation(screen.width / 2 - width / 2, screen.height / 2 - height / 2);
   }

   private static JLabel centeredLabel(String text) {
      JLabel label = new JLabel(text, SwingConstants.CENTER);
      label.setBorder(BorderFactory.createEmptyBorder(LABEL_VERTICAL_PADDING, LABEL_HORIZONTAL_PADDING, LABEL_VERTICAL_PADDING, LABEL_HORIZONTAL_PADDING));
      return label;
   }

   /** An underlined blue label that opens {@code url} in the default browser when clicked. */
   private static JLabel linkLabel(final String url) {
      JLabel label = new JLabel("<html><u>" + url + "</u></html>", SwingConstants.CENTER);
      label.setForeground(LINK_COLOR);
      label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      label.setBorder(BorderFactory.createEmptyBorder(LABEL_VERTICAL_PADDING, LABEL_HORIZONTAL_PADDING, LABEL_VERTICAL_PADDING, LABEL_HORIZONTAL_PADDING));
      label.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent click) {
            openInBrowser(url);
         }
      });
      return label;
   }

   private static void openInBrowser(String url) {
      try {
         if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(new URI(url));
         } else {
            System.out.println("no browser support available for " + url);
         }
      } catch (Exception failure) {
         System.out.println("could not open browser for " + url + ": " + failure.getMessage());
      }
   }
}
