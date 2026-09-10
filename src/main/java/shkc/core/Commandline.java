package shkc.core;

import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

public class Commandline {
   public String[] params;
   Hashtable optionlist;
   public boolean debug = false;
   private String _separators = ":, ";

   public Commandline(String[] args, String shortFlags, String shortOptions, String[] longFlags, String[] longOptions) throws InvalidCommandlineArgument {
      this.doParse(args, shortFlags, shortOptions, longFlags, longOptions);
   }

   public Commandline(String[] args, String shortFlags, String shortOptions) throws InvalidCommandlineArgument {
      String[] empty = new String[0];
      this.doParse(args, shortFlags, shortOptions, empty, empty);
   }

   public Commandline(String[] args, String shortFlags, String shortOptions, String requiredShortOptions, String[] longFlags, String[] longOptions, String[] requiredLongOptions) throws InvalidCommandlineArgument, MissingCommandlineArgument {
      boolean missing = false;
      String message = "Missing Commandline Option(s): ";
      String[] allLongOptions = new String[longOptions.length + requiredLongOptions.length];
      int writeIndex = 0;

      for (int longOptionIndex = 0; longOptionIndex < longOptions.length; writeIndex++) {
         allLongOptions[writeIndex] = longOptions[longOptionIndex];
         longOptionIndex++;
      }

      for (int requiredIndex = 0; requiredIndex < requiredLongOptions.length; writeIndex++) {
         allLongOptions[writeIndex] = requiredLongOptions[requiredIndex];
         requiredIndex++;
      }

      this.doParse(args, shortFlags, shortOptions + requiredShortOptions, longFlags, allLongOptions);

      for (int shortIndex = 0; shortIndex < requiredShortOptions.length(); shortIndex++) {
         if (this.getOption(new Character(requiredShortOptions.charAt(shortIndex))).equals("")) {
            message = message + " " + new Character(requiredShortOptions.charAt(shortIndex)).toString();
            missing = true;
         }
      }

      for (int requiredLongIndex = 0; requiredLongIndex < requiredLongOptions.length; requiredLongIndex++) {
         if (this.getOption(requiredLongOptions[requiredLongIndex]).equals("")) {
            message = message + " " + requiredLongOptions[requiredLongIndex];
            missing = true;
         }
      }

      if (missing) {
         throw new MissingCommandlineArgument(message);
      }
   }

   void doParse(String[] args, String shortFlags, String shortOptions, String[] longFlags, String[] longOptions) throws InvalidCommandlineArgument {
      boolean invalid = false;
      String message = new String("Invalid Commandline argument(s): ");
      Hashtable takesValue = new Hashtable();
      boolean parsingOptions = true;
      Vector extraParams = new Vector(1, 1);
      this.optionlist = new Hashtable(1, 1.0F);

      for (int longFlagIndex = 0; longFlagIndex < longFlags.length; longFlagIndex++) {
         if (this.debug) {
            System.out.println("debug: Looking for arg \"" + longFlags[longFlagIndex] + "\"");
         }

         takesValue.put(longFlags[longFlagIndex], Boolean.FALSE);
      }

      for (int longOptionIndex = 0; longOptionIndex < longOptions.length; longOptionIndex++) {
         if (this.debug) {
            System.out.println("debug: Looking for arg \"" + longOptions[longOptionIndex] + "\"");
         }

         takesValue.put(longOptions[longOptionIndex], Boolean.TRUE);
      }

      for (int shortFlagIndex = 0; shortFlagIndex < shortFlags.length(); shortFlagIndex++) {
         if (this.debug) {
            System.out.println("debug: Looking for arg " + new Character(shortFlags.charAt(shortFlagIndex)).toString());
         }

         takesValue.put(new Character(shortFlags.charAt(shortFlagIndex)).toString(), Boolean.FALSE);
      }

      for (int shortOptionIndex = 0; shortOptionIndex < shortOptions.length(); shortOptionIndex++) {
         if (this.debug) {
            System.out.println("debug: Looking for arg " + new Character(shortOptions.charAt(shortOptionIndex)).toString());
         }

         takesValue.put(new Character(shortOptions.charAt(shortOptionIndex)).toString(), Boolean.TRUE);
      }

      for (int argIndex = 0; argIndex < args.length; argIndex++) {
         String arg = args[argIndex];
         if (this.debug) {
            System.out.println("debug: Arg \"" + arg + "\": ");
         }

         if (!parsingOptions) {
            extraParams.addElement(arg);
            if (this.debug) {
               System.out.println("debug:   has param " + arg);
            }
         } else if (arg.equalsIgnoreCase("--")) {
            parsingOptions = false;
         } else if (arg.length() > 2 && arg.charAt(0) == '-' && takesValue.containsKey(arg.substring(1))) {
            int consumed = 0;
            arg = arg.substring(1);
            if (this.debug) {
               System.out.println("debug:   looking for internal flag \"" + arg + "\"");
            }

            if (takesValue.containsKey(arg)) {
               if (takesValue.get(arg) == Boolean.FALSE) {
                  this.sethasflag(arg);
               } else {
                  String value;
                  this.sethasoption(arg, value = this.paramfor(args, argIndex, ++consumed));
                  if (value.equals("")) {
                     consumed--;
                  }
               }
            } else {
               message = message + " " + arg;
               invalid = true;
            }

            argIndex += consumed;
         } else if (!this.isoptlist(arg)) {
            extraParams.addElement(arg);
            if (this.debug) {
               System.out.println("debug:   has param " + arg);
            }
         } else {
            int consumedShort = 0;

            for (int charIndex = 1; charIndex < arg.length(); charIndex++) {
               String optionName = new Character(arg.charAt(charIndex)).toString();
               if (takesValue.containsKey(optionName)) {
                  if (takesValue.get(optionName) == Boolean.FALSE) {
                     this.sethasflag(optionName);
                  } else {
                     String shortValue;
                     this.sethasoption(optionName, shortValue = this.paramfor(args, argIndex, ++consumedShort));
                     if (shortValue.equals("")) {
                        consumedShort--;
                     }
                  }
               } else {
                  message = message + " " + optionName;
                  invalid = true;
               }
            }

            argIndex += consumedShort;
         }
      }

      this.params = new String[extraParams.size()];
      extraParams.copyInto(this.params);
      if (invalid) {
         throw new InvalidCommandlineArgument(message);
      }
   }

   String paramfor(String[] args, int argIndex, int offset) {
      for (int lookahead = argIndex + 1; lookahead <= argIndex + offset; lookahead++) {
         if (lookahead >= args.length || this.isoptlist(args[lookahead])) {
            if (this.debug) {
               System.out.print("debug:   escaping paramfor early");
               if (lookahead >= args.length) {
                  System.out.println(" cuz arglist is too short.");
               } else {
                  System.out.println(" cuz we hit a new opt list.");
               }
            }

            return new String("");
         }
      }

      return args[argIndex + offset];
   }

   synchronized void sethasflag(String name) {
      if (this.debug) {
         System.out.println("debug:   has flag " + name);
      }

      this.optionlist.put(name, new String(""));
   }

   synchronized void sethasoption(String name, String value) {
      if (this.debug) {
         System.out.println("debug:   has option " + name + " with value " + value);
      }

      this.optionlist.put(name, value);
   }

   boolean isoptlist(String arg) {
      if (this.debug) {
         System.out.println("debug:   calling isoptlist(" + arg + ")");
      }

      return arg.length() > 1 && arg.charAt(0) == '-';
   }

   public boolean hasOption(Character name) {
      return this.hasOption(name.toString());
   }

   public boolean hasOption(String names) {
      StringTokenizer tokens = new StringTokenizer(names, this._separators, false);

      while (tokens.hasMoreTokens()) {
         String name = tokens.nextToken();
         if (this.optionlist.containsKey(name)) {
            return true;
         }
      }

      return false;
   }

   public String getOption(Character name) {
      return this.getOption(name.toString());
   }

   public String getOption(String names) {
      return this.getOption(names, null);
   }

   public int getIntOption(String names, int defaultValue) {
      StringTokenizer tokens = new StringTokenizer(names, this._separators, false);

      while (tokens.hasMoreTokens()) {
         String name = tokens.nextToken();
         if (this.hasOption(name)) {
            return Integer.parseInt(this.getOption(name));
         }
      }

      return defaultValue;
   }

   public boolean hasFlag(String names) {
      StringTokenizer tokens = new StringTokenizer(names, this._separators, false);

      while (tokens.hasMoreTokens()) {
         String name = tokens.nextToken();
         if (this.hasOption(name)) {
            return true;
         }
      }

      return false;
   }

   public String getOption(String names, String defaultValue) {
      StringTokenizer tokens = new StringTokenizer(names, this._separators, false);

      while (tokens.hasMoreTokens()) {
         String name = tokens.nextToken();
         String value = (String)this.optionlist.get(name);
         if (value != null) {
            return value;
         }
      }

      return defaultValue;
   }

}
