// ------------------------------------------------------------------------
// -- $Id: process_reset.js,v 1.1 2015/11/18 15:00:40 sking Exp $
// ------------------------------------------------------------------------
//-- Copyright (c) 2001-2010 by Monfox, LLC.  ALL RIGHTS RESERVED
//--
//-- This software, and the ideas, mechanisms and algorithms expressed
//-- therein, is the intellectual and material property of Monfox, LLC
//-- and is provided for use under applicable license agreement only.
//-- No title to or ownership of the software is hereby transferred. No
//-- license to copy, modify, distribute, translate, decompile, reverse
//-- engineer or otherwise remanufacture this software except under the
//-- above license is granted.
//------------------------------------------------------------------------

var resetForm = null;

function InitializeResetBoard()
{
   // completePopup = new dhtmlXPopup({
   //    toolbar: schedulerScoutGrid.getToolbar(),
   //    id: "Complete" //attaches popup to the "Seat" button
   // });


   if (resetForm !== null)
   {
      return;
   }

   resetPopup = schedulerScoutGrid.getResetPopup();

   resetForm = resetPopup.attachForm([{type: "button", name: "Confirm", value: "Confirm"}]);

   resetForm.attachEvent("onButtonClick", function(name) {
      // your code here

      if (name == "Confirm")
      {
         // Should have been validated by original toolbar click
         var s_id = schedulerScoutGrid.getSelectedRowId();
         if (!s_id)
         {
            //alert("No Scout Selected !!");
            dhtmlx.alert({
               title: "Reset Error",
               type: "alert-error",
               text: "No Scout Selected !!"
            });
            return;
         }
         //alert("completing");

         // alert("Results: " + result + "\n\n notes: " + notes);

         resetPopup.hide();
         SendResetRequest(s_id);
      }
      else
      {
         resetPopup.hide();
      }
   });
}

function ProcessResetBoard(s_id)
{
   InitializeResetBoard();
   // var s_last = scoutGrid.cells(s_id, 1).getValue();
   // var s_first = scoutGrid.cells(s_id, 2).getValue();
   // var s_status = scoutGrid.cells(s_id, 7).getValue();

   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");
   var s_status = schedulerScoutGrid.getColumnValue(s_id, "Status");

   if (s_status === "Verified" || s_status === "Seated" || s_status === "InProgress")
   {
      //SendResetRequest(s_id);
      resetPopup.show("Reset");
   }
   else
   {
      dhtmlx.alert({
         title: "Reset Error",
         type: "alert-error",
         text: "Reset Error<br/>Invalid Status: '" + s_status + "'<br/>Expected: 'Verified' | 'Seated' | 'InProgress'"
      });
      return;
   }
}


function SendResetRequest(s_id)
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
                  title: "Reset Error",
                  type: "alert-error",
                  text: s_first + " " + s_last
                          + " Reset failed.<br/> " + xmlHttp.responseText
               });
            }
            else
            {
               dhtmlx.message({
                  title: "Reset",
                  type: "OK",
                  text: s_first + " " + s_last + " Reset OK"
               });
            }
         } else {
            dhtmlx.alert({
               title: "Reset Error",
               type: "alert-error",
               text: s_first + " " + s_last + " Reset Failed."
            });
         }
      }
      setTimeout(function() {
         refresh_all();
      }, 500);
   }

   xmlHttp.open("GET", "/reset-board?ScoutID=" + s_id, false);
   xmlHttp.send(null);
}