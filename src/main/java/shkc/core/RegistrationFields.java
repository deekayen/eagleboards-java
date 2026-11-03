package shkc.core;

/**
 * The columns a sign-in form fills in, one list for a youth and one for an
 * adult. A sign-in copies only these columns onto an existing record, and an
 * edit of an adult on the Adults tab is carried to the adult history by the
 * same list ({@link AdultUpdateHandler}).
 */
public final class RegistrationFields {
   // No DOB (SPEC.md D-7): signing in again never writes a birthdate, and
   // leaves one already on file untouched (O-5). No Phone either (D-8): a
   // youth's number already on file is left alone the same way.
   /** The columns a youth's sign-in writes. */
   public static final String[] SCOUT_FIELDS = new String[]{"First", "Last", "Unit", "UnitType", "Email", "Leader"};

   /** The columns an adult's sign-in writes (the roles are ProjectReview and FinalBoard). */
   public static final String[] ADULT_FIELDS = new String[]{"First", "Last", "Unit", "UnitType", "Email", "Phone", "ProjectReview", "FinalBoard"};

   private RegistrationFields() {
   }
}
