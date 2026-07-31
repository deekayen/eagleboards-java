// ------------------------------------------------------------------------
// scheduler_scout_grid.js — Youth panel (Tabulator).
//
// Columns:  # (RegNum), T (MinsSinceLastUpdate),
//           Last, First, Unit, F/P (BoardType), RM# (Room), Status, Leader
// Toolbar:  Seat / Complete | Locate / View-Hide / Reset /
//           Postpone — enabled according to the selected scout's status.
//           (Seat and Start were merged: seating goes straight to InProgress.)
// Timers:   active (Seated/InProgress) rows raise a room card warning
//           (yellow) then overdue (red) at the board-type thresholds in
//           config.properties, measured from when the board was seated.
// ------------------------------------------------------------------------

function SchedulerScoutGrid(container_id, toolbar_id, title) {
   SchedulerGrid.call(this, container_id, title, "/scout-cells",
      [
         { title: "#", field: "RegNum", width: 46, sorter: "number" },
         { title: "T", field: "MinsSinceLastUpdate", width: 44, sorter: "number" },
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
         { title: "Status", field: "Status", width: 100, sorter: sort_status, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "Leader", field: "Leader", widthGrow: 1, headerFilter: "input" }
      ],
      ["RegNum", "MinsSinceLastUpdate", "Last", "First", "UnitName", "BoardType", "Room", "Status", "Leader"]);

   var this_obj = this;

   this.showCompleted = false;    // old "Filter" two-state button, off = hide Completed/Postponed
   this.roomTimerUpdateFunction = null;
   this.lastNotice = {};          // row id -> last minute count a notice fired at

   this.toolbar = document.getElementById(toolbar_id);
   this.buttons = {};
   var names = ["Seat", "Complete", "Locate", "Filter", "Reset", "Postpone"];
   for (var i = 0; i < names.length; i++) {
      this.buttons[names[i]] = this.toolbar.querySelector("[data-action='" + names[i] + "']");
   }

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
         return;
      }

      var s_id = this_obj.getSelectedRowId();
      if (!s_id) {
         ebAlert("Error", "No Scout Selected !!");
         return;
      }

      if (id === "Seat") {
         ProcessSeatBoard(s_id);
      } else if (id === "Complete") {
         ProcessCompleteBoard(s_id);
      } else if (id === "Postpone") {
         ProcessPostponeBoard(s_id);
      } else if (id === "Reset") {
         ProcessResetBoard(s_id);
      } else if (id === "Locate") {
         SCHEDULER_locateAdults(s_id, true);
      }
   });

   this.setFilterButton();
   this.updateButtonStatus();

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
   this.updateButtonStatus(s_id);
   SCHEDULER_selectScout(s_id);
};

SchedulerScoutGrid.prototype.updateSelected = function (id) {
   this.updateButtonStatus(id);
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
   this.updateButtonStatus();
   this.updateHidden(null);
   this.checkTimers();
};

SchedulerScoutGrid.prototype.updateHidden = function (state) {
   // visibility is driven by this.showCompleted inside the table filter
   this.table.refreshFilter();
};

// Minutes-since-seated at which an active board's room card turns yellow
// (warning) then red (overdue). Board-type specific; configurable via
// config.properties (see SCHEDULER_*Mins in scheduler_config.js).
SchedulerScoutGrid.prototype.getYellowTime = function (status, btype) {
   if (status != "Seated" && status != "InProgress") {
      return 0;
   }
   return (btype == "Final") ? SCHEDULER_FinalYellowTime : SCHEDULER_ProjectYellowTime;
};

SchedulerScoutGrid.prototype.getRedTime = function (status, btype) {
   if (status != "Seated" && status != "InProgress") {
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

SchedulerScoutGrid.prototype.setButtonStatus = function (enabled_buttons) {
   for (var name in this.buttons) {
      if (this.buttons[name]) {
         this.buttons[name].disabled = (enabled_buttons.indexOf(name) < 0);
      }
   }
};

SchedulerScoutGrid.prototype.updateButtonStatus = function (s_id) {
   if (s_id == null) {
      s_id = this.getSelectedRowId();
      if (s_id == null) {
         this.setButtonStatus(["Filter"]);
         return;
      }
   }
   var status = this.getColumnValue(s_id, "Status");

   if (status === "Registered") {
      // Verify was removed, so a registered scout is seated directly.
      this.setButtonStatus(["Seat", "Postpone", "Locate", "Filter"]);
   } else if (status === "Verified") {
      // Legacy records only: nothing sets this status anymore, but a carried-
      // over scout must still be seatable rather than stuck.
      this.setButtonStatus(["Seat", "Reset", "Postpone", "Locate", "Filter"]);
   } else if (status === "Seated" || status === "InProgress") {
      // Seat and Start are merged: seating goes straight to InProgress.
      // Legacy "Seated" records (if any) are treated the same as active.
      this.setButtonStatus(["Reset", "Complete", "Locate", "Filter"]);
   } else if (status === "Completed") {
      this.setButtonStatus(["Locate", "Filter"]);
   } else if (status === "Postponed") {
      this.setButtonStatus(["Locate", "Filter"]);
   } else {
      this.setButtonStatus(["Filter"]);
   }
};
