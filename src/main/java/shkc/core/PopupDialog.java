package shkc.core;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.util.StringTokenizer;
import javax.swing.JFrame;
import javax.swing.JLabel;

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
      this.setSize(250, 200);
      Dimension var2 = Toolkit.getDefaultToolkit().getScreenSize();
      this.setLocation(var2.width / 2 - this.getSize().width / 2, var2.height / 2 - this.getSize().height / 2);
      this.setDefaultCloseOperation(3);
      Container var3 = this.getContentPane();
      var3.setLayout(new GridLayout(0, 1));
      StringTokenizer var4 = new StringTokenizer(var1, "\n", false);

      while (var4.hasMoreTokens()) {
         var3.add(new JLabel(var4.nextToken()));
      }
   }

   public static void main(String[] var0) {
      PopupDialog var1 = new PopupDialog("1.1.1.1\n2.2.2.2\n3.3.3.3");
      var1.setVisible(true);
   }
}
