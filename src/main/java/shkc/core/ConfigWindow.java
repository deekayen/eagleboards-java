package shkc.core;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.filechooser.FileNameExtensionFilter;

public class ConfigWindow extends JFrame {
   String _dataDir;
   String _preregFile;
   String _historyFile;
   String _configFile;
   JLabel _dataDirLabel;
   JLabel _preregFileLabel;
   JLabel _historyFileLabel;
   JLabel _configFileLabel;
   private boolean _isSubmitted = false;

   public ConfigWindow(String var1, String var2, String var3, String var4) {
      super("File Chooser Test Frame");
      this._dataDir = var1;
      this._preregFile = var2;
      this._historyFile = var3;
      this._configFile = var4;
      this.setSize(600, 200);
      Dimension var5 = Toolkit.getDefaultToolkit().getScreenSize();
      this.setLocation(var5.width / 2 - this.getSize().width / 2, var5.height / 2 - this.getSize().height / 2);
      this.setDefaultCloseOperation(3);
      Container var6 = this.getContentPane();
      var6.setLayout(new GridLayout(0, 3, 20, 20));
      JButton var7 = new JButton("Change");
      JButton var8 = new JButton("Change");
      JButton var9 = new JButton("Change");
      JButton var10 = new JButton("Change");
      JButton var11 = new JButton("Submit");
      this._dataDirLabel = new JLabel(this._dataDir);
      this._preregFileLabel = new JLabel(this._preregFile);
      this._historyFileLabel = new JLabel(this._historyFile);
      this._configFileLabel = new JLabel(this._configFile);
      final JLabel var12 = new JLabel("");
      var7.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent var1) {
            JFileChooser var2x = new JFileChooser(new File(System.getProperty("user.dir")));
            var2x.setMultiSelectionEnabled(false);
            var2x.setFileHidingEnabled(true);
            var2x.setFileSelectionMode(1);
            int var3x = var2x.showOpenDialog(ConfigWindow.this);
            if (var3x == 0) {
               ConfigWindow.this._dataDir = var2x.getSelectedFile().getAbsolutePath();
               ConfigWindow.this._dataDirLabel.setText(ConfigWindow.this._dataDir);
            } else {
               var12.setText("You canceled.");
            }
         }
      });
      var8.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent var1) {
            JFileChooser var2 = new JFileChooser(new File(System.getProperty("user.dir")));
            var2.setDialogTitle("Select Pre-registration File (.csv)");
            var2.setMultiSelectionEnabled(false);
            var2.setFileHidingEnabled(true);
            FileNameExtensionFilter var3 = new FileNameExtensionFilter("CSV", "csv");
            var2.addChoosableFileFilter(var3);
            int var4 = var2.showOpenDialog(ConfigWindow.this);
            if (var4 == 0) {
               ConfigWindow.this._preregFile = var2.getSelectedFile().getAbsolutePath();
               ConfigWindow.this._preregFileLabel.setText(ConfigWindow.this._preregFile);
            } else {
               var12.setText("You canceled.");
            }
         }
      });
      var9.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent var1) {
            JFileChooser var2 = new JFileChooser(new File(System.getProperty("user.dir")));
            var2.setDialogTitle("Select Adult History File (.csv)");
            var2.setMultiSelectionEnabled(false);
            var2.setFileHidingEnabled(true);
            FileNameExtensionFilter var3 = new FileNameExtensionFilter("CSV", "csv");
            var2.addChoosableFileFilter(var3);
            int var4 = var2.showOpenDialog(ConfigWindow.this);
            if (var4 == 0) {
               ConfigWindow.this._historyFile = var2.getSelectedFile().getAbsolutePath();
               ConfigWindow.this._historyFileLabel.setText(ConfigWindow.this._historyFile);
            } else {
               var12.setText("You canceled.");
            }
         }
      });
      var10.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent var1) {
            JFileChooser var2 = new JFileChooser(new File(System.getProperty("user.dir")));
            var2.setDialogTitle("Select Config File (.csv)");
            var2.setMultiSelectionEnabled(false);
            var2.setFileHidingEnabled(true);
            FileNameExtensionFilter var3 = new FileNameExtensionFilter("CSV", "csv");
            var2.addChoosableFileFilter(var3);
            int var4 = var2.showOpenDialog(ConfigWindow.this);
            if (var4 == 0) {
               ConfigWindow.this._configFile = var2.getSelectedFile().getAbsolutePath();
               ConfigWindow.this._configFileLabel.setText(ConfigWindow.this._configFile);
            } else {
               var12.setText("You canceled.");
            }
         }
      });
      var11.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent var1) {
            ConfigWindow.this._isSubmitted = true;
            ConfigWindow.this.setVisible(false);
         }
      });
      var6.add(new JLabel("     Data Directory: "));
      var6.add(this._dataDirLabel);
      var6.add(var7);
      var6.add(new JLabel("     Pre-Registration File: "));
      var6.add(this._preregFileLabel);
      var6.add(var8);
      var6.add(new JLabel("     Adult History File: "));
      var6.add(this._historyFileLabel);
      var6.add(var9);
      var6.add(new JLabel("     Config File: "));
      var6.add(this._configFileLabel);
      var6.add(var10);
      var6.add(new JLabel(""));
      var6.add(var11);
      var6.add(new JLabel(""));
      var6.add(new JLabel(""));
      var6.add(var12);
      var6.add(new JLabel(""));
   }

   public boolean isCompleted() {
      return this._isSubmitted;
   }

   public String getDataDir() {
      return this._dataDir;
   }

   public String getPreregFile() {
      return this._preregFile;
   }

   public String getAdultHistoryFile() {
      return this._historyFile;
   }

   public String getConfigFile() {
      return this._configFile;
   }

   public static void main(String[] var0) {
      ConfigWindow var1 = new ConfigWindow("1111", "pre.csv", "adult.csv", "condig.csv");
      var1.setVisible(true);
   }
}
