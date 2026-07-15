package shkc.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import shkc.json.simple.JSONArray;
import shkc.json.simple.JSONObject;
import shkc.json.simple.JSONValue;

public class SignUpGeniusPlugin {
   private String _key;
   private String _signupID;
   private NegaPreRegScoutRecordConverter.FirstNameConverter _firstNameConverter = new NegaPreRegScoutRecordConverter.FirstNameConverter();
   private NegaPreRegScoutRecordConverter.LastNameConverter _lastNameConverter = new NegaPreRegScoutRecordConverter.LastNameConverter();
   private NegaPreRegScoutRecordConverter.PhoneConverter _phoneConverter = new NegaPreRegScoutRecordConverter.PhoneConverter();
   private NegaPreRegScoutRecordConverter.LeaderConverter _leaderConverter = new NegaPreRegScoutRecordConverter.LeaderConverter();
   private NegaPreRegScoutRecordConverter.StatusConverter _statusConverter = new NegaPreRegScoutRecordConverter.StatusConverter();
   private NegaPreRegScoutRecordConverter.UnitConverter _unitConverter = new NegaPreRegScoutRecordConverter.UnitConverter();
   private NegaPreRegScoutRecordConverter.BoardTypeConverter _boardTypeConverter = new NegaPreRegScoutRecordConverter.BoardTypeConverter();
   private DataRecordFile<AdultRecord> _adultHistoryRecords;
   private DataRecordFile<ScoutRecord> _scoutsScheduledRecords;

   public static void main(String[] var0) throws Exception {
      String var1 = "REPLACE_WITH_SIGNUP_GENIUS_KEY";
      SignUpGeniusPlugin var2 = new SignUpGeniusPlugin(
         var1,
         null,
         new DataRecordFile<>(new File("adult_test.csv"), new AdultRecord.Factory()),
         new DataRecordFile<>(new File("scout_test.csv"), new ScoutRecord.Factory())
      );
      var2.populatePreRegistrations();
   }

   public SignUpGeniusPlugin(String var1, String var2, DataRecordFile<AdultRecord> var3, DataRecordFile<ScoutRecord> var4) {
      this._key = var1;
      this._signupID = var2;
      this._adultHistoryRecords = var3;
      this._scoutsScheduledRecords = var4;
   }

   public void populatePreRegistrations() throws Exception {
      System.out.println("\n\n   Loading Preregistrations from SignupGenius: " + this._key + "\n");
      if (this._signupID == null) {
         System.out.println("\n   Looking up SignupID from SignupGenius...\n");
         this._signupID = this.getSignupID();
      }

      System.out.println("\n   SignupID : " + this._signupID + "\n");
      FileLocator var1 = new FileLocator(".");

      InputStream var2;
      try {
         String var3 = "https://api.signupgenius.com/v2/k/signups/report/filled/" + this._signupID + "/?user_key=" + this._key;
         EagleBoardScheduler.verbose("REQUESTING: signup data (URL: " + var3 + ")");
         var2 = var1.getInputStream(var3);
      } catch (IOException var22) {
         EagleBoardScheduler.verbose("signup request error: " + var22);
         throw new Exception("error connecting to SignUpGenius server (" + var22.getMessage());
      }

      JSONObject var23 = (JSONObject)JSONValue.parseWithException(new InputStreamReader(var2));
      SimpleDateFormat var4 = new SimpleDateFormat("yyyy-MM");
      String var5 = var4.format(new Date());
      JSONObject var6 = (JSONObject)var23.get("data");
      JSONArray var7 = (JSONArray)var6.get("signup");

      for (int var8 = 0; var8 < var7.size(); var8++) {
         JSONObject var9 = (JSONObject)var7.get(var8);
         String var10 = var9.get("startdatestring").toString();
         if (var10.startsWith(var5)) {
            String var11 = var9.get("firstname").toString();
            String var12 = var9.get("lastname").toString();
            String var13 = var9.get("item").toString();
            String var14 = var9.get("email").toString();
            JSONArray var15 = (JSONArray)var9.get("customfields");
            String var16 = this.getCustomField(var15, 0);
            String var17 = this.getCustomField(var15, 1);
            String var18 = this.getCustomField(var15, 2);
            String var19 = this.getCustomField(var15, 3);
            EagleBoardScheduler.verbose(" EVALUATING: " + var11 + " " + var12 + " (" + var13 + ")");
            if (var13.toLowerCase().contains("adult")) {
               if (this._adultHistoryRecords != null) {
                  AdultRecord var20 = this._adultHistoryRecords.createNew();
                  this._firstNameConverter.convert(var20, "", var11);
                  this._lastNameConverter.convert(var20, "", var12);
                  this._phoneConverter.convert(var20, "", var17);
                  var20.setValue("ProjectReview", "Member");
                  var20.setValue("FinalBoard", "Member");
                  this._unitConverter.convert(var20, "", var16);
                  var20.setValue("Email", var14);
                  List var21 = this._adultHistoryRecords.get("Email", var14);
                  if (var21 != null && var21.size() != 0) {
                     if (var21.size() == 1) {
                        EagleBoardScheduler.verbose("SIGNUP-GENIUS/UPDATING ADULT : " + var21.get(0));
                        ((AdultRecord)var21.get(0)).updateFrom(var20, new String[]{"Phone"});
                     }
                  } else {
                     EagleBoardScheduler.verbose("SIGNUP-GENIUS/ADDING ADULT " + var20);
                     this._adultHistoryRecords.add(var20, false);
                  }

                  this._adultHistoryRecords.store();
               }
            } else if (this._scoutsScheduledRecords != null) {
               ScoutRecord var24 = this._scoutsScheduledRecords.createNew();
               this._firstNameConverter.convert(var24, "", var11);
               this._lastNameConverter.convert(var24, "", var12);
               this._phoneConverter.convert(var24, "", var17);
               this._boardTypeConverter.convert(var24, "", var13);
               this._unitConverter.convert(var24, "", var16);
               this._leaderConverter.convert(var24, "", var18);
               var24.setValue("Email", var14);
               var24.setValue("ID", "SCOUT:" + var24.getLast() + ":" + var24.getFirst() + ":" + var24.getUnit());
               List var25 = this._scoutsScheduledRecords.get("Email", var14);
               if (var25 == null || var25.size() == 0) {
                  this._scoutsScheduledRecords.add(var24, true);
                  EagleBoardScheduler.verbose("SIGNUP-GENIUS/ADDING SCOUT " + var24);
               }
            }
         }
      }

      this._scoutsScheduledRecords.store();
   }

   private String getCustomField(JSONArray var1, int var2) {
      try {
         JSONObject var3 = (JSONObject)var1.get(var2);
         return var3.get("value").toString();
      } catch (Exception var4) {
         return "";
      }
   }

   private String getSignupID() throws Exception {
      FileLocator var1 = new FileLocator(".");

      InputStream var2;
      try {
         var2 = var1.getInputStream("https://api.signupgenius.com/v2/k/signups/created/active/?user_key=" + this._key);
      } catch (IOException var13) {
         throw new Exception("error connecting to SignUpGenius server (" + var13.getMessage());
      }

      JSONObject var3 = (JSONObject)JSONValue.parseWithException(new InputStreamReader(var2));
      SimpleDateFormat var4 = new SimpleDateFormat("yyyy-MM-dd");
      String var5 = var4.format(new Date());
      JSONArray var6 = (JSONArray)var3.get("data");

      for (int var7 = 0; var7 < var6.size(); var7++) {
         JSONObject var8 = (JSONObject)var6.get(var7);
         String var9 = var8.get("signupid").toString();
         String var10 = var8.get("enddatestring").toString();
         String var11 = var8.get("title").toString();
         String var12 = var8.get("startdatestring").toString();
         if (var5.compareTo(var10) <= 0 && var5.compareTo(var12) >= 0 && var11.toLowerCase().contains("board") && var11.toLowerCase().contains("eagle")) {
            System.out
               .println(
                  "Using SignUp {\n   id         : "
                     + var9
                     + "\n   title      : "
                     + var11
                     + "\n   start-date : "
                     + var12
                     + "\n   end-date   : "
                     + var10
                     + "\n}"
               );
            return var9;
         }

         System.out.println("out of date range: " + var11 + "(" + var9 + ")");
      }

      throw new Exception("no valid )in range SignUpIds found.");
   }
}
