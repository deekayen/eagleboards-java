package shkc.core;

import java.util.Map;

/**
 * The columns a scout and an adult have in common: name, unit, email, the room
 * they are in. Also works out the record's ID from the person's name and unit,
 * and the display columns {@code UnitName} and {@code ShortName}.
 */
public class PersonRecord extends DataRecord {

   public PersonRecord(String recordType, String[] columns, Map<String, ?> values) {
      super(recordType, columns, values);
      this.populateID();
   }

   /** Rebuilds {@code UnitName} and {@code ShortName} from the columns they are made of. */
   @Override
   public void updateFields(boolean markUpdated) {
      super.updateFields(markUpdated);
      // UnitName is the unit type and number as one label, e.g. "Troop1776".
      //
      // The original abbreviated the type to its first letter ("T1776"), which
      // was already ambiguous -- Pack and Post both produced "P" -- and adding
      // the District, Council and Community options made it worse, because
      // Council, Community and Crew would all have collided on "C". Nothing
      // reads this value back apart from the retired adult/scout ratio
      // calculation, and a CSV column has no width limit to justify the
      // truncation, so the whole word is written.
      String unitType = this.getUnitType();
      String unitNumber = this.getUnit();
      this.setValue("UnitName", unitType + unitNumber);
      this.setValue("ShortName", this.getShortName());
   }

   /**
    * Gives the record an ID of the form {@code Type:Last:First:Unit} when it
    * has none, or when its ID is not one of this type's (or is the empty
    * placeholder {@code Type:::}). An ID that is already set is kept.
    */
   public void populateID() {
      String id = this.getID();
      String type = this.getType();
      if (id.length() == 0 || !id.startsWith(type) || id.equals(type + ":::")) {
         // No commas in an ID. MemberIDs travels as a comma-separated list, so
         // "Smith, Jr." split into two unknown ids and that adult could never
         // be seated; and the CSV writer turns ',' into '~' anyway, so the id
         // changed under everyone at the next restart. '~' is what it became.
         String newId = (type + ":" + this.getLast() + ":" + this.getFirst() + ":" + this.getUnit()).replace(',', '~');
         this.put(COLUMN_ID, newId);
      }
   }

   @Override
   public void postLoadUpdate() {
      this.populateID();
      this.updateFields(false);
   }

   public String getEmail() {
      return this.getValue("Email");
   }

   /** The first initial and last name, such as {@code J. Smith}; just the last name if there is no first name. */
   public String getShortName() {
      String first = this.getFirst();
      String initial = "";
      if (first.length() > 0) {
         initial = first.charAt(0) + ". ";
      }

      return initial + this.getLast();
   }

   public String getFullName() {
      return this.getFirst() + " " + this.getLast();
   }

   public String getFirst() {
      return this.getValue("First");
   }

   public String getLast() {
      return this.getValue("Last");
   }

   public String getUnit() {
      return this.getValue("Unit");
   }

   public String getUnitType() {
      return this.getValue("UnitType");
   }

   public String getRoom() {
      return this.getValue("Room");
   }

   public void setRoom(String room) {
      this.setValue("Room", room);
   }

   public void setFlags(String flags) {
      this.setValue("Flags", flags);
   }

}
