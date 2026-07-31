// ------------------------------------------------------------------------
// scheduler_board_grid.js — Active/Completed Boards panel (Tabulator).
//
// Shows scouts whose Status is Seated/InProgress/Completed/Postponed,
// colored by status. Selecting a board row selects everything for its
// room (scout, adults, room card).
// ------------------------------------------------------------------------

function SchedulerBoardGrid(container_id, title) {
   SchedulerGrid.call(this, container_id, title,
      "/youth-cells?filter=Status~Seated|InProgress|Completed|Postponed",
      [
         { title: "#", field: "RegNum", width: 50, sorter: "number", headerFilter: "input" },
         { title: "Last", field: "Last", width: 90, headerFilter: "input" },
         { title: "First", field: "First", width: 90, headerFilter: "input" },
         // Display-only shortening; see scheduler_grid.js.
         { title: "Unit", field: "UnitName", width: 70, formatter: function (cell) { return ebUnitLabel(cell.getValue()); },
           headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "Leader", field: "Leader", width: 100, headerFilter: "input" },
         { title: "Board", field: "BoardType", width: 70, headerFilter: "input" },
         { title: "Status", field: "Status", width: 90, sorter: sort_status, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "RM#", field: "Room", width: 60, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "Result", field: "Result", width: 80, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "Chair", field: "BoardChair", width: 100, headerFilter: "input" },
         { title: "Members", field: "BoardMembers", width: 120, headerFilter: "input" },
         { title: "Notes", field: "Notes", widthGrow: 1, headerFilter: "input" }
      ],
      ["RegNum", "Last", "First", "UnitName", "Leader", "BoardType", "Status", "Room", "Result", "BoardChair", "BoardMembers", "Notes"]);
}

SchedulerBoardGrid.prototype = new SchedulerGrid();
SchedulerBoardGrid.prototype.constructor = SchedulerBoardGrid;

SchedulerBoardGrid.prototype.onUserSelect = function (s_id) {
   var b_room = this.getColumnValue(s_id, "Room");

   if ((b_room.length > 0) && (b_room !== "N/A")) {
      SCHEDULER_selectAllForRoom(b_room);
   }
   SCHEDULER_selectScout(s_id);
};
