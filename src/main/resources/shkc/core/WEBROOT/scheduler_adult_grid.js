// ------------------------------------------------------------------------
// -- $Id: scheduler_adult_grid.js,v 1.7 2017/09/13 18:44:36 sking Exp $
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

//var leaderGrid;

function SchedulerAdultGrid(layout_cell, title)
{
   SchedulerGrid.call(this, layout_cell, title, "/adult-cells",
           "@,ST,Last,First,Unit,RM#,Final,Project",
           "Sel,X,Last,First,UnitName,Room,FinalBoard,ProjectReview",
           "40,40,80,80,60,50,80,*",
           true);

   var this_obj = this;

   // leaderGrid = this.grid;
   this.grid.setColTypes("ch,img,ro,ro,ro,ro,ro,ro")
      this.grid.attachHeader("#select_filter,,#text_filter,#text_filter,#select_filter,#select_filter,#select_filter,#select_filter")


   this.grid.attachEvent("onCheck", function (r_id, c_ind, state)
   {
      if (state)
      {
         this_obj.grid.sortRows(0, "str", "des");
         //  this_obj.grid.showRow(r_id);
         this_obj.grid.showRow(this_obj.grid.getRowId(0));
         //SHK this_obj.grid.selectRowById(r_id);
      }
      this_obj.highlightRow(r_id);
      //this_obj.highlight();
   });

   //this.grid.setImagesPath("/images/32x32/");

   this.toolbar = layout_cell.attachToolbar();
   this.toolbar.setIconsPath("/images/32x32/");
   this.toolbar.setIconSize(24);
   //this.toolbar.addButton("Locate", 1, "Locate", "edit-find-user.png", "edit-find-user.png");

   this.toolbar.addButtonTwoState("Filter", 2, "View", "flag-green.png", "flag-green-dis.png");
   this.toolbar.addButton("Clear", 3, "Clear", null, null);

   //this.toolbar.addSpacer("Filter");
   this.toolbar.addSeparator("Sep", 4);

   this.toolbar.addButton("Enable", 5, null, "im-user.png", "im-user-dis.png");
   this.toolbar.addButton("Disable", 6, null, "im-user-offline.png", "im-user-offline-dis.png");

   this.toolbar.setItemToolTip("Enable", "Make Adult Available");
   this.toolbar.setItemToolTip("Disable", "Make Adult Unavailable");



   //  this.toolbar.attachEvent("onClick", function(id) {
//
   //completePopup.hide();

   //   var s_id = schedulerScoutGrid.getSelectedRowId(); // scoutGrid.getSelectedRowId();
   //   if (!s_id)
   //   {
   //      //alert("No Scout Selected !!");
   //      dhtmlx.alert({
   //         title: "Error",
   //         type: "alert-error",
   //         text: "No Scout Selected !!",
   //         expires: 1000
   //      });
   //      return;
   //   }

   //   var s_status = schedulerScoutGrid.getColumnValue(s_id, "Status");
   //   if (s_status == "Seated")
   //   {
   //      SCHEDULER_locateAdults(s_id, false);
   //   }
   //   else if (s_status == "InProgress")
   //   {
   //      SCHEDULER_locateAdults(s_id, true);
   //   }
   //});


   this.toolbar.attachEvent("onStateChange", function (id, state) {
      //your code here
      if (id === "Filter")
      {
         this_obj.setFilterIcon(state);

         // if (state) {
         //    this_obj.toolbar.setItemImage("Filter", "flag-green-dis.png");
         //    this_obj.toolbar.setItemToolTip("Filter", "Hide Completed Records");

         // } else {
         //   this_obj.toolbar.setItemImage("Filter", "flag-green.png");
         //   this_obj.toolbar.setItemToolTip("Filter", "Show Completed Records");

         //  }
         this_obj.updateHidden(state);
      }

   });



   this.processor = new dataProcessor("/adult-update"); //lock feed url
   this.processor.init(this.grid); //link dataprocessor to the grid
   this.processor.setTransactionMode("GET", false);
   this.processor.enableDataNames(true);
   this.processor.enablePartialDataSend(true);

   this.processor.attachEvent("onAfterUpdate", function (r_id, action, tid, response)
   {
      this_obj.highlightRow(r_id);
   });


   this.toolbar.attachEvent("onClick", function (id) {

      if (id === "Clear")
      {
         this_obj.uncheckAll();
      } else if (id === "Disable")
      {
         var r_id = this_obj.grid.getSelectedRowId();
         var room = this_obj.getColumnValue(r_id, "Room");
         var lname = this_obj.getColumnValue(r_id, "Last");
         var fname = this_obj.getColumnValue(r_id, "First");

         if (room === "N/A")
         {
            dhtmlx.alert({
               title: "Disable Error",
               text: fname + " " + lname + " already disabled."
            });
         } else if (room && (room.length > 0))
         {
            dhtmlx.alert({
               title: "Disable Error",
               text: fname + " " + lname + " is currently assigned to room " + room + "."
            });
         } else {
            dhtmlx.confirm({
               title: "Confirm Disable",
               text: "Do you want to disable " + fname + " " + lname + "  ?",
               callback: function (result) {
                  if (result == true)
                  {
                     this_obj.updateRoom(r_id, "N/A");
                     // this_obj.setColumnValue(r_id, "Room", "N/A");
                     // this_obj.processor.sendData(r_id);
                  }
               }
            });
         }
      } else if (id === "Enable")
      {
         var r_id = this_obj.grid.getSelectedRowId();

         if (r_id)
         {
            var room = this_obj.getColumnValue(r_id, "Room");
            var lname = this_obj.getColumnValue(r_id, "Last");
            var fname = this_obj.getColumnValue(r_id, "First");

            if (room !== "N/A")
            {
               dhtmlx.alert({
                  title: "Enable Error",
                  text: fname + " " + lname + " is not disabled."
               });
            } else {
               dhtmlx.confirm({
                  title: "Confirm Enable",
                  text: "Do you want to enable " + fname + " " + lname + "  ?",
                  callback: function (result) {
                     if (result == true)
                     {
                        this_obj.updateRoom(r_id, "");
                        // this_obj.setColumnValue(r_id, "Room", "N/A");
                        // this_obj.processor.sendData(r_id);
                     }
                  }
               });
            }
         }
      }
   });

   this.grid.attachEvent("onRowSelect", function (l_id, ind)
   {
      this_obj.updateButtonStatus(l_id);
      //SCHEDULER_selectScout(s_id);
   });
}

SchedulerAdultGrid.prototype = new SchedulerGrid();

SchedulerAdultGrid.prototype.constructor = SchedulerAdultGrid;

SchedulerAdultGrid.prototype.updateButtonStatus = function (l_id)
{
   if (!l_id)
   {
      l_id = this.getSelectedRowId();
   }

   if (!l_id || l_id.length == 0 || typeof l_id === "object")
   {
      this.setButtonStatus(["Filter", "Clear"]);
      return;
   }

   // list of IDs, multiselect
   if (l_id.indexOf(',') > 0)
   {
      this.setButtonStatus(["Filter","Clear"]);
      return;
   }

   //alert("lid: " + typeof l_id + "/" + l_id);

   var room = this.getColumnValue(l_id, "Room");

   if (room === "N/A")
   {
      this.setButtonStatus(["Enable", "Clear", "Filter"]);
   } else if (room === "")
   {
      this.setButtonStatus(["Disable", "Clear", "Filter"]);
   } else
   {
      this.setButtonStatus(["Filter", "Clear"]);
   }
}

SchedulerAdultGrid.prototype.setButtonStatus = function (enabled_buttons)
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

SchedulerAdultGrid.prototype.doAfterLoad = function ()
{

   //this.updateButtonStatus();
   this.updateHidden(null);
   this.updateButtonStatus();

   //this.();
};

SchedulerAdultGrid.prototype.setFilterIcon = function (state)
{
   var this_obj = this;

   if (state) {
      this_obj.toolbar.setItemImage("Filter", "flag-green-dis.png");
      this_obj.toolbar.setItemToolTip("Filter", "Hide Assigned Leaders");
      this_obj.toolbar.setItemText("Filter", "Hide");
   } else {
      this_obj.toolbar.setItemImage("Filter", "flag-green.png");
      this_obj.toolbar.setItemToolTip("Filter", "Show All Leaders");
      this_obj.toolbar.setItemText("Filter", "View");
   }
}

SchedulerAdultGrid.prototype.updateHidden = function (state)
{
   var this_obj = this;

   if (state == null)
   {
      state = this.toolbar.getItemState("Filter");
      this_obj.setFilterIcon(state);
   }

//   this.highlight();


   this.grid.forEachRow(function (r_id)
   {
      var l_room = this_obj.getColumnValue(r_id, "Room");
      var l_final = this_obj.getColumnValue(r_id, "FinalBoard");
      var l_project = this_obj.getColumnValue(r_id, "ProjectReview");

      if ((!state) && (l_room.length > 0))
      {
         this_obj.grid.setRowHidden(r_id, true);
         this_obj.highlightRow(r_id);
      } else if ((!state) && (l_final === "Unavailable") && (l_final === "Unavailable"))
      {
         this_obj.grid.setRowHidden(r_id, true);
         this_obj.highlightRow(r_id);
      } else
      {
         if (l_room.length > 0)
         {
            this_obj.grid.cells(r_id, 0).setValue(false);
         }
         this_obj.grid.setRowHidden(r_id, false);
         this_obj.highlightRow(r_id);
      }

   });
   this.updateButtonStatus();

};



SchedulerAdultGrid.prototype.selectForRoom = function (room_num)
{
   this.toolbar.setItemState("Filter", true);
   this.updateHidden(null);
   this.selectForRoomExt(room_num, false, 0);
   this.grid.sortRows(0, "str", "des");
   this.grid.showRow(this.grid.getRowId(0));
}

SchedulerAdultGrid.prototype.highlight = function ()
{
}

SchedulerAdultGrid.prototype.getCheckedRowIds = function ()
{
   var results = [];
   var this_obj = this;
   this.grid.forEachRow(function (r_id)
   {
      if (this_obj.grid.cells(r_id, 0).getValue() == true)
      {
         results.push(r_id);
      }
   });
   return results.join();
}


SchedulerAdultGrid.prototype.checkRow = function (r_id)
{
   this.grid.cells(r_id, 0).setValue(true);
   this.grid.sortRows(0, "str", "des");
   this.grid.showRow(this.grid.getRowId(0));
   //SHK this.grid.selectRowById(r_id, true, true, false);
   this.highlightRow(r_id);
}

SchedulerAdultGrid.prototype.sortChecked = function (r_id)
{
   this.grid.sortRows(0, "str", "des");
   this.grid.showRow(this.grid.getRowId(0));
   //SHK this.grid.selectRowById(r_id, true, true, false);
   this.highlightRow(r_id);
}

SchedulerAdultGrid.prototype.uncheckAll = function (room_num)
{
   this.grid.uncheckAll();
   // this.highlight();
}

SchedulerAdultGrid.prototype.findBoardMembers = function (s_uname, s_btype, member_type_arr, cnt, omit_ids)
{
   // alert("findBoard Chairs: " + member_type_arr);
   var this_obj = this;
   var member_arr = [];

   this.grid.forEachRow(function (l_id)
   {
      if (omit_ids.indexOf(l_id) >= 0)
      {
         return;
      }
      //ADULT: Last,First,UType,Unit,Final,Project, RM#
      var l_uname = this_obj.getColumnValue(l_id, "UnitName");
      var l_final = this_obj.getColumnValue(l_id, "FinalBoard");
      var l_project = this_obj.getColumnValue(l_id, "ProjectReview");
      var l_room = this_obj.getColumnValue(l_id, "Room");

//alert("checking leader: " + l_id + ", unit="+ l_unit + ", project=" + l_project + "final=", l_final)
      if (l_room == "" || l_room == "-") // Not occupied
      {
         if (l_uname != s_uname)
         {
            // Okay to select...
            var p = false;
            var f = false;
            if (((s_btype == "Final") && (member_type_arr.indexOf(l_final) >= 0))
                    || ((s_btype == "Project") && (member_type_arr.indexOf(l_project) >= 0))
                    )
            {
               if (member_arr.length < cnt)
               {
                  //this_obj.grid.selectRowById(l_id, true, true, false);
                  var ch = this_obj.grid.cells(l_id, 0).setValue(true);
                  this_obj.sortChecked(l_id);
                  member_arr.push(l_id);
                  // alert("len=" + member_arr.length + " ?= " + cnt + ": " + member_arr.toString());
                  console.log("selected: " + l_id + "[" + (f ? l_final : l_project) + "]");
                  return;
               }
            }
         }
      }
   });
   return member_arr;
}

SchedulerAdultGrid.prototype.highlight = function ()
{
   var selected_id = this.grid.getSelectedRowId();
   var t = this;

   this.grid.forEachRow(function (s_id)
   {
      t.highlightRow(s_id);
//      var l_room = t.getColumnValue(s_id, "Room"); // his.grid.cells(s_id, status_col).getValue();

//      if (l_room != null)
//      {
//         if (l_room === "")
//         {
//            //   t.grid.setRowTextStyle(s_id, "background-color: #ffffff");
//         }
//         else
//         {
//            t.grid.setRowTextStyle(s_id, "color: #ff0000");
//         }
//      }
      //  else
      //  {
      //     t.grid.setRowTextStyle(s_id, "background-color: #ffffff");
      //  }
      //  window.console.log("STYLE: "+ l_status + ",style=" + style);
   });
}

SchedulerAdultGrid.prototype.highlightRow = function (l_id)
{
   var this_obj = this;

   var l_room = this_obj.getColumnValue(l_id, "Room"); // his.grid.cells(s_id, status_col).getValue();
   var l_final = this_obj.getColumnValue(l_id, "FinalBoard");
   var l_project = this_obj.getColumnValue(l_id, "ProjectReview");
   // console.log("highlightRow: " + r_id);

   if (l_room && (l_room.length > 0))
   {
      if (l_room === "N/A")
      {
         this_obj.grid.setRowTextStyle(l_id, "color: #888888");
         this_obj.grid.cells(l_id, 1).setValue("/images/24x24/im-user-offline.png");
      } else
      {
         this_obj.grid.setRowTextStyle(l_id, "color: #ff0000");
         this_obj.grid.cells(l_id, 1).setValue("/images/24x24/im-user-busy.png");
      }
   } else
   {
      this_obj.grid.setRowTextStyle(l_id, "color: #000000");
      this_obj.grid.cells(l_id, 1).setValue("/images/24x24/im-user.png");
   }

}


SchedulerAdultGrid.prototype.updateRoom = function (l_id, room_value)
{
   var this_obj = this;

   var l_last = this_obj.getColumnValue(l_id, "Last");
   var l_first = this_obj.getColumnValue(l_id, "First");

   xmlHttp = new XMLHttpRequest();
   xmlHttp.onreadystatechange = function () {
      if (xmlHttp.readyState === 4) {
         if (xmlHttp.status === 200) {
//            if (xmlHttp.responseText.length > 4)
//            {
//               dhtmlx.alert({
//                  title: "Disable Error",
//                  type: "alert-error",
//                  text: l_first + " " + l_last
//                          + " complete failed.<br/> " + xmlHttp.responseText
//               });
//            }
//            else
//            {
//               dhtmlx.message({
//                  title: "Disable Successful",
//                  type: "OK",
//                  text: "Success: " + l_first + " " + l_last + " Seated OK"
//               });
//            }
         } else {
            dhtmlx.alert({
               title: "Disable Error",
               type: "alert-error",
               text: l_first + " " + l_last + " Seat Failed."
            });
         }
      }
      setTimeout(function () {
         refresh_all();
      }, 500);
   }

   xmlHttp.open("GET", "/adult-update?editing=true&gr_id=" + l_id + "&Room=" + room_value, false);
   xmlHttp.send(null);
}