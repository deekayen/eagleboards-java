// ------------------------------------------------------------------------
// scheduler_scout_grid.js — Scouts/Candidates panel (Tabulator).
//
// Columns:  # (RegNum), B/S (AdultScoutRatio), T (MinsSinceLastUpdate),
//           Last, First, Unit, F/P (BoardType), RM# (Room), Status, Leader
// Toolbar:  Verify / Seat / Start / Complete | Locate / View-Hide /
//           Reset / Postpone — enabled according to the selected scout's
//           status, exactly as the old toolbar did.
// Timers:   rows whose Status is Seated/InProgress raise room alerts when
//           MinsSinceLastUpdate exceeds the configured alert/reminder times.
// ------------------------------------------------------------------------

function SchedulerScoutGrid(container_id, toolbar_id, title) {
   SchedulerGrid.call(this, container_id, title, "/scout-cells",
      [
         { title: "#", field: "RegNum", width: 46, sorter: "number" },
         { title: "B/S", field: "AdultScoutRatio", width: 48 },
         { title: "T", field: "MinsSinceLastUpdate", width: 44, sorter: "number" },
         { title: "Last", field: "Last", width: 90, headerFilter: "input" },
         { title: "First", field: "First", width: 90, headerFilter: "input" },
         { title: "Unit", field: "UnitName", width: 70, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "F/P", field: "BoardType", width: 70, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "RM#", field: "Room", width: 64, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "Status", field: "Status", width: 100, sorter: sort_status, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "Leader", field: "Leader", widthGrow: 1, headerFilter: "input" }
      ],
      ["RegNum", "AdultScoutRatio", "MinsSinceLastUpdate", "Last", "First", "UnitName", "BoardType", "Room", "Status", "Leader"]);

   var this_obj = this;

   this.showCompleted = false;    // old "Filter" two-state button, off = hide Completed/Postponed
   this.roomTimerUpdateFunction = null;
   this.lastNotice = {};          // row id -> last minute count a notice fired at

   this.toolbar = document.getElementById(toolbar_id);
   this.buttons = {};
   var names = ["Verify", "Seat", "InProgress", "Complete", "Locate", "Filter", "Reset", "Postpone"];
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

      if (id === "Verify") {
         ProcessVerifyBoard(s_id);
      } else if (id === "Seat") {
         ProcessSeatBoard(s_id);
      } else if (id === "InProgress") {
         ProcessInProgressBoard(s_id);
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

SchedulerScoutGrid.prototype.getStatusAlertTime = function (status) {
   if (status == "Seated") {
      return SCHEDULER_SeatedAlertTime;
   } else if (status == "InProgress") {
      return SCHEDULER_InProgressAlertTime;
   }
   return 0;
};

SchedulerScoutGrid.prototype.getStatusReminderTime = function (status) {
   if (status == "Seated") {
      return SCHEDULER_SeatedReminderTime;
   } else if (status == "InProgress") {
      return SCHEDULER_InProgressReminderTime;
   }
   return 0;
};

SchedulerScoutGrid.prototype.checkTimers = function () {
   var this_obj = this;
   this.forEachRow(function (r_id) {
      var status = this_obj.getColumnValue(r_id, "Status");
      var alert_time = this_obj.getStatusAlertTime(status);
      var reminder_time = this_obj.getStatusReminderTime(status);

      if (alert_time > 0) {
         var mins = this_obj.getColumnValue(r_id, "MinsSinceLastUpdate");
         var s_room = this_obj.getColumnValue(r_id, "Room");

         if (parseInt(mins) >= parseInt(alert_time)) {
            if (typeof this_obj.roomTimerUpdateFunction === "function") {
               this_obj.lastNotice[r_id] = "" + mins;
               if (mins < (parseInt(alert_time) + parseInt(reminder_time))) {
                  this_obj.roomTimerUpdateFunction(s_room, mins, "alert");
               } else {
                  this_obj.roomTimerUpdateFunction(s_room, mins, "reminder");
               }
            }
         } else {
            if (typeof this_obj.roomTimerUpdateFunction === "function") {
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
      this.setButtonStatus(["Verify", "Postpone", "Locate", "Filter"]);
   } else if (status === "Verified") {
      this.setButtonStatus(["Seat", "Reset", "Postpone", "Locate", "Filter"]);
   } else if (status === "Seated") {
      this.setButtonStatus(["Reset", "InProgress", "Postpone", "Locate", "Filter"]);
   } else if (status === "InProgress") {
      this.setButtonStatus(["Reset", "Complete", "Locate", "Filter"]);
   } else if (status === "Completed") {
      this.setButtonStatus(["Locate", "Filter"]);
   } else if (status === "Postponed") {
      this.setButtonStatus(["Locate", "Filter"]);
   } else {
      this.setButtonStatus(["Filter"]);
   }
};
