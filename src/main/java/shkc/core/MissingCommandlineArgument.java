package shkc.core;

class MissingCommandlineArgument extends Throwable {
   public MissingCommandlineArgument() {
   }

   public MissingCommandlineArgument(String message) {
      super(message);
   }
}
