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

public class PopupDialog extends JFrame {
   public PopupDialog(String text) {
      super("InfoDialog");
      this.setDefaultCloseOperation(3);
      Container content = this.getContentPane();
      content.setLayout(new GridLayout(0, 1, 0, 4));

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
      Dimension pref = this.getSize();
      int w = Math.max(pref.width, 320);
      int h = Math.max(pref.height, 160);
      this.setSize(w, h);
      Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
      this.setLocation(screen.width / 2 - w / 2, screen.height / 2 - h / 2);
   }

   private static JLabel centeredLabel(String text) {
      JLabel label = new JLabel(text, SwingConstants.CENTER);
      label.setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 16));
      return label;
   }

   private static JLabel linkLabel(final String url) {
      JLabel label = new JLabel("<html><u>" + url + "</u></html>", SwingConstants.CENTER);
      label.setForeground(new Color(0, 102, 204));
      label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      label.setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 16));
      label.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
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
