// ------------------------------------------------------------------------
// -- $Id: old_saved_script.js,v 1.1 2015/01/30 04:04:44 sking Exp $
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




function scout_highlight(scout_grid, status_col)
{
   var selected_id = scout_grid.getSelectedRowId();

   scout_grid.forEachRow(function(s_id)
   {
      var l_status = scout_grid.cells(s_id, status_col).getValue();


      //alert("ROW: " + s_id + " => " + l_status
      if (l_status == "Waiting")
      {
         if (selected_id == s_id)
         {
            scout_grid.setRowTextStyle(s_id, "background-color: #ffaaaa;");
         }
         else
         {
            scout_grid.setRowTextStyle(s_id, "background-color: #ffdddd;");
         }
      }
      else if (l_status == "InProgress")
      {
         if (selected_id == s_id)
         {
            scout_grid.setRowTextStyle(s_id, "background-color: #ffff55;");
         }
         else
         {
            scout_grid.setRowTextStyle(s_id, "background-color: #ffffdd;");
         }
      }
      else if (l_status == "Completed")
      {
         if (selected_id == s_id)
         {
            scout_grid.setRowTextStyle(s_id, "background-color: #88ee88;");
         }
         else
         {
            scout_grid.setRowTextStyle(s_id, "background-color: #ddffdd;");
         }
      }
      else if (l_status == "Postponed")
      {
         if (selected_id == s_id)
         {
            scout_grid.setRowTextStyle(s_id, "background-color: #8888ff;");
         }
         else
         {
            scout_grid.setRowTextStyle(s_id, "background-color: #ddddff;");
         }
      }

   });
}

function scoutGrid_highlight()
{
   scout_highlight(scoutGrid, 7);
}

