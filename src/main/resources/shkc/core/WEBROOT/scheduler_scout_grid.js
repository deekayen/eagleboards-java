// ------------------------------------------------------------------------
// scheduler_scout_grid.js — Youth panel (Tabulator).
//
// Columns:  # (RegNum), Min (MinsSinceLastUpdate),
//           Last, First, Unit, F/P (BoardType), RM# (Room), Status, Leader
// Toolbar:  Seat / Complete | Locate / View-Hide / Reset /
//           Postpone — enabled according to the selected scout's status.
//           (Seat and Start were merged: seating goes straight to InProgress.)
// Timers:   active (Seated/InProgress) rows raise a room card warning
//           (yellow) then overdue (red) at the board-type thresholds in
//           config.properties, measured from when the board was seated.
// ------------------------------------------------------------------------

function SchedulerScoutGrid(container_id, toolbar_id, title) {
   SchedulerGrid.call(this, container_id, title, "/youth-cells",
      [
         // sorter:"number" was wrong here -- these values are "P3"/"W1", and
         // parseFloat of those is NaN. sort_regnum orders pre-registered above
         // walk-ins, then numerically within each; see scheduler_config.js.
         //
         // The prefix is the only place the pre-registered/walk-in distinction
         // appears in the UI, and "P"/"W" say nothing on their own, so both the
         // heading and every cell carry a tooltip that spells it out.
         {
            title: "#", field: "RegNum", width: 46, sorter: sort_regnum,
            headerTooltip: "P = pre-registered, matched a sign-up. "
                         + "W = walk-in, nothing matched at sign-in. "
                         + "The number is the order they signed in within their group. "
                         + "Sorted pre-registered first, so walk-ins queue behind them.",
            tooltip: function (e, cell) {
               var v = cell.getValue() || "";
               var n = v.replace(/^[A-Za-z]+/, "");
               if (v.charAt(0) === "P") {
                  return "Pre-registered — matched a sign-up. #" + n + " of the pre-registered to sign in.";
               }
               if (v.charAt(0) === "W") {
                  return "Walk-in — no pre-registration matched. #" + n + " of the walk-ins to sign in.";
               }
               return "";
            }
         },
         // Was "T", which said nothing. "Min" names the unit rather than the
         // meaning, deliberately: the value is minutes since the record last
         // CHANGED, and seating changes it. A Registered youth's count is how
         // long they have waited, but the moment they are seated it restarts
         // and measures how long the board has been running. "Wait" would have
         // been wrong for every row on a board; a unit name is true for both.
         //
         // The two meanings are not interchangeable and the second is
         // load-bearing: checkTimers compares this same number against
         // ProjectYellowMins/FinalYellowMins to colour the room cards. Do not
         // "fix" it into a true wait time. The tooltip below says which of the
         // two a given cell means; Status and RM# beside it already
         // distinguish the cases at a glance.
         {
            title: "Min", field: "MinsSinceLastUpdate", width: 56, sorter: "number",
            tooltip: function (e, cell) {
               var mins = cell.getValue();
               if (mins === "" || mins == null) {
                  return "";
               }
               // Spell out what the count is measuring for THIS row. Completed
               // and Postponed rows are reachable through the View toggle, and
               // calling their count a wait would be as wrong as calling an
               // in-progress board's one.
               var status = cell.getRow().getData().Status;
               if (status === "InProgress") {
                  return "On a board for " + mins + " min";
               }
               if (status === "Completed" || status === "Postponed") {
                  return mins + " min since finishing";
               }
               return "Waiting " + mins + " min";
            }
         },
         { title: "Last", field: "Last", width: 90, headerFilter: "input" },
         { title: "First", field: "First", width: 90, headerFilter: "input" },
         // ebUnitLabel shortens numbered units for display only; see
         // scheduler_grid.js. The filter dropdown still lists the stored whole
         // words, which is what makes "Council" and "Community" tellable apart
         // when picking one.
         { title: "Unit", field: "UnitName", width: 70, formatter: function (cell) { return ebUnitLabel(cell.getValue()); },
           headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "F/P", field: "BoardType", width: 70, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "RM#", field: "Room", width: 64, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "Status", field: "Status", width: 100, sorter: sort_status, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true },
           formatter: function (cell) { return SCHEDULER_statusCellHtml(cell.getValue()); } },
         { title: "Leader", field: "Leader", widthGrow: 1, headerFilter: "input" }
      ],
      // The last two are not shown; auto-select reads them to tell when each
      // adult came off their last board (freeSinceTimes in process_seat.js).
      ["RegNum", "MinsSinceLastUpdate", "Last", "First", "UnitName", "BoardType", "Room", "Status", "Leader",
       "LastUpdateTime", "BoardMembersIDs"]);

   var this_obj = this;

   this.showCompleted = false;    // old "Filter" two-state button, off = hide Completed/Postponed
   this.roomTimerUpdateFunction = null;
   this.lastNotice = {};          // row id -> last minute count a notice fired at

   // Default order: the pre-registered queue first, walk-ins beneath it, each
   // group still in the order its people signed in. Without this the grid
   // renders in server order -- one arrival sequence with walk-ins mixed
   // through it -- and a walk-in who happened to arrive early sits above
   // people who booked a slot. Sorting by RegNum expresses the priority,
   // because the prefix records how they got here and the counter records
   // when. An operator can still click any header to reorder.
   this.ready.then(function () {
      this_obj.table.setSort([{ column: "RegNum", dir: "asc" }]);
   });

   this.toolbar = document.getElementById(toolbar_id);
   this.buttons = {};
   var names = ["Filter"];
   for (var i = 0; i < names.length; i++) {
      this.buttons[names[i]] = this.toolbar.querySelector("[data-action='" + names[i] + "']");
   }

   // Seat/Start Review/Complete/Locate/Reset/Postpone moved to the details
   // pane's own toolbar (D-10/D-11): one status-driven primary action next
   // to the board it acts on, not always-visible buttons up here.
   this.toolbar.addEventListener("click", function (ev) {
      var btn = ev.target.closest("button[data-action]");
      if (!btn || btn.disabled) {
         return;
      }
      var id = btn.getAttribute("data-action");

      if (id === "Filter") {
         this_obj.showCompleted = !this_obj.showCompleted;
         this_obj.setFilterButton();
         this_obj.updateHidden(this_obj.showCompleted);
      }
   });

   this.setFilterButton();

   // apply the Completed/Postponed visibility filter permanently; it
   // consults this.showCompleted each time it runs.
   this.ready.then(function () {
      this_obj.table.setFilter(function (data) {
         if (!this_obj.showCompleted
               && (data.Status === "Completed" || data.Status === "Postponed")) {
            return false;
         }
         return true;
      });
   });
}

SchedulerScoutGrid.prototype = new SchedulerGrid();
SchedulerScoutGrid.prototype.constructor = SchedulerScoutGrid;

SchedulerScoutGrid.prototype.onUserSelect = function (s_id) {
   SCHEDULER_selectScout(s_id);
};

// P-1: the board-lifecycle actions, not the View toggle (that's a page
// setting, not something done to this youth). These now live in the
// details pane (D-10/D-11), so the menu borrows its buttons.
SchedulerScoutGrid.prototype.onContextMenu = function (s_id, e) {
   ebContextMenu(
      [detailsPane.buttons.Primary, detailsPane.buttons.Locate,
       detailsPane.buttons.Reset, detailsPane.buttons.Postpone],
      e.clientX, e.clientY);
};

// Programmatic selection (e.g. clicking a room card selects the scout in
// it via selectForRoom) doesn't run the auto-select cascade, but the
// details pane still needs to show that scout's board.
SchedulerScoutGrid.prototype.updateSelected = function (id) {
   detailsPane.onScoutSelected(id);
};

SchedulerScoutGrid.prototype.setRoomTimerUpdateFunction = function (f) {
   this.roomTimerUpdateFunction = f;
};

SchedulerScoutGrid.prototype.setFilterButton = function () {
   var b = this.buttons["Filter"];
   if (this.showCompleted) {
      b.textContent = "Hide";
      b.title = "Hide Completed Records";
   } else {
      b.textContent = "View";
      b.title = "Show Completed Records";
   }
};

SchedulerScoutGrid.prototype.doAfterLoad = function () {
   this.updateHidden(null);
   this.checkTimers();
   // Keeps the details pane's primary-action label in step with this
   // youth's latest status on every poll, not only right after the
   // operator's own click (e.g. Start Review completing, or another
   // window's changes arriving here).
   detailsPane.render();
};

SchedulerScoutGrid.prototype.updateHidden = function (state) {
   // visibility is driven by this.showCompleted inside the table filter
   this.table.refreshFilter();
};

// Minutes at which a board's room card turns yellow (warning) then red
// (overdue). The clock runs on MinsSinceLastUpdate, so it restarts by itself
// when the status changes -- which is what keeps the two phases timed apart:
//
//   "Seated"      the board is convening, reading the paperwork before the
//                 scout is called in. One cap, no yellow: returning the same
//                 value for both means the card stays okay and then goes
//                 straight to red once the board has held the room too long.
//   "InProgress"  the scout is in the room. Board-type specific yellow/red.
//
// Configurable via config.properties (see SCHEDULER_*Time in
// scheduler_config.js).
SchedulerScoutGrid.prototype.getYellowTime = function (status, btype) {
   if (status == "Seated") {
      return SCHEDULER_ConveneRedTime;
   }
   if (status != "InProgress") {
      return 0;
   }
   return (btype == "Final") ? SCHEDULER_FinalYellowTime : SCHEDULER_ProjectYellowTime;
};

SchedulerScoutGrid.prototype.getRedTime = function (status, btype) {
   if (status == "Seated") {
      return SCHEDULER_ConveneRedTime;
   }
   if (status != "InProgress") {
      return 0;
   }
   return (btype == "Final") ? SCHEDULER_FinalRedTime : SCHEDULER_ProjectRedTime;
};

SchedulerScoutGrid.prototype.checkTimers = function () {
   var this_obj = this;
   this.forEachRow(function (r_id) {
      var status = this_obj.getColumnValue(r_id, "Status");
      var btype = this_obj.getColumnValue(r_id, "BoardType");
      var yellow_time = this_obj.getYellowTime(status, btype);
      var red_time = this_obj.getRedTime(status, btype);

      if (yellow_time > 0) {
         var mins = parseInt(this_obj.getColumnValue(r_id, "MinsSinceLastUpdate"), 10);
         var s_room = this_obj.getColumnValue(r_id, "Room");

         if (typeof this_obj.roomTimerUpdateFunction === "function") {
            if (red_time > 0 && mins >= red_time) {
               this_obj.lastNotice[r_id] = "" + mins;
               this_obj.roomTimerUpdateFunction(s_room, mins, "reminder");
            } else if (mins >= yellow_time) {
               this_obj.lastNotice[r_id] = "" + mins;
               this_obj.roomTimerUpdateFunction(s_room, mins, "alert");
            } else {
               this_obj.roomTimerUpdateFunction(s_room, mins, "okay");
            }
         }
      }
   });
};

// Button enable/disable by status now lives on the details pane (D-11:
// one status-driven primary action) -- see SchedulerDetailsPane._setButtons.
