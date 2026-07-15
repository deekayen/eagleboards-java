// ------------------------------------------------------------------------
// -- $Id: scheduler_board_grid.js,v 1.5 2015/11/18 15:00:40 sking Exp $
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

var boardGrid;

function SchedulerBoardGrid(layout_cell, title)
{
   SchedulerGrid.call(this, layout_cell, title,
           "/scout-cells?filter=Status~Seated|InProgress|Completed|Postponed&fmt=rows",
           "#,Last,First,Unit,Leader,Board,Status,RM#, Result,Chair,Members,Notes",
           "RegNum,Last,First,UnitName,Leader,BoardType,Status,Room,Result,BoardChair,BoardMembers,Notes",
           "50,80,80,60,100,60,80,50,60,100,100,*",
           false);

   boardGrid = this.grid;
   
   this.grid.attachHeader("#text_filter,#text_filter,#text_filter,#select_filter,#text_filter,#text_filter,#select_filter,#select_filter,#select_filter,#text_filter,#text_filter,#text_filter")


   var this_obj = this;

   this.grid.attachEvent("onRowSelect", function(s_id, ind) {
      var b_room = this_obj.getColumnValue(s_id, "Room"); // this_obj.grid.cells(s_id, 7).getValue();

      if ((b_room.length > 0) && (b_room !== "N/A"))
      {
         SCHEDULER_selectAllForRoom(b_room);
      }

      SCHEDULER_selectScout(s_id);

      //   if (scoutGrid.doesRowExist(s_id))
      //   {
      //      scoutGrid.selectRowById(s_id, true, true, false);
      //      global_findScoutmaster(s_id);
      //      // alert("selecting: " + s_id);
      //   }
      // boardGrid_highlight();
      // scoutGrid_highlight();
   });
}

SchedulerBoardGrid.prototype = new SchedulerGrid();

SchedulerBoardGrid.prototype.constructor = SchedulerBoardGrid;



