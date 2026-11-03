package shkc.core;

import java.util.List;
import java.util.Map;

/**
 * Loads the adult rows of a pre-registration sheet (a CSV export) into the
 * adult history, so that sign-in can pre-fill an adult who volunteered.
 *
 * <p>Which column feeds which field is decided by the converters registered in
 * the constructor; the converters themselves are in
 * {@link PreRegFieldConverters}. An adult already known by ID or by email is
 * updated rather than added a second time (see {@link #processNewRecord}).
 */
public class NegaPreRegAdultRecordConverter extends DataFileConverter<AdultRecord> {
   /**
    * Sheet headings that are renamed before being matched to a record column.
    * A heading mapped to "" matches no column, so it is ignored.
    */
   private static final Map<String, String> COLUMN_MAP = Map.of(
      "email", "Email",
      "Unit Type", "UnitType",
      "Unit Number", "Unit",
      "id", "");

   /** What a sheet row may change on an adult found by email alone. */
   private static final String[] COLUMNS_UPDATED_BY_EMAIL_MATCH = {"Phone"};
   /** What a sheet row may change on an adult found by ID. */
   private static final String[] COLUMNS_UPDATED_BY_ID_MATCH = {"Phone", "Email"};

   public NegaPreRegAdultRecordConverter(DataRecordFile<AdultRecord> dataFile) {
      super(dataFile, COLUMN_MAP, PreRegFieldConverters.REQUIRED_SHEET_COLUMNS);
      this.addConverter("name", PreRegFieldConverters.FULL_NAME);
      this.addConverter("phone", PreRegFieldConverters.PHONE);
      this.addConverter("request_status", PreRegFieldConverters.NOT_CANCELLED);
      this.addConverter("Item", PreRegFieldConverters.ADULT_BOARD_TYPE);
      this.addConverter("UnitNumber", PreRegFieldConverters.UNIT_TRIMMED);
      this.addConverter("First Name", PreRegFieldConverters.FIRST_NAME);
      this.addConverter("Last Name", PreRegFieldConverters.LAST_NAME);
      this.addConverter("Scouts Contact Number", PreRegFieldConverters.PHONE);
      this.addConverter("Unit Number", PreRegFieldConverters.UNIT_TRIMMED);
   }

   /**
    * Adds a new adult, or refreshes one already on file. An adult with the
    * same ID is updated; failing that, one with the same email is updated, but
    * only when exactly one adult has that email (if several do, nothing is
    * guessed and the row is dropped).
    */
   @Override
   public void processNewRecord(AdultRecord incoming) {
      DataRecordFile<AdultRecord> dataFile = this.getDataFile();
      AdultRecord existing = dataFile.get(incoming.getID());
      if (existing != null) {
         Log.verbose("UPDATING PREREG ADULT RECORD[2]: " + existing);
         existing.updateFrom(incoming, COLUMNS_UPDATED_BY_ID_MATCH);
         return;
      }

      List<AdultRecord> sameEmail = dataFile.get("Email", incoming.getEmail());
      if (sameEmail.isEmpty()) {
         Log.verbose("ADDING NEW PREREG ADULT RECORD: " + incoming);
         dataFile.add(incoming, false);
      } else if (sameEmail.size() == 1) {
         Log.verbose("UPDATING PREREG ADULT RECORD[1]: " + sameEmail.get(0));
         sameEmail.get(0).updateFrom(incoming, COLUMNS_UPDATED_BY_EMAIL_MATCH);
      }
   }
}
