// ------------------------------------------------------------------------
// -- $Id: process_postpone.js,v 1.2 2015/02/01 04:00:32 sking Exp $
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

var postponeForm = null;

function InitializePostponeBoard()
{
   // completePopup = new dhtmlXPopup({
   //    toolbar: schedulerScoutGrid.getToolbar(),
   //    id: "Complete" //attaches popup to the "Seat" button
   // });


   if (postponeForm !== null)
   {
      return;
   }

   postponePopup = schedulerScoutGrid.getPostponePopup();

   postponeForm = postponePopup.attachForm([{type: "button", name: "Confirm", value: "Confirm"}]);

   postponeForm.attachEvent("onButtonClick", function(name) {
      // your code here

      if (name == "Confirm")
      {
         // Should have been validated by original toolbar click
         var s_id = schedulerScoutGrid.getSelectedRowId();
         if (!s_id)
         {
            //alert("No Scout Selected !!");
            dhtmlx.alert({
               title: "Postpone Error",
               type: "alert-error",
               text: "No Scout Selected !!"
            });
            return;
         }
         //alert("completing");

         // alert("Results: " + result + "\n\n notes: " + notes);

         postponePopup.hide();
         SendPostponeRequest(s_id);
      }
      else
      {
         postponePopup.hide();
      }
   });
}

function ProcessPostponeBoard(s_id)
{
   InitializePostponeBoard();
   // var s_last = scoutGrid.cells(s_id, 1).getValue();
   // var s_first = scoutGrid.cells(s_id, 2).getValue();
   // var s_status = scoutGrid.cells(s_id, 7).getValue();

   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");
   var s_status = schedulerScoutGrid.getColumnValue(s_id, "Status");

   if (s_status === "Registered" || s_status === "Verified")
   {
      //SendPostponeRequest(s_id);
      postponePopup.show("Postpone");
   }
   else
   {
      dhtmlx.alert({
         title: "Postpone Error",
         type: "alert-error",
         text: "Postpone Error<br/>Invalid Status: '" + s_status + "'<br/>Expected: 'Registered' | 'Verified'"
      });
      return;
   }
}


function SendPostponeRequest(s_id)
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
                  title: "Postpone Error",
                  type: "alert-error",
                  text: s_first + " " + s_last
                          + " Postpone failed.<br/> " + xmlHttp.responseText
               });
            }
            else
            {
               dhtmlx.message({
                  title: "Postpone",
                  type: "OK",
                  text: s_first + " " + s_last + " Postpone OK"
               });
            }
         } else {
            dhtmlx.alert({
               title: "Postpone Error",
               type: "alert-error",
               text: s_first + " " + s_last + " Postpone Failed."
            });
         }
      }
      setTimeout(function() {
         refresh_all();
      }, 500);
   }

   xmlHttp.open("GET", "/postpone-board?ScoutID=" + s_id, false);
   xmlHttp.send(null);
}