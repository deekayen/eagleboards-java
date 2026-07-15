
var verifyForm = null;

function InitializeVerifyBoard()
{

   console.log("InitializeVerifyBoard: already created: verifyPopup");


   if (verifyForm !== null)
   {
      console.log("InitializeVerifyBoard: already created: verifyPopup");
      return;
   }

   console.log("InitializeVerifyBoard: creating verifyPopup");

   verifyPopup = schedulerScoutGrid.getVerifyPopup();

   verifyForm = verifyPopup.attachForm([{type: "button", name: "Confirm", value: "Confirm"}]);

   verifyForm.attachEvent("onButtonClick", function(name) {
      // your code here

      if (name == "Confirm")
      {
         // Should have been validated by original toolbar click
         var s_id = schedulerScoutGrid.getSelectedRowId();
         if (!s_id)
         {
            //alert("No Scout Selected !!");
            dhtmlx.alert({
               title: "Verify Error",
               type: "alert-error",
               text: "No Scout Selected !!"
            });
            return;
         }
         //alert("completing");

         // alert("Results: " + result + "\n\n notes: " + notes);
   console.log("InitializeVerifyBoard: creating verifyPopup");

         verifyPopup.hide();
         SendVerifyRequest(s_id);
      }
      else
      {
            console.log("InitializeVerifyBoard: creating verifyPopup");

         verifyPopup.hide();
      }
   });
}

function ProcessVerifyBoard(s_id)
{
   InitializeVerifyBoard();

   // var s_last = scoutGrid.cells(s_id, 1).getValue();
   // var s_first = scoutGrid.cells(s_id, 2).getValue();
   // var s_status = scoutGrid.cells(s_id, 7).getValue();

   //schedulerScoutGrid.getVerifyPopup().hide();

   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");
   var s_status = schedulerScoutGrid.getColumnValue(s_id, "Status");

   if (s_status == "Registered")
   {
      //SendVerifyRequest(s_id);
      //verifyForm.setItemValue("name", "MY SCOUT");
      console.log("showing Verify Popup");
      schedulerScoutGrid.getVerifyPopup().show("Verify");
   }
   else
   {
      dhtmlx.alert({
         title: "Complete Error",
         type: "alert-error",
         text: "Invalid Status: '" + s_status + "'<br/>Expected: 'Registered'"
      });
      return;
   }
}


function SendVerifyRequest(s_id)
{
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");
   var xmlHttp = null;

   xmlHttp = new XMLHttpRequest();
   xmlHttp.onreadystatechange = function() {
      if (xmlHttp.readyState === 4) {
         if (xmlHttp.status === 200) {
            if (xmlHttp.responseText.length > 4)
            {
               dhtmlx.alert({
                  title: "Verify Error",
                  type: "alert-error",
                  text: s_first + " " + s_last
                          + " Verify Failed.<br/> " + xmlHttp.responseText
               });
            }
            else
            {
               dhtmlx.message({
                  title: "Verified",
                  type: "OK",
                  text: s_first + " " + s_last + " Verified OK, <br/><br/> COLLECT PROJECT COST<br/> &amp; BSA & OTHER HOURS!! "
               });
            }
         } else {
            dhtmlx.alert({
               title: "Verify Error",
               type: "alert-error",
               text: s_first + " " + s_last + " Verify Failed."
            });
         }
      }
      setTimeout(function() {
         refresh_all();
      }, 500);
   }

   xmlHttp.open("GET", "/verify-board?ScoutID=" + s_id, false);
   xmlHttp.send(null);
}