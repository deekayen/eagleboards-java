package shkc.core;

class InvalidCommandlineArgument extends Throwable {
   public InvalidCommandlineArgument() {
   }

   public InvalidCommandlineArgument(String message) {
      super(message);
   }
}
