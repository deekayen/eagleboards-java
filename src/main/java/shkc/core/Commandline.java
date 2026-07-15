package shkc.core;

import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

public class Commandline {
   public String[] params;
   Hashtable optionlist;
   public boolean debug = false;
   private String _separators = ":, ";
   private static final String _ident = "$Id: Commandline.java,v 1.1 2015/01/24 14:50:21 sking Exp $";

   public Commandline(String[] var1, String var2, String var3, String[] var4, String[] var5) throws InvalidCommandlineArgument {
      this.doParse(var1, var2, var3, var4, var5);
   }

   public Commandline(String[] var1, String var2, String var3) throws InvalidCommandlineArgument {
      String[] var4 = new String[0];
      this.doParse(var1, var2, var3, var4, var4);
   }

   public Commandline(String[] var1, String var2, String var3, String var4, String[] var5, String[] var6, String[] var7) throws InvalidCommandlineArgument, MissingCommandlineArgument {
      boolean var8 = false;
      String var9 = "Missing Commandline Option(s): ";
      String[] var10 = new String[var6.length + var7.length];
      int var12 = 0;

      for (int var11 = 0; var11 < var6.length; var12++) {
         var10[var12] = var6[var11];
         var11++;
      }

      for (int var13 = 0; var13 < var7.length; var12++) {
         var10[var12] = var7[var13];
         var13++;
      }

      this.doParse(var1, var2, var3 + var4, var5, var10);

      for (int var14 = 0; var14 < var4.length(); var14++) {
         if (this.getOption(new Character(var4.charAt(var14))).equals("")) {
            var9 = var9 + " " + new Character(var4.charAt(var14)).toString();
            var8 = true;
         }
      }

      for (int var15 = 0; var15 < var7.length; var15++) {
         if (this.getOption(var7[var15]).equals("")) {
            var9 = var9 + " " + var7[var15];
            var8 = true;
         }
      }

      if (var8) {
         throw new MissingCommandlineArgument(var9);
      }
   }

   public void setOptionNameSeparators(String var1) {
      this._separators = var1;
   }

   void doParse(String[] var1, String var2, String var3, String[] var4, String[] var5) throws InvalidCommandlineArgument {
      boolean var8 = false;
      String var9 = new String("Invalid Commandline argument(s): ");
      Hashtable var10 = new Hashtable();
      boolean var13 = true;
      Vector var14 = new Vector(1, 1);
      this.optionlist = new Hashtable(1, 1.0F);

      for (int var6 = 0; var6 < var4.length; var6++) {
         if (this.debug) {
            System.out.println("debug: Looking for arg \"" + var4[var6] + "\"");
         }

         var10.put(var4[var6], Boolean.FALSE);
      }

      for (int var17 = 0; var17 < var5.length; var17++) {
         if (this.debug) {
            System.out.println("debug: Looking for arg \"" + var5[var17] + "\"");
         }

         var10.put(var5[var17], Boolean.TRUE);
      }

      for (int var18 = 0; var18 < var2.length(); var18++) {
         if (this.debug) {
            System.out.println("debug: Looking for arg " + new Character(var2.charAt(var18)).toString());
         }

         var10.put(new Character(var2.charAt(var18)).toString(), Boolean.FALSE);
      }

      for (int var19 = 0; var19 < var3.length(); var19++) {
         if (this.debug) {
            System.out.println("debug: Looking for arg " + new Character(var3.charAt(var19)).toString());
         }

         var10.put(new Character(var3.charAt(var19)).toString(), Boolean.TRUE);
      }

      for (int var20 = 0; var20 < var1.length; var20++) {
         String var11 = var1[var20];
         if (this.debug) {
            System.out.println("debug: Arg \"" + var11 + "\": ");
         }

         if (!var13) {
            var14.addElement(var11);
            if (this.debug) {
               System.out.println("debug:   has param " + var11);
            }
         } else if (var11.equalsIgnoreCase("--")) {
            var13 = false;
         } else if (var11.length() > 2 && var11.charAt(0) == '-' && var10.containsKey(var11.substring(1))) {
            int var21 = 0;
            var11 = var11.substring(1);
            if (this.debug) {
               System.out.println("debug:   looking for internal flag \"" + var11 + "\"");
            }

            if (var10.containsKey(var11)) {
               if (var10.get(var11) == Boolean.FALSE) {
                  this.sethasflag(var11);
               } else {
                  String var23;
                  this.sethasoption(var11, var23 = this.paramfor(var1, var20, ++var21));
                  if (var23.equals("")) {
                     var21--;
                  }
               }
            } else {
               var9 = var9 + " " + var11;
               var8 = true;
            }

            var20 += var21;
         } else if (!this.isoptlist(var11)) {
            var14.addElement(var11);
            if (this.debug) {
               System.out.println("debug:   has param " + var11);
            }
         } else {
            int var7 = 0;

            for (int var15 = 1; var15 < var11.length(); var15++) {
               String var16 = new Character(var11.charAt(var15)).toString();
               if (var10.containsKey(var16)) {
                  if (var10.get(var16) == Boolean.FALSE) {
                     this.sethasflag(var16);
                  } else {
                     String var12;
                     this.sethasoption(var16, var12 = this.paramfor(var1, var20, ++var7));
                     if (var12.equals("")) {
                        var7--;
                     }
                  }
               } else {
                  var9 = var9 + " " + var16;
                  var8 = true;
               }
            }

            var20 += var7;
         }
      }

      this.params = new String[var14.size()];
      var14.copyInto(this.params);
      if (var8) {
         throw new InvalidCommandlineArgument(var9);
      }
   }

   String paramfor(String[] var1, int var2, int var3) {
      for (int var4 = var2 + 1; var4 <= var2 + var3; var4++) {
         if (var4 >= var1.length || this.isoptlist(var1[var4])) {
            if (this.debug) {
               System.out.print("debug:   escaping paramfor early");
               if (var4 >= var1.length) {
                  System.out.println(" cuz arglist is too short.");
               } else {
                  System.out.println(" cuz we hit a new opt list.");
               }
            }

            return new String("");
         }
      }

      return var1[var2 + var3];
   }

   synchronized void sethasflag(String var1) {
      if (this.debug) {
         System.out.println("debug:   has flag " + var1);
      }

      this.optionlist.put(var1, new String(""));
   }

   synchronized void sethasoption(String var1, String var2) {
      if (this.debug) {
         System.out.println("debug:   has option " + var1 + " with value " + var2);
      }

      this.optionlist.put(var1, var2);
   }

   boolean isoptlist(String var1) {
      if (this.debug) {
         System.out.println("debug:   calling isoptlist(" + var1 + ")");
      }

      return var1.length() > 1 && var1.charAt(0) == '-';
   }

   boolean islongopt(String var1) {
      return var1.length() > 2 && var1.charAt(0) == '-' && var1.charAt(1) == '-';
   }

   public boolean hasOption(Character var1) {
      return this.hasOption(var1.toString());
   }

   public boolean hasOption(String var1) {
      StringTokenizer var2 = new StringTokenizer(var1, this._separators, false);

      while (var2.hasMoreTokens()) {
         String var3 = var2.nextToken();
         if (this.optionlist.containsKey(var3)) {
            return true;
         }
      }

      return false;
   }

   public String getOption(Character var1) {
      return this.getOption(var1.toString());
   }

   public String getOption(String var1) {
      return this.getOption(var1, null);
   }

   public int getIntOption(String var1, int var2) {
      StringTokenizer var3 = new StringTokenizer(var1, this._separators, false);

      while (var3.hasMoreTokens()) {
         String var4 = var3.nextToken();
         if (this.hasOption(var4)) {
            return Integer.parseInt(this.getOption(var4));
         }
      }

      return var2;
   }

   public boolean hasFlag(String var1) {
      StringTokenizer var2 = new StringTokenizer(var1, this._separators, false);

      while (var2.hasMoreTokens()) {
         String var3 = var2.nextToken();
         if (this.hasOption(var3)) {
            return true;
         }
      }

      return false;
   }

   public String getOption(String var1, String var2) {
      StringTokenizer var3 = new StringTokenizer(var1, this._separators, false);

      while (var3.hasMoreTokens()) {
         String var4 = var3.nextToken();
         String var5 = (String)this.optionlist.get(var4);
         if (var5 != null) {
            return var5;
         }
      }

      return var2;
   }

   public static void test(String[] var0, String var1, String var2, String[] var3, String[] var4) {
      System.out.println("Valid short options are: " + var2 + " (with args) and " + var1 + " (without args).");
      System.out.println("Valid long options are: ");

      for (int var6 = 0; var6 < var4.length; var6++) {
         System.out.println("  " + var4[var6]);
      }

      System.out.println("Valid long flags are: ");

      for (int var11 = 0; var11 < var3.length; var11++) {
         System.out.println("  " + var3[var11]);
      }

      System.out.print("Running with options: ");

      for (int var12 = 0; var12 < var0.length; var12++) {
         System.out.print(var0[var12] + " ");
      }

      System.out.println(" ...");

      try {
         Commandline var13 = new Commandline(var0, var1, var2, var3, var4);
         var2 = var2 + var1;

         for (int var7 = 0; var7 < var2.length(); var7++) {
            String var5 = var2.substring(var7, var7 + 1);
            if (var13.hasOption(var5)) {
               System.out.print("... found option " + var5 + " with value " + var13.getOption(var5) + "\n");
            } else {
               System.out.print("... didn't find option " + var5 + "\n");
            }
         }

         for (int var14 = 0; var14 < var4.length + var3.length; var14++) {
            String var10;
            if (var14 < var4.length) {
               var10 = var4[var14];
            } else {
               var10 = var3[var14 - var4.length];
            }

            if (var13.hasOption(var10)) {
               System.out.print("... found option " + var10 + " with value " + var13.getOption(var10) + "\n");
            } else {
               System.out.print("... didn't find option " + var10 + "\n");
            }
         }

         System.out.print("... Extra Parameters: ");

         for (int var15 = 0; var15 < var13.params.length; var15++) {
            System.out.print(var13.params[var15] + " ");
         }

         System.out.print("\n");
      } catch (InvalidCommandlineArgument var8) {
         System.out.println("Caught an Invalid command line argument:\n" + var8.toString() + "\n");
      }

      System.out.println("Done.");
   }
}
