package shkc.core;

import java.util.Map;

public class PersonRecord extends DataRecord {

   public PersonRecord(String var1, String[] var2, Map var3) {
      super(var1, var2, var3);
      if (this.getLast().length() > 0 || this.getUnit() != null) {
         this.populateID();
      }
   }

   @Override
   public void updateFields(boolean var1) {
      super.updateFields(var1);
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

   public void populateID() {
      if (this.getID() == null || this.getID().length() == 0 || !this.getID().startsWith(this.getType()) || this.getID().equals(this.getType() + ":::")) {
         String var1 = this.getType() + ":" + this.getLast() + ":" + this.getFirst() + ":" + this.getUnit();
         this.put("ID", var1);
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

   public String getPhone() {
      return this.getValue("Phone");
   }

   public String getShortName() {
      String var1 = this.getFirst();
      String var2 = "";
      if (var1.length() > 0) {
         var2 = this.getFirst().charAt(0) + ". ";
      }

      return var2 + this.getLast();
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

   public String getUnitName() {
      return this.getValue("UnitName");
   }

   public String getRoom() {
      return this.getValue("Room");
   }

   public void setRoom(String var1) {
      this.setValue("Room", var1);
   }

   public void setFlags(String var1) {
      this.setValue("Flags", var1);
   }

   public String getFlags() {
      return this.getValue("Flags");
   }
}
