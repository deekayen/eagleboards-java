// ------------------------------------------------------------------------
// -- $Id: scheduler_config.js,v 1.2 2015/02/18 01:16:56 sking Exp $
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


SCHEDULER_refreshTime = 10;
SCHEDULER_SeatedAlertTime = 10;
SCHEDULER_SeatedReminderTime = 5;
SCHEDULER_InProgressAlertTime = 20;
SCHEDULER_InProgessReminderTime = 10;


SCHEDULER_Config = null;

var r = window.dhx4.ajax.getSync("/config-autofill?Name=DEFAULT&fmt=json");
if (r != null)
{
   SCHEDULER_Config = window.dhx4.s2j(r.xmlDoc.responseText); // convert response to json object); // script will wait for response

   if (SCHEDULER_Config)
   {
      SCHEDULER_refreshTime = SCHEDULER_Config.RefreshTimeSecs;
      SCHEDULER_SeatedAlertTime = SCHEDULER_Config.SeatedAlertTimeMins;
      SCHEDULER_SeatedReminderTime = SCHEDULER_Config.SeatedReminderTimeMins;
      SCHEDULER_InProgressAlertTime = SCHEDULER_Config.InProgressAlertTimeMins;
      SCHEDULER_InProgressReminderTime = SCHEDULER_Config.InProgressReminderTimeMins;
   }
   console.log(SCHEDULER_Config);

}



var status2StyleMap = {};
var status2SelectedStyleMap = {};

//status2StyleMap["Registered"] = "background-color: #A60835;";
//status2StyleMap["Verified"] = "background-color: #B07709;";
//status2StyleMap["Seated"] = "background-color: #098CB0;";
//status2StyleMap["InProgress"] = "background-color: #339900;";
//status2StyleMap["Completed"] = "background-color: #ffffff;";
//status2StyleMap["Postponed"] = "background-color: #eeeeee;";


if (SCHEDULER_Config != null)
{
   status2StyleMap["Registered"] = "background-color: " + SCHEDULER_Config.RegisteredColor;
   status2StyleMap["Verified"] = "background-color: " + SCHEDULER_Config.VerifiedColor;
   status2StyleMap["Seated"] = "background-color: " + SCHEDULER_Config.SeatedColor;
   status2StyleMap["InProgress"] = "background-color: " + SCHEDULER_Config.InProgressColor;
   status2StyleMap["Completed"] = "background-color: " + SCHEDULER_Config.CompletedColor;
   status2StyleMap["Postponed"] = "background-color:" + SCHEDULER_Config.PostponedColor;

   status2SelectedStyleMap["Registered"] = "text-decoration: underline !important; background-color: " + SCHEDULER_Config.RegisteredHiColor;
   status2SelectedStyleMap["Verified"] = "text-decoration: underline !important;background-color:  " + SCHEDULER_Config.VerifiedHiColor;
   status2SelectedStyleMap["Seated"] = "text-decoration: underline !important;background-color: " + SCHEDULER_Config.SeatedHiColor;
   status2SelectedStyleMap["InProgress"] = "text-decoration: underline !important; background-color:  " + SCHEDULER_Config.InProgressHiColor;
   status2SelectedStyleMap["Completed"] = "text-decoration: underline !important; background-color:  " + SCHEDULER_Config.CompletedHiColor;
   status2SelectedStyleMap["Postponed"] = "text-decoration: underline !important; background-color: " + SCHEDULER_Config.PostponedHiColor;

}
else
{
   status2StyleMap["Registered"] = "background-color: #A60835;";
   status2StyleMap["Verified"] = "background-color: #B07709;";
   status2StyleMap["Seated"] = "background-color: #098CB0;";
   status2StyleMap["InProgress"] = "background-color: #339900;";
   status2StyleMap["Completed"] = "background-color: #ffffff;";
   status2StyleMap["Postponed"] = "background-color: #eeeeee;";


   status2SelectedStyleMap["Registered"] = "text-decoration: underline !important; background-color: #ff3366; border-top-width: 1px; border-top-color:black; color: #ffffff;";
   status2SelectedStyleMap["Verified"] = "text-decoration: underline !important;background-color: #FF9933; color: #ffffff;";
   status2SelectedStyleMap["Seated"] = "text-decoration: underline !important;background-color: #FFCC66; color: #ffffff;";
   status2SelectedStyleMap["InProgress"] = "text-decoration: underline !important; background-color: #0099ff; color: #ffffff;";
   status2SelectedStyleMap["Completed"] = "text-decoration: underline !important; background-color: #eeeeee; color: #ffffff;";
   status2SelectedStyleMap["Postponed"] = "text-decoration: underline !important; background-color: #cccccc; color: #ffffff;";

}

var status2numMap = {};

status2numMap["Registered"] = 0;
status2numMap["Verified"] = 1;
status2numMap["Seated"] = 2;
status2numMap["InProgress"] = 3;
status2numMap["Completed"] = 4;
status2numMap["Postponed"] = 5;

function sort2num(status)
{
   var v = status2numMap[status];

   if (v != null)
   {
      return v;
   }
   else
   {
      return -1;
   }
}

function sort_status(a, b, order)
{
   var n = sort2num(a);
   var m = sort2num(b);

   if (order == "asc")
      return n > m ? 1 : -1;
   else
      return n < m ? 1 : -1;
}
;


//var status2iconMap = {};
//status2iconMap["Registered"] = "/images/24x24/status_red.png";
//status2iconMap["Verified"] = "/images/24x24/status_orange.png";
//status2iconMap["Seated"] = "/images/24x24/status_yellow.png";
//status2iconMap["InProgress"] = "/images/24x24/status_blue.png";
//status2iconMap["Completed"] = "/images/24x24/status_green.png";
//status2iconMap["Postponed"] = "/images/24x24/status_grey.png";



//var status2SelectedStyleMap = {};
//status2SelectedStyleMap["Registered"] = "text-decoration: underline !important; color: #ffffff;";
//status2SelectedStyleMap["Verified"] = "text-decoration: underline !important;background-color: #fff49e;";
//status2SelectedStyleMap["Seated"] = "text-decoration: underline !important;background-color: #fff49e;";
//status2SelectedStyleMap["InProgress"] = "text-decoration: underline !important; background-color: #fff49e;";
//status2SelectedStyleMap["Completed"] = "text-decoration: underline !important; background-color: #fff49e;";
//status2SelectedStyleMap["Postponed"] = "text-decoration: underline !important; background-color: #fff49e;";

//function status2icon(status)
//{
//   return status2iconMap[status];//
//}


function status2style(status, selected)
{
   if (selected)
   {
      //return "background-color: #fff49e; color: #555555;";

      return status2SelectedStyleMap[status];
   }
   else
   {
      //return "background-image: /images/24x24/status_green.png; background-repeat: repeat-x;";

      return status2StyleMap[status];

   }
}

function cell_value(grid, row_id, col_id)
{
   var col_idx = grid.getColIndexById(col_id);
   //alert("col-id" + col_idx)

   if (col_idx >= 0)
   {
      var cell = grid.cells(row_id, col_idx);
      // alert("cell: " + cell)

      if (cell)
      {
         return cell.getValue();
      }
   }
   return null;
}

