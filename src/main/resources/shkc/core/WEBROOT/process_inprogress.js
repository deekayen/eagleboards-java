// ------------------------------------------------------------------------
// -- $Id: process_inprogress.js,v 1.3 2015/02/18 01:16:55 sking Exp $
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

var inProgressForm = null;

function InitializeInProgressBoard()
{
   // completePopup = new dhtmlXPopup({
   //    toolbar: schedulerScoutGrid.getToolbar(),
   //    id: "Complete" //attaches popup to the "Seat" button
   // });


   if (inProgressForm !== null)
   {
      return;
   }

   inProgressPopup = schedulerScoutGrid.getInProgressPopup();

   inProgressForm = inProgressPopup.attachForm([{type: "button", name: "Confirm", value: "Confirm"}]);

   inProgressForm.attachEvent("onButtonClick", function(name) {
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

         inProgressPopup.hide();
         SendInProgressRequest(s_id);
      }
      else
      {
         inProgressPopup.hide();
      }
   });
}

function ProcessInProgressBoard(s_id)
{
   
    InitializeInProgressBoard();

   // var s_last = scoutGrid.cells(s_id, 1).getValue();
   // var s_first = scoutGrid.cells(s_id, 2).getValue();
   // var s_status = scoutGrid.cells(s_id, 7).getValue();

   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");
   var s_status = schedulerScoutGrid.getColumnValue(s_id, "Status");

   if (s_status == "Seated")
   {
      // SendInProgressRequest(s_id);
      inProgressPopup.show("InProgress");
   }
   else
   {
      dhtmlx.alert({
         title: "Complete Error",
         type: "alert-error",
         text: "Invalid Status: '" + s_status + "'<br/>Expected: 'Seated'"
      });
      return;
   }
}


function SendInProgressRequest(s_id)
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
                  title: "Start Error",
                  type: "alert-error",
                  text: s_first + " " + s_last
                          + " Start failed.<br/> " + xmlHttp.responseText
               });
            }
            else
            {
               dhtmlx.message({
                  title: "Started",
                  type: "OK",
                  text: s_first + " " + s_last + " Started OK"
               });
               
               SCHEDULER_locateAdults(s_id,false);
            }
         } else {
            dhtmlx.alert({
               title: "Start Error",
               type: "alert-error",
               text: s_first + " " + s_last + " Start Failed."
            });
         }
      }
      setTimeout(function() {
         refresh_all();
      }, 500);
   }

   xmlHttp.open("GET", "/inprogress-board?ScoutID=" + s_id, false);
   xmlHttp.send(null);
}