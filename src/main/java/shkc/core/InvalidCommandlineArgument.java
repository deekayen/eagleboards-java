package shkc.core;

/** Thrown when the command line names an option the program does not know. */
public class InvalidCommandlineArgument extends Exception {
   private static final long serialVersionUID = 1L;

   public InvalidCommandlineArgument() {
   }

   public InvalidCommandlineArgument(String message) {
      super(message);
   }
}
