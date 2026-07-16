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
   String _dataDir;
   String _preregFile;
   String _historyFile;
   String _configFile;
   JLabel _dataDirLabel;
   JLabel _preregFileLabel;
   JLabel _historyFileLabel;
   JLabel _configFileLabel;
   private boolean _isSubmitted = false;

   public PopupDialog(String var1) {
      super("InfoDialog");
      this.setDefaultCloseOperation(3);
      Container var3 = this.getContentPane();
      var3.setLayout(new GridLayout(0, 1, 0, 4));

      StringTokenizer var4 = new StringTokenizer(var1, "\n", false);
      while (var4.hasMoreTokens()) {
         String line = var4.nextToken().trim();
         if (line.length() == 0) {
            continue;
         }
         if (line.startsWith("http://") || line.startsWith("https://")) {
            // The base URL goes on the check-in station; center it for reading.
            var3.add(centeredLabel(line));
            // Convenience link for this (admin) machine: open the scheduler.
            var3.add(linkLabel(line + "/scheduler"));
         } else {
            var3.add(centeredLabel(line));
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
      } catch (Exception var2) {
         System.out.println("could not open browser for " + url + ": " + var2.getMessage());
      }
   }

   public static void main(String[] var0) {
      PopupDialog var1 = new PopupDialog(
         "        Connect to the following URLs\n\n                 http://192.168.0.163:8080\n");
      var1.setVisible(true);
   }
}
