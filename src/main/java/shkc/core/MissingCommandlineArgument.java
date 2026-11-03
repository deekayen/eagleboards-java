package shkc.core;

/** Thrown when a command-line option the program requires was not given. */
public class MissingCommandlineArgument extends Exception {
   private static final long serialVersionUID = 1L;

   public MissingCommandlineArgument() {
   }

   public MissingCommandlineArgument(String message) {
      super(message);
   }
}
