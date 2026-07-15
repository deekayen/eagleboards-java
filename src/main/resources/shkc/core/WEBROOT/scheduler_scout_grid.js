// ------------------------------------------------------------------------
// -- $Id: scheduler_scout_grid.js,v 1.6 2015/11/18 15:00:40 sking Exp $
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

var scoutGrid;

function SchedulerScoutGrid(layout_cell, title)
{
   SchedulerGrid.call(this, layout_cell, title, "/scout-cells",
           "#,B/S,T,Last,First,Unit,F/P,RM#,Status,Leader",
           "RegNum,AdultScoutRatio,MinsSinceLastUpdate,Last,First,UnitName,BoardType,Room,Status,Leader",
           "40,40,40,80,80,60,60,60,90,*",
           false);

   this.grid.setColSorting("str,str,int,str,str,str,str,str,sort_status,str");
   this.grid.setColTypes("ro,ro,ro,ro,ro,ro,ro,ro,ro,ro");
   this.grid.attachHeader("#text_filter,,,#text_filter,#text_filter,#select_filter,#select_filter,#select_filter,#select_filter,#text_filter")

   var this_obj = this;

   scoutGrid = this.grid;

   this.toolbar = layout_cell.attachToolbar();
   this.toolbar.setIconsPath("/images/32x32/");
   this.toolbar.setIconSize(24);
   this.toolbar.addButton("Verify", 1, "Verify", "dialog-ok-5.png", "dialog-ok-5-dis.png");
   this.toolbar.addButton("Seat", 2, "Seat", "appointment-new-4.png", "appointment-new-4-dis.png");
   this.toolbar.addButton("InProgress", 3, "Start", "user-group-new-2.png", "user-group-new-2-dis.png");
   this.toolbar.addButton("Complete", 4, "Complete", "dialog-ok-apply-5.png", "dialog-ok-apply-5-dis.png");
   this.toolbar.addSeparator("sep2", 5);
   this.toolbar.addButton("Locate", 6, "Locate", "edit-find-user.png", "edit-find-user.png");
   this.toolbar.addButtonTwoState("Filter", 7, "View", "flag-green.png", "flag-green-dis.png");
   this.toolbar.addButton("Reset", 8, "Reset", "system-reboot-2.png", "system-reboot-2-dis.png");

   this.toolbar.addButton("Postpone", 9, "Postpone", "dialog-cancel-5.png", "dialog-cancel-5-dis.png");
   this.toolbar.addSpacer("Filter");

   this.verifyPopup = new dhtmlXPopup({
      toolbar: this_obj.toolbar,
      id: "Verify" //attaches popup to the "Verify" button
   });

   this.seatPopup = new dhtmlXPopup({
      toolbar: this_obj.toolbar,
      id: "Seat" //attaches popup to the "Seat" button
   });
   this.inProgressPopup = new dhtmlXPopup({
      toolbar: this_obj.toolbar,
      id: "InProgress" //attaches popup to the "Seat" button
   });

   this.completePopup = new dhtmlXPopup({
      toolbar: this_obj.toolbar,
      id: "Complete" //attaches popup to the "Seat" button
   });

   this.postponePopup = new dhtmlXPopup({
      toolbar: this_obj.toolbar,
      id: "Postpone" //attaches popup to the "Seat" button
   });

   this.resetPopup = new dhtmlXPopup({
      toolbar: this_obj.toolbar,
      id: "Reset" //attaches popup to the "Seat" button
   });

   this.toolbar.attachEvent("onStateChange", function (id, state) {
      //your code here
      if (id === "Filter")
      {
         if (state) {
            this_obj.toolbar.setItemImage("Filter", "flag-green-dis.png");
            this_obj.toolbar.setItemToolTip("Filter", "Hide Completed Records");
            this_obj.toolbar.setItemText("Filter", "Hide");


         } else {
            this_obj.toolbar.setItemImage("Filter", "flag-green.png");
            this_obj.toolbar.setItemToolTip("Filter", "Show Completed Records");
            this_obj.toolbar.setItemText("Filter", "View");


         }
         this_obj.updateHidden(state);
      }
   });

   this_obj.toolbar.enableItem("Filter");
   this_obj.updateHidden(true);
   this_obj.toolbar.setItemImage("Filter", "flag-green.png");
   this_obj.toolbar.setItemToolTip("Filter", "Show Completed Records");


   this.toolbar.attachEvent("onClick", function (id) {

      //completePopup.hide();

      var s_id = this_obj.getSelectedRowId(); // scoutGrid.getSelectedRowId();
      if (!s_id)
      {
         //alert("No Scout Selected !!");
         dhtmlx.alert({
            title: "Error",
            type: "alert-error",
            text: "No Scout Selected !!"
         });
         return;
      }

      if (id === "Verify")
      {
         console.log("verify: " + s_id);
         ProcessVerifyBoard(s_id);
      } else if (id === "Seat")
      {
         ProcessSeatBoard(s_id);
      } else if (id === "InProgress")
      {
         ProcessInProgressBoard(s_id);
      } else if (id === "Complete")
      {
         ProcessCompleteBoard(s_id);
      } else if (id === "Postpone")
      {
         ProcessPostponeBoard(s_id);
      } else if (id === "Reset")
      {
         ProcessResetBoard(s_id);
      } else if (id === "Locate")
      {
         var s_status = schedulerScoutGrid.getColumnValue(s_id, "Status");
         SCHEDULER_locateAdults(s_id, true);

         //  if (s_status == "Seated")
         //  {
         //     SCHEDULER_locateAdults(s_id, false);
         //  }
         //  else
         //  {
         //     SCHEDULER_locateAdults(s_id, true);
         // }
      }


   });

   this.grid.attachEvent("onRowSelect", function (s_id, ind)
   {
      this_obj.updateButtonStatus(s_id);
      SCHEDULER_selectScout(s_id);
   });

   this.roomTimerUpdateFunction = null;
}

SchedulerScoutGrid.prototype = new SchedulerGrid();

SchedulerScoutGrid.prototype.constructor = SchedulerScoutGrid;

SchedulerScoutGrid.prototype.getToolbar = function ()
{
   return this.toolbar;
};

SchedulerScoutGrid.prototype.setRoomTimerUpdateFunction = function (f)
{
   // alert("setRoomTimerUpdateFunction: " + typeof f)
   this.roomTimerUpdateFunction = f;
};


SchedulerScoutGrid.prototype.getCompletePopup = function ()
{
   return this.completePopup;
};

SchedulerScoutGrid.prototype.getSeatPopup = function ()
{
   return this.seatPopup;
};

SchedulerScoutGrid.prototype.getVerifyPopup = function ()
{
   return this.verifyPopup;
};

SchedulerScoutGrid.prototype.getInProgressPopup = function ()
{
   return this.inProgressPopup;
};


SchedulerScoutGrid.prototype.getPostponePopup = function ()
{
   return this.postponePopup;
};

SchedulerScoutGrid.prototype.getResetPopup = function ()
{
   return this.resetPopup;
};

SchedulerScoutGrid.prototype.updateSelected = function (id)
{
   // alert("SchedulerScoutGrid.updateSelected: " + id);
   this.updateButtonStatus(id);
};


SchedulerScoutGrid.prototype.doAfterLoad = function ()
{
   //var this_obj = this;
   //this.grid.forEachRow(function(r_id)
   //{
   //    var status = this_obj.getColumnValue(r_id, "Status");
   //   this_obj.grid.cells(r_id,0).setValue(status2icon(status));
   //});

   this.updateButtonStatus();
   this.updateHidden(null);
   this.checkTimers();
};

SchedulerScoutGrid.prototype.checkTimers = function ()
{
   var this_obj = this;
   this.grid.forEachRow(function (r_id)
   {
      var status = this_obj.getColumnValue(r_id, "Status");
      var alert_time = this_obj.getStatusAlertTime(status);
      var reminder_time = this_obj.getStatusReminderTime(status);


      if (alert_time > 0)
      {
         console.log("checkTimers: " + status + "=>" + alert_time);

         var last_notice = this_obj.grid.getUserData(r_id, "last_notice");
         var mins = this_obj.getColumnValue(r_id, "MinsSinceLastUpdate");

         var s_last = this_obj.getColumnValue(r_id, "Last");
         var s_first = this_obj.getColumnValue(r_id, "First");
         var s_room = this_obj.getColumnValue(r_id, "Room");

         if (parseInt(mins) >= parseInt(alert_time))
         {
            console.log("alertTime: " + mins + ">=" + alert_time);
            console.log("reminderTime: " + (mins - last_notice) + ">=?" + reminder_time);

            //     if ((last_notice == null) || ((mins - last_notice) >= parseInt(reminder_time)))
            //    {
            /// SEND NOTICE



            // alert(typeof  this_obj.roomTimerUpdateFunction);

            //  f(s_room, mins, "alert");
            if (typeof this_obj.roomTimerUpdateFunction === "function")
            {
               this_obj.grid.setUserData(r_id, "last_notice", "" + mins);

               // alert("no timer function");

               if (mins < (parseInt(alert_time) + parseInt(reminder_time)))
               {
                  this_obj.roomTimerUpdateFunction(s_room, mins, "alert");
               } else
               {
                  this_obj.roomTimerUpdateFunction(s_room, mins, "reminder");
               }
               //         }
//               else
//               {
//                  alert("no timer function");
//               }




//                  dhtmlx.message({
//                     title: "Timer Notice",
//                     type: "timer-" + status,
//                     text: "<span style='font-size: large;'><img src='/images/24x24/task-reminder.png'/>   <b>RM#: " + s_room
//                             + "</b></span><br/>Scout: <b>" + s_last + ", " + s_first + " - </b>" + mins + "m",
//                     expire: -1
//                  });

            }
         } else
         {
            if (typeof this_obj.roomTimerUpdateFunction === "function")
            {
               this_obj.roomTimerUpdateFunction(s_room, mins, "okay");
            }
         }

         console.log("!! alertTime: " + mins + "<" + alert_time);
         console.log("!! reminderTime: " + (mins - last_notice) + "<?" + reminder_time);
      }
   }
   );
}

SchedulerScoutGrid.prototype.getStatusAlertTime = function (status)
{
   if (status == "Seated")
   {
      return SCHEDULER_SeatedAlertTime;
   } else if (status == "InProgress")
   {
      return SCHEDULER_InProgressAlertTime;
   } else
   {
      return 0;
   }
};

SchedulerScoutGrid.prototype.getStatusReminderTime = function (status)
{
   if (status == "Seated")
   {
      return SCHEDULER_SeatedReminderTime;
   } else if (status == "InProgress")
   {
      return SCHEDULER_InProgressReminderTime;
   } else
   {
      return 0;
   }
};


SchedulerScoutGrid.prototype.updateHidden = function (state)
{
   var this_obj = this;

   if (state == null)
   {
      state = this.toolbar.getItemState("Filter");
   }

   this.grid.forEachRow(function (r_id)
   {
      var l_status = this_obj.getColumnValue(r_id, "Status");

      if (!state && ((l_status == "Completed") || l_status == "Postponed"))
      {
         this_obj.grid.setRowHidden(r_id, true);
      } else
      {
         this_obj.grid.setRowHidden(r_id, false);
      }
   });
};

SchedulerScoutGrid.prototype.setButtonStatus = function (enabled_buttons)
{
   var this_obj = this;

   this_obj.toolbar.forEachItem(function (id)
   {
      if (enabled_buttons.indexOf(id) >= 0)
      {
         this_obj.toolbar.enableItem(id);
      } else
      {
         this_obj.toolbar.disableItem(id);
      }
   });
};

SchedulerScoutGrid.prototype.updateButtonStatus = function (s_id)
{
   if (s_id == null)
   {
      s_id = this.getSelectedRowId();
      if (s_id == null)
      {
         this.setButtonStatus(["Filter"]);
         return;
      }
   }
   var status = this.getColumnValue(s_id, "Status");

   if (status === "Registered")
   {
      this.setButtonStatus(["Verify", "Postpone", "Locate", "Filter"]);
   } else if (status === "Verified")
   {
      this.setButtonStatus(["Seat", "Reset", "Postpone", "Locate", "Filter"]);
   } else if (status === "Seated")
   {
      this.setButtonStatus(["Reset", "InProgress", "Postpone", "Locate", "Filter"]);
   } else if (status === "InProgress")
   {
      this.setButtonStatus(["Reset", "Complete", "Locate", "Filter"]);
   } else if (status === "Completed")
   {
      this.setButtonStatus(["Locate", "Filter"]);
   } else if (status === "Postponed")
   {
      this.setButtonStatus(["Locate", "Filter"]);
   }
};
