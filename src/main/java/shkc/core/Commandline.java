package shkc.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * A small command-line parser that understands short options ({@code -d dir}),
 * long options ({@code -dir dir}), flags that take no value ({@code -v}) and
 * grouped short flags ({@code -vw}).
 *
 * <p>Everything after a bare {@code --}, and every argument that is not an
 * option, is kept in {@link #params} in the order it was given.
 *
 * <p>Callers ask for a value by one name or by several alternative names, for
 * example {@code getOption("d:dir")} finds the value of either {@code -d} or
 * {@code -dir}. Alternative names are separated by any of {@code :}, a comma or
 * a space.
 */
public class Commandline {
   /** Characters that separate alternative names in a lookup such as {@code "d:dir"}. */
   private static final String OPTION_NAME_SEPARATORS = ":, ";

   /** The arguments that were not options, in the order given. */
   public String[] params;

   /** Option and flag values by name; a flag is stored with an empty value. */
   private Map<String, String> optionValues;

   /**
    * Parses {@code args}.
    *
    * @param args         the command line, exactly as handed to {@code main}
    * @param shortFlags   one character per single-letter option that takes no value
    * @param shortOptions one character per single-letter option that takes a value
    * @param longFlags    names of the long options that take no value
    * @param longOptions  names of the long options that take a value
    * @throws InvalidCommandlineArgument if the command line names an option not listed above
    */
   public Commandline(String[] args, String shortFlags, String shortOptions, String[] longFlags, String[] longOptions) throws InvalidCommandlineArgument {
      this.parse(args, shortFlags, shortOptions, longFlags, longOptions);
   }

   /** Parses {@code args} when there are no long options at all. */
   public Commandline(String[] args, String shortFlags, String shortOptions) throws InvalidCommandlineArgument {
      String[] noNames = new String[0];
      this.parse(args, shortFlags, shortOptions, noNames, noNames);
   }

   /**
    * Parses {@code args}, and also requires some of the options to be present.
    *
    * @param requiredShortOptions one character per single-letter option that takes a value and must be given
    * @param requiredLongOptions  names of the long options that take a value and must be given
    * @throws InvalidCommandlineArgument if the command line names an option that is not listed
    * @throws MissingCommandlineArgument if a required option was left out
    */
   public Commandline(String[] args, String shortFlags, String shortOptions, String requiredShortOptions, String[] longFlags, String[] longOptions, String[] requiredLongOptions) throws InvalidCommandlineArgument, MissingCommandlineArgument {
      String[] allLongOptions = new String[longOptions.length + requiredLongOptions.length];
      System.arraycopy(longOptions, 0, allLongOptions, 0, longOptions.length);
      System.arraycopy(requiredLongOptions, 0, allLongOptions, longOptions.length, requiredLongOptions.length);

      this.parse(args, shortFlags, shortOptions + requiredShortOptions, longFlags, allLongOptions);

      StringBuilder message = new StringBuilder("Missing Commandline Option(s): ");
      boolean missing = false;

      for (int shortIndex = 0; shortIndex < requiredShortOptions.length(); shortIndex++) {
         char requiredName = requiredShortOptions.charAt(shortIndex);
         if (isBlank(this.getOption(Character.valueOf(requiredName)))) {
            message.append(' ').append(requiredName);
            missing = true;
         }
      }

      for (String requiredName : requiredLongOptions) {
         if (isBlank(this.getOption(requiredName))) {
            message.append(' ').append(requiredName);
            missing = true;
         }
      }

      if (missing) {
         throw new MissingCommandlineArgument(message.toString());
      }
   }

   /** Reads {@code args} once from front to back, filling {@link #params} and the option table. */
   private void parse(String[] args, String shortFlags, String shortOptions, String[] longFlags, String[] longOptions) throws InvalidCommandlineArgument {
      Map<String, Boolean> takesValue = registerOptionNames(shortFlags, shortOptions, longFlags, longOptions);
      List<String> unknownNames = new ArrayList<>();
      List<String> extraParams = new ArrayList<>();
      boolean parsingOptions = true;
      this.optionValues = new HashMap<>();

      for (int argIndex = 0; argIndex < args.length; argIndex++) {
         String arg = args[argIndex];

         if (!parsingOptions) {
            extraParams.add(arg);
         } else if (arg.equalsIgnoreCase("--")) {
            parsingOptions = false;
         } else if (arg.length() > 2 && arg.charAt(0) == '-' && takesValue.containsKey(arg.substring(1))) {
            // A long option such as -dir or -verbose.
            argIndex += this.recordOption(arg.substring(1), takesValue, args, argIndex, 0);
         } else if (!looksLikeOptionList(arg)) {
            extraParams.add(arg);
         } else {
            // One or more single-letter options run together, such as -vw or -d.
            int valuesConsumed = 0;

            for (int charIndex = 1; charIndex < arg.length(); charIndex++) {
               String optionName = String.valueOf(arg.charAt(charIndex));
               if (takesValue.containsKey(optionName)) {
                  valuesConsumed += this.recordOption(optionName, takesValue, args, argIndex, valuesConsumed);
               } else {
                  unknownNames.add(optionName);
               }
            }

            argIndex += valuesConsumed;
         }
      }

      this.params = extraParams.toArray(new String[0]);
      if (!unknownNames.isEmpty()) {
         StringBuilder message = new StringBuilder("Invalid Commandline argument(s): ");
         for (String unknownName : unknownNames) {
            message.append(' ').append(unknownName);
         }
         throw new InvalidCommandlineArgument(message.toString());
      }
   }

   /**
    * Builds the table of every name the parser accepts. A name maps to
    * {@code true} when it takes a value and {@code false} when it is a flag.
    * If a name is listed twice, the later list wins.
    */
   private static Map<String, Boolean> registerOptionNames(String shortFlags, String shortOptions, String[] longFlags, String[] longOptions) {
      Map<String, Boolean> takesValue = new HashMap<>();

      for (String name : longFlags) {
         takesValue.put(name, Boolean.FALSE);
      }
      for (String name : longOptions) {
         takesValue.put(name, Boolean.TRUE);
      }
      for (int index = 0; index < shortFlags.length(); index++) {
         takesValue.put(String.valueOf(shortFlags.charAt(index)), Boolean.FALSE);
      }
      for (int index = 0; index < shortOptions.length(); index++) {
         takesValue.put(String.valueOf(shortOptions.charAt(index)), Boolean.TRUE);
      }

      return takesValue;
   }

   /**
    * Stores one recognized option or flag.
    *
    * @param name              the option's name, without its leading dash
    * @param argIndex          the position of the argument that named the option
    * @param valuesAlreadyUsed how many following arguments earlier options in the same
    *                          argument have already taken as their values
    * @return how many following arguments this option used up as its value (0 or 1)
    */
   private int recordOption(String name, Map<String, Boolean> takesValue, String[] args, int argIndex, int valuesAlreadyUsed) {
      if (!takesValue.get(name)) {
         this.setFlag(name);
         return 0;
      }

      String value = valueFollowing(args, argIndex, valuesAlreadyUsed + 1);
      this.setOption(name, value);
      return value.equals("") ? 0 : 1;
   }

   /**
    * Returns the argument {@code offset} places after {@code argIndex} to use as
    * an option's value, or an empty string when there is none (the command line
    * ended, or another option came first).
    */
   private static String valueFollowing(String[] args, int argIndex, int offset) {
      for (int lookahead = argIndex + 1; lookahead <= argIndex + offset; lookahead++) {
         if (lookahead >= args.length || looksLikeOptionList(args[lookahead])) {
            return "";
         }
      }

      return args[argIndex + offset];
   }

   private synchronized void setFlag(String name) {
      this.optionValues.put(name, "");
   }

   private synchronized void setOption(String name, String value) {
      this.optionValues.put(name, value);
   }

   /** True for an argument that starts with a dash and has something after it, like {@code -v}. */
   private static boolean looksLikeOptionList(String arg) {
      return arg.length() > 1 && arg.charAt(0) == '-';
   }

   /** Whether the option or flag was given, e.g. {@code hasOption('d')}. */
   public boolean hasOption(Character name) {
      return this.hasOption(name.toString());
   }

   /** Whether any one of the alternative names (such as {@code "d:dir"}) was given. */
   public boolean hasOption(String names) {
      for (String name : splitNames(names)) {
         if (this.optionValues.containsKey(name)) {
            return true;
         }
      }

      return false;
   }

   /** The value given for the option, or null when it was not given. */
   public String getOption(Character name) {
      return this.getOption(name.toString());
   }

   /** The value given for any of the alternative names, or null when none was given. */
   public String getOption(String names) {
      return this.getOption(names, null);
   }

   /** The first of the alternative names that was given, read as a whole number, else {@code defaultValue}. */
   public int getIntOption(String names, int defaultValue) {
      for (String name : splitNames(names)) {
         if (this.hasOption(name)) {
            return Integer.parseInt(this.getOption(name));
         }
      }

      return defaultValue;
   }

   /** Whether any of the alternative names (such as {@code "v:verbose"}) was given as a flag. */
   public boolean hasFlag(String names) {
      return this.hasOption(names);
   }

   /** The value given for any of the alternative names, or {@code defaultValue} when none was given. */
   public String getOption(String names, String defaultValue) {
      for (String name : splitNames(names)) {
         String value = this.optionValues.get(name);
         if (value != null) {
            return value;
         }
      }

      return defaultValue;
   }

   /** An option that was never given reads as null; one given with no value reads as "". Both count as missing. */
   private static boolean isBlank(String optionValue) {
      return optionValue == null || optionValue.isEmpty();
   }

   private static List<String> splitNames(String names) {
      List<String> nameList = new ArrayList<>();
      StringTokenizer tokens = new StringTokenizer(names, OPTION_NAME_SEPARATORS, false);
      while (tokens.hasMoreTokens()) {
         nameList.add(tokens.nextToken());
      }
      return nameList;
   }
}
