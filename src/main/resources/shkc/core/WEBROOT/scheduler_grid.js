// ------------------------------------------------------------------------
// -- $Id: scheduler_grid.js,v 1.3 2015/02/18 01:16:56 sking Exp $
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


function SchedulerGrid(layout_cell, title, url, header, colnames, widths, multi_line)
{
   if (!layout_cell)
   {
      return;
   }
   // scoutGrid = myLayout.cells("a").attachGrid();
   //      myLayout.cells("a").setText("Scout Board Candidates");

   if (url.indexOf("?") > 0)
   {
      url = url + "&";
   }
   else
   {
      url = url + "?";
   }
   this.url = url;
   this.title = title;

   this.grid = layout_cell.attachGrid();

   layout_cell.setText(title);

   //mygrid = new dhtmlXGridObject('gridbox3');
   this.grid.selMultiRows = false;
   this.grid.setImagePath("/dhtmlx/skins/terrace/imgs/dhxgrid_terrace/");
   this.grid.setHeader(header);

   this.COLNAMES = colnames;
   this.COLNAMEARR = this.COLNAMES.split(",");

   var hlen = header.split(",").length
   if (hlen != this.COLNAMEARR.length)
   {
      alert("GRID: " + title + "\n\nHeader length(" + hlen + ") != column length(" + this.COLNAMEARR.length + ")");
   }

   this.COLTYPES = new Array();
   this.COLSORTING = new Array();
   this.COLALIGN = new Array();

   this.NAME2INDEX = {};
   for (var i = 0; i < this.COLNAMEARR.length; i++)
   {
      this.NAME2INDEX[this.COLNAMEARR[i]] = i;
      this.COLALIGN.push("left");
      this.COLTYPES.push("ro");
      this.COLSORTING.push("str");

   }
   // this.grid.setColumnIds("RegTime,Last,First,UnitType,Unit,BoardType,Room,Status,Leader");
   this.grid.setColumnIds(this.COLNAMES);

   // this.grid.setInitWidths("60,100,80,80,60,60,60,90,*");
   if (widths)
   {
      var wlen = widths.split(",").length
      if (wlen != this.COLNAMEARR.length)
      {
         alert("GRID: " + title + "\n\nColumn widths length(" + wlen + ") != column length(" + this.COLNAMEARR.length + ")");
      }
      this.grid.setInitWidths(widths);
   }
   this.grid.setColAlign(this.COLALIGN.join(","));
   this.grid.setColTypes(this.COLTYPES.join(","));
   this.grid.setColSorting(this.COLSORTING.join(","));

//alert("Setting: " + title + ", multiLine=" + multi_line);
   this.grid.setMultiLine(false);
   this.grid.selMultiRows = multi_line;
   this.grid.enableDragAndDrop(false);
   this.grid.init();
}

SchedulerGrid.prototype.constructor = SchedulerGrid;

SchedulerGrid.prototype.getSelectedRowId = function()
{
   return this.grid.getSelectedRowId();
}

SchedulerGrid.prototype.clearSelection = function()
{
   //console.log("clearing selection: " + this.title);
   return this.grid.clearSelection();
}

SchedulerGrid.prototype.load = function()
{
   var t = this;

   this.grid.load(this.url + "cols=" + this.COLNAMES, function() {
      //alert("refreshed_scouts");      
      t.highlight();
      t.doAfterLoad();
   });
}

SchedulerGrid.prototype.refresh = function()
{
   var t = this;

   this.grid.updateFromXML(this.url + "cols=" + this.COLNAMES, true, true, function() {
      //alert("refreshed_scouts");      
      t.highlight();
      t.doAfterLoad();

   });
}

SchedulerGrid.prototype.doAfterLoad = function()
{

}

SchedulerGrid.prototype.getColumnValue = function(row_id, col_id)
{
   var idx = this.NAME2INDEX[col_id];
   if (idx >= 0)
   {
      var cell = this.grid.cells(row_id, idx);
      if (cell)
      {
         return cell.getValue();
      }
   }
   else
   {
      console.log("unknown col '" + col_id + "' [" + idx + "] for grid " + this.title);
//      console.log(this.NAME2INDEX);
   }

   return null;
}

SchedulerGrid.prototype.setColumnValue = function(row_id, col_id, value)
{
   var idx = this.NAME2INDEX[col_id];
   if (idx >= 0)
   {
      var cell = this.grid.cells(row_id, idx);
      if (cell)
      {
         return cell.setValue(value);
      }
   }
   else
   {
      console.log("unknown col '" + col_id + "' [" + idx + "] for grid " + this.title);
//      console.log(this.NAME2INDEX);
   }

   return null;
}

SchedulerGrid.prototype.forEachRow = function(f)
{
   this.grid.forEachRow(f);
}

SchedulerGrid.prototype.selectForRoom = function(room_num)
{
   this.selectForRoomExt(room_num, true);
}

SchedulerGrid.prototype.selectById = function(id)
{
   if (this.grid.doesRowExist(id))
   {
      this.grid.selectRowById(id, true, true, false);
      this.updateSelected(id);
   }
}

SchedulerGrid.prototype.updateSelected = function(id)
{  
}

SchedulerGrid.prototype.selectForRoomExt = function(room_num, single_select, check_col)
{
   var this_obj = this;

   var r_id = this_obj.grid.getSelectedRowId();

   if (single_select && r_id && (this_obj.getColumnValue(r_id, "Room") == room_num))
   {
      // Do nothing... 
   }
   else
   {
      this_obj.clearSelection(); // t.grid.clearSelection();
      this_obj.grid.forEachRow(function(b_id)
      {

         var b_room = this_obj.getColumnValue(b_id, "Room");
         if (room_num == b_room)
         {
            //this_obj.grid.selectRowById(b_id, true, true, false);
            this_obj.selectById(b_id);
            if (check_col>=0)
            {
               //alert("select for room: " + check_col);
               this_obj.checkCol(b_id, check_col);
            }
            
         }
      });
   }
   //   alert("select for room");

}

SchedulerGrid.prototype.checkCol = function(r_id, col_idx)
{
   this.grid.cells(r_id, col_idx).setValue(true);
   this.grid.sortRows(col_idx, "str", "des");
   this.grid.showRow(r_id);
   this.grid.selectRowById(r_id, true, true, false);
}

SchedulerGrid.prototype.highlight = function()
{
   var selected_id = this.grid.getSelectedRowId();
   var t = this;

   this.grid.forEachRow(function(s_id)
   {
      var l_status = t.getColumnValue(s_id, "Status"); // his.grid.cells(s_id, status_col).getValue();

      if (l_status != null)
      {
         var style = status2style(l_status, (selected_id == s_id));
         t.grid.setRowTextStyle(s_id, style);
      }
      //  window.console.log("STYLE: "+ l_status + ",style=" + style);
   });
}

