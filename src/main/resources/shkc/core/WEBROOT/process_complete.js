// ------------------------------------------------------------------------
// -- $Id: process_complete.js,v 1.5 2017/09/13 18:44:35 sking Exp $
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

var completePopup = null;

function InitializeCompleteBoard()
{
   // completePopup = new dhtmlXPopup({
   //    toolbar: schedulerScoutGrid.getToolbar(),
   //    id: "Complete" //attaches popup to the "Seat" button
   // });

   completePopup = schedulerScoutGrid.getCompletePopup();

   completeForm = completePopup.attachForm([
      {type: "select", label: "Board Result", name: "Result", options: [
            {text: "Approved", value: "Approved", selected: true},
            {text: "Suspended", value: "Suspended"},
            {text: "NotApproved", value: "NotApproved"}

         ]},
      {type: 'editor', name: 'Notes', id: 'Notes',
         labelWidth: 120,
         labelAlign: 'right', inputWidth: 300, inputHeight: 100, value: "Completion Notes..."},
      {type: 'input', name: 'Cost', id: 'Cost',
         labelWidth: 100, label: 'Project Cost:',
         labelAlign: 'left', inputWidth: 60, value: ""},
      {type: 'input', name: 'BSAHours', id: 'BSAHours',
         labelWidth: 100, label: 'BSA Hours:',
         labelAlign: 'left', inputWidth: 60, value: ""},
      {type: 'input', name: 'OtherHours', id: 'OtherHours',
         labelWidth: 100, label: 'Other Hours:',
         labelAlign: 'left', inputWidth: 60, value: ""},
      {type: "block", list: [
            {type: "button", name: "Complete", value: "Complete"},
            {type: "newcolumn"},
            {type: "button", name: "Cancel", offsetLeft: 80, value: "Cancel"}
         ]}

   ]);

   completeForm.attachEvent("onButtonClick", function(name) {
      // your code here

      if (name == "Complete")
      {
         // Should have been validated by original toolbar click
         var s_id = scoutGrid.getSelectedRowId();
         if (!s_id)
         {
            //alert("No Scout Selected !!");
            dhtmlx.alert({
               title: "Complete Error",
               type: "alert-error",
               text: "No Scout Selected !!"
            });
            return;
         }
         //alert("completing");
         var result = completeForm.getItemValue("Result");
         var notes = completeForm.getItemValue("Notes");
         var project_cost = completeForm.getItemValue("Cost");
         var bsa_hours = completeForm.getItemValue("BSAHours");
         var other_hours = completeForm.getItemValue("OtherHours");
         
         console.log("NOTES: " + notes);
         var sidx = notes.indexOf('<');
         var lidx = notes.lastIndexOf('>');
         if (sidx>=0 && lidx>sidx)
         {
            notes = notes.substring(0,sidx);
         }

         // alert("Results: " + result + "\n\n notes: " + notes);

         completePopup.hide();
         SendCompleteRequest(s_id, result, notes, project_cost, bsa_hours, other_hours);

         completeForm.setItemValue("Cost", "");
         completeForm.setItemValue("BSAHours", "");
         completeForm.setItemValue("OtherHours", "");
         completeForm.setItemValue("Notes", "Completion Notes...");
         completeForm.setItemValue("Result", "Approved");
      }
      else if (name == "Cancel")
      {
         completePopup.hide();
      }
   });
}

function ProcessCompleteBoard(s_id)
{
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last"); // .cells(s_id, 1).getValue();
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");
   var s_uname = schedulerScoutGrid.getColumnValue(s_id, "UnitName");
   var s_btype = schedulerScoutGrid.getColumnValue(s_id, "BoardType");
   var s_room = schedulerScoutGrid.getColumnValue(s_id, "Room");
   var s_status = schedulerScoutGrid.getColumnValue(s_id, "Status");

   if (s_status == "Completed")
   {
      dhtmlx.alert({
         title: "Complete Error",
         type: "alert-error",
         text: s_first + " " + s_last + " has already completed his board "
      });
      return;
   }
   else if (s_status == "InProgress")
   {
      // alert("InProgress .. all good");
      completePopup.show("Complete");
   }
   else
   {
      dhtmlx.alert({
         title: "Complete Error",
         type: "alert-error",
         text: s_first + " " + s_last + " has not been seated yet."
      });
      return;
   }
}

function SendCompleteRequest(s_id, result, notes, project_cost, bsa_hours, other_hours)
{
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last"); // .cells(s_id, 1).getValue();
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");

   var xmlHttp = null;

   xmlHttp = new XMLHttpRequest();
   xmlHttp.onreadystatechange = function() {
      if (xmlHttp.readyState === 4) {
         if (xmlHttp.status === 200) {
            if (xmlHttp.responseText.length > 4)
            {
               dhtmlx.alert({
                  title: "Complete Error",
                  type: "alert-error",
                  text: s_first + " " + s_last
                          + " complete failed.<br/> " + xmlHttp.responseText
               });
            }
            else
            {
               dhtmlx.message({
                  title: "Completed",
                  type: "OK",
                  text: s_first + " " + s_last + " Completed OK"
               });

               SCHEDULER_locateAdults(s_id, true);
            }
         } else {
            dhtmlx.alert({
               title: "Complete Error",
               type: "alert-error",
               text: s_first + " " + s_last + " Complete Failed."
            });
         }
      }
      setTimeout(function() {
         refresh_all();
      }, 500);
   }
   
   var query = "/complete-board?ScoutID=" + s_id + "&Result=" + result
           + "&Notes=" + urlEntities(notes)
           + "&Cost=" + urlEntities(project_cost)
           + "&BSAHours=" + urlEntities(bsa_hours)
           + "&OtherHours=" + urlEntities(other_hours);
   console.log("QUERY: " + query);
   xmlHttp.open("GET", query,false);

/*
   xmlHttp.open("GET", "/complete-board?ScoutID=" + s_id + "&Result=" + result
           + "&Notes=" + urlEntities(notes)
           + "&Cost=" + urlEntities(project_cost)
           + "&BSAHours=" + urlEntities(bsa_hours)
           + "&OtherHours=" + urlEntities(other_hours),
           false); */
   
   xmlHttp.send(null);
}

function urlEntities(str) {
   return String(str).replace(/&/g, 'and').replace(/=/, "eq");
}