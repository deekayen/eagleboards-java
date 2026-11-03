package shkc.core;

import java.util.Map;

/**
 * Loads the youth rows of a pre-registration sheet (a CSV export) into the
 * scouts-scheduled file, so that sign-in can pre-fill a scout who RSVP'd.
 *
 * <p>Which column feeds which field is decided by the converters registered in
 * the constructor; the converters themselves are in
 * {@link PreRegFieldConverters}.
 */
public class NegaPreRegScoutRecordConverter extends DataFileConverter<ScoutRecord> {
   /**
    * Sheet headings that are renamed before being matched to a record column.
    * A heading mapped to "" matches no column, so it is ignored.
    */
   private static final Map<String, String> COLUMN_MAP = Map.of(
      "Email", "Email",
      "id", "",
      "ScoutMasters Name", "Scoutmaster");

   // SPEC.md D-8: no youth's phone number is imported. "Scouts Contact Number"
   // stays among the columns the sheet must have, since that is how the file
   // is recognized, but nothing reads it any more.
   public NegaPreRegScoutRecordConverter(DataRecordFile<ScoutRecord> dataFile) {
      super(dataFile, COLUMN_MAP, PreRegFieldConverters.REQUIRED_SHEET_COLUMNS);
      this.addConverter("First Name", PreRegFieldConverters.FIRST_NAME);
      this.addConverter("Last Name", PreRegFieldConverters.LAST_NAME);
      this.addConverter("request_status", PreRegFieldConverters.NOT_CANCELLED);
      this.addConverter("Item", PreRegFieldConverters.SCOUT_BOARD_TYPE);
      this.addConverter("Unit Number", PreRegFieldConverters.UNIT_AS_TYPED);
      this.addConverter("Scoutmasters Name", PreRegFieldConverters.LEADER);
      this.addConverter("Life-to-Eagle Coach", PreRegFieldConverters.LEADER);
   }

   // And any other column that would land in Phone, whatever the sheet calls
   // it, is dropped before the record is kept.
   @Override
   public void processNewRecord(ScoutRecord record) {
      record.setValue("Phone", "");
      super.processNewRecord(record);
   }
}
