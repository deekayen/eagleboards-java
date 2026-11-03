package shkc.core;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Console logging for the whole program.
 *
 * <p>There is one switch, turned on by the {@code -v} / {@code -verbose}
 * command-line flag. When it is off, {@link #verbose(Object)} prints nothing.
 * {@link #error(String)} always prints, followed by a stack dump so the caller
 * that hit the problem can be found.
 *
 * <p>This class only holds static state and cannot be instantiated.
 */
public final class Log {
   private static boolean verbose = false;

   private Log() {
   }

   /** Turns verbose messages on or off. Called once from {@code main}. */
   public static void setVerbose(boolean enabled) {
      verbose = enabled;
   }

   /** Prints {@code message} to standard output, but only in verbose mode. */
   public static void verbose(Object message) {
      if (verbose) {
         System.out.println(message);
      }
   }

   /** Logs a request and its parameters, the way every handler starts. */
   public static void request(HttpServletRequest request) {
      verbose(request);
      verbose(request.getParameterMap());
   }

   /** Prints an error message, then the current stack, whether or not verbose is on. */
   public static void error(String message) {
      System.out.println("\n\nERROR: " + message + "\n");
      Thread.dumpStack();
   }
}
