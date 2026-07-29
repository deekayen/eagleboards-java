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
      String var2 = this.getUnitType();
      String var3 = this.getUnit();
      if (var2.length() > 0) {
         var2 = var2.substring(0, 1);
      }

      this.setValue("UnitName", var2 + var3);
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
