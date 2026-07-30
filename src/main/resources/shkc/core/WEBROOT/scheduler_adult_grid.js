// ------------------------------------------------------------------------
// scheduler_adult_grid.js — Adult Board Members panel (Tabulator).
//
// Columns:  @ (Sel checkbox), ST (availability icon), Last, First, Unit,
//           RM# (Room), Final (FinalBoard), Project (ProjectReview)
// Behavior: checking an adult marks them for the next Seat action and
//           bubbles checked rows to the top; row text turns red when the
//           adult is assigned to a room and grey when disabled (Room=N/A).
// Toolbar:  View/Hide (hide assigned/unavailable), Clear (uncheck all),
//           Enable / Disable (Room "" <-> "N/A" via /adult-update).
// ------------------------------------------------------------------------

function SchedulerAdultGrid(container_id, toolbar_id, title) {
   var this_obj = null;

   SchedulerGrid.call(this, container_id, title, "/adult-cells",
      [
         {
            title: "@", field: "Sel", width: 40, hozAlign: "center",
            sorter: "string", headerSort: true,
            formatter: function (cell) {
               var checked = cell.getValue() == "1";
               return "<input type='checkbox'" + (checked ? " checked" : "") + "/>";
            },
            cellClick: function (e, cell) {
               if (e.target && e.target.tagName === "INPUT") {
                  this_obj.onCheck(cell.getRow().getIndex(), e.target.checked);
               }
            }
         },
         {
            title: "ST", field: "Room", width: 44, hozAlign: "center", headerSort: false,
            formatter: function (cell) {
               var room = cell.getValue() || "";
               var icon = "im-user.png";
               if (room === "N/A") {
                  icon = "im-user-offline.png";
               } else if (room.length > 0) {
                  icon = "im-user-busy.png";
               }
               return "<img style='vertical-align: middle' src='/images/24x24/" + icon + "'/>";
            }
         },
         { title: "Last", field: "Last", width: 90, headerFilter: "input" },
         { title: "First", field: "First", width: 90, headerFilter: "input" },
         { title: "Unit", field: "UnitName", width: 70, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "RM#", field: "Room", width: 60, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "Final", field: "FinalBoard", width: 90, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "Project", field: "ProjectReview", widthGrow: 1, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } }
      ],
      ["Sel", "Last", "First", "UnitName", "Room", "FinalBoard", "ProjectReview"]);

   this_obj = this;

   this.showAll = false;   // old "Filter" two-state: off = hide assigned/unavailable adults

   this.toolbar = document.getElementById(toolbar_id);
   this.buttons = {};
   var names = ["Filter", "Clear", "Enable", "Disable"];
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
         this_obj.showAll = !this_obj.showAll;
         this_obj.setFilterIcon(this_obj.showAll);
         this_obj.updateHidden(this_obj.showAll);
      } else if (id === "Clear") {
         this_obj.uncheckAll();
      } else if (id === "Disable") {
         var r_id = this_obj.getSelectedRowId();
         if (!r_id) {
            return;
         }
         var room = this_obj.getColumnValue(r_id, "Room");
         var lname = this_obj.getColumnValue(r_id, "Last");
         var fname = this_obj.getColumnValue(r_id, "First");

         if (room === "N/A") {
            ebAlert("Disable Error", fname + " " + lname + " already disabled.");
         } else if (room && (room.length > 0)) {
            ebAlert("Disable Error", fname + " " + lname + " is currently assigned to room " + room + ".");
         } else {
            ebConfirm("Confirm Disable", "Do you want to disable " + fname + " " + lname + "  ?", function (result) {
               if (result == true) {
                  this_obj.updateRoom(r_id, "N/A");
               }
            });
         }
      } else if (id === "Enable") {
         var e_id = this_obj.getSelectedRowId();
         if (e_id) {
            var e_room = this_obj.getColumnValue(e_id, "Room");
            var e_lname = this_obj.getColumnValue(e_id, "Last");
            var e_fname = this_obj.getColumnValue(e_id, "First");

            if (e_room !== "N/A") {
               ebAlert("Enable Error", e_fname + " " + e_lname + " is not disabled.");
            } else {
               ebConfirm("Confirm Enable", "Do you want to enable " + e_fname + " " + e_lname + "  ?", function (result) {
                  if (result == true) {
                     this_obj.updateRoom(e_id, "");
                  }
               });
            }
         }
      }
   });

   this.setFilterIcon(this.showAll);

   this.ready.then(function () {
      this_obj.table.setFilter(function (data) {
         if (!this_obj.showAll) {
            var room = data.Room || "";
            if (room.length > 0) {
               return false;
            }
            if (data.FinalBoard === "Unavailable") {
               return false;
            }
         }
         return true;
      });
   });
}

SchedulerAdultGrid.prototype = new SchedulerGrid();
SchedulerAdultGrid.prototype.constructor = SchedulerAdultGrid;

// Adults are colored by availability, not status.
SchedulerAdultGrid.prototype.styleRow = function (row) {
   var el = row.getElement();
   var room = row.getData().Room || "";
   var selected = this.isRowSelected(row.getIndex());
   if (room === "N/A") {
      el.style.color = "#888888";
   } else if (room.length > 0) {
      el.style.color = "#ff0000";
   } else {
      el.style.color = "#000000";
   }
   el.style.backgroundColor = selected ? "#e8f0fe" : "";
   el.style.textDecoration = selected ? "underline" : "";
};

SchedulerAdultGrid.prototype.prepareRows = function (rows) {
   for (var i = 0; i < rows.length; i++) {
      var v = rows[i].Sel;
      rows[i].Sel = (v == "1" || v == "true") ? "1" : "0";
   }
};

SchedulerAdultGrid.prototype.onUserSelect = function (l_id) {
   this.updateButtonStatus(l_id);
};

SchedulerAdultGrid.prototype.updateSelected = function (l_id) {
   this.updateButtonStatus(l_id);
};

SchedulerAdultGrid.prototype.doAfterLoad = function () {
   this.updateHidden(null);
   this.updateButtonStatus();
};

// User toggled a checkbox: persist Sel (dataProcessor parity), bubble
// checked rows to the top, recolor.
SchedulerAdultGrid.prototype.onCheck = function (r_id, state) {
   this.setChecked(r_id, state, true);
   if (state) {
      this.sortChecked();
   }
};

SchedulerAdultGrid.prototype.setChecked = function (r_id, state, persist) {
   this.table.updateData([{ id: r_id, Sel: state ? "1" : "0" }]);
   if (persist) {
      ebSaveRow("/adult-update", "updated", r_id, { Sel: state ? "1" : "0" });
   }
};

SchedulerAdultGrid.prototype.sortChecked = function () {
   this.table.setSort([{ column: "Sel", dir: "desc" }]);
   var rows = this.table.getRows("active");
   if (rows.length > 0) {
      rows[0].getElement().scrollIntoView({ block: "nearest" });
   }
};

SchedulerAdultGrid.prototype.checkRow = function (r_id) {
   // Persist. Sel is a server column, and refresh() replaces the grid's data
   // with whatever the server holds -- so a check that only existed locally
   // silently vanished at the next poll, taking an auto-selected board with it.
   this.setChecked(r_id, true, true);
   this.sortChecked();
};

SchedulerAdultGrid.prototype.uncheckAll = function () {
   var this_obj = this;
   var patches = [];
   this.forEachRow(function (r_id) {
      if (this_obj.getColumnValue(r_id, "Sel") == "1") {
         patches.push({ id: r_id, Sel: "0" });
      }
   });
   if (patches.length > 0) {
      this.table.updateData(patches);
      // Persist too, for the mirror-image reason: a local-only clear left Sel=1
      // on the server, so the next poll resurrected every box the operator had
      // just cleared. Clearing and checking have to agree on where truth lives.
      patches.forEach(function (patch) {
         ebSaveRow("/adult-update", "updated", patch.id, { Sel: "0" });
      });
   }
};

SchedulerAdultGrid.prototype.getCheckedRowIds = function () {
   var results = [];
   var this_obj = this;
   this.forEachRow(function (r_id) {
      if (this_obj.getColumnValue(r_id, "Sel") == "1") {
         results.push(r_id);
      }
   });
   return results.join();
};

SchedulerAdultGrid.prototype.setFilterIcon = function (state) {
   var b = this.buttons["Filter"];
   if (state) {
      b.textContent = "Hide";
      b.title = "Hide Assigned Leaders";
   } else {
      b.textContent = "View";
      b.title = "Show All Leaders";
   }
};

SchedulerAdultGrid.prototype.updateHidden = function (state) {
   var this_obj = this;
   if (state == null) {
      state = this.showAll;
      this.setFilterIcon(state);
   }
   if (state) {
      // when showing everyone, drop stale checks on assigned adults
      var patches = [];
      this.forEachRow(function (r_id) {
         var room = this_obj.getColumnValue(r_id, "Room") || "";
         if (room.length > 0 && this_obj.getColumnValue(r_id, "Sel") == "1") {
            patches.push({ id: r_id, Sel: "0" });
         }
      });
      if (patches.length > 0) {
         this.table.updateData(patches);
      }
   }
   this.table.refreshFilter();
   this.updateButtonStatus();
};

SchedulerAdultGrid.prototype.selectForRoom = function (room_num) {
   var this_obj = this;
   this.showAll = true;
   this.setFilterIcon(true);
   this.table.refreshFilter();
   this.forEachRow(function (r_id) {
      if (this_obj.getColumnValue(r_id, "Room") == room_num) {
         this_obj.setChecked(r_id, true, false);
         this_obj.selectById(r_id);
      }
   });
   this.sortChecked();
};

// Auto-select up to cnt available adults qualified as member_type_arr
// (["Chair"] or ["Member"] or both) for the given board type, skipping
// the scout's own unit and anyone in omit_ids. Checks them in the grid.
SchedulerAdultGrid.prototype.findBoardMembers = function (s_uname, s_btype, member_type_arr, cnt, omit_ids) {
   var this_obj = this;
   var member_arr = [];

   this.forEachRow(function (l_id) {
      if (omit_ids.indexOf(l_id) >= 0) {
         return;
      }
      var l_uname = this_obj.getColumnValue(l_id, "UnitName");
      var l_final = this_obj.getColumnValue(l_id, "FinalBoard");
      var l_project = this_obj.getColumnValue(l_id, "ProjectReview");
      var l_room = this_obj.getColumnValue(l_id, "Room");

      if (l_room == "" || l_room == "-") { // not occupied
         if (l_uname != s_uname) {
            if (((s_btype == "Final") && (member_type_arr.indexOf(l_final) >= 0))
                  || ((s_btype == "Project") && (member_type_arr.indexOf(l_project) >= 0))) {
               if (member_arr.length < cnt) {
                  this_obj.checkRow(l_id);
                  member_arr.push(l_id);
               }
            }
         }
      }
   });
   return member_arr;
};

SchedulerAdultGrid.prototype.setButtonStatus = function (enabled_buttons) {
   for (var name in this.buttons) {
      if (this.buttons[name]) {
         this.buttons[name].disabled = (enabled_buttons.indexOf(name) < 0);
      }
   }
};

SchedulerAdultGrid.prototype.updateButtonStatus = function (l_id) {
   if (!l_id) {
      l_id = this.getSelectedRowId();
   }
   if (!l_id) {
      this.setButtonStatus(["Filter", "Clear"]);
      return;
   }
   var room = this.getColumnValue(l_id, "Room");

   if (room === "N/A") {
      this.setButtonStatus(["Enable", "Clear", "Filter"]);
   } else if (room === "") {
      this.setButtonStatus(["Disable", "Clear", "Filter"]);
   } else {
      this.setButtonStatus(["Filter", "Clear"]);
   }
};

// Enable/disable an adult by rewriting their Room value.
SchedulerAdultGrid.prototype.updateRoom = function (l_id, room_value) {
   var this_obj = this;
   var l_last = this_obj.getColumnValue(l_id, "Last");
   var l_first = this_obj.getColumnValue(l_id, "First");

   ebSaveRow("/adult-update", "updated", l_id, { Room: room_value })
      .then(function (ok) {
         if (!ok) {
            ebAlert("Update Error", l_first + " " + l_last + " update failed.");
         }
      })
      .catch(function () {
         ebAlert("Update Error", l_first + " " + l_last + " update failed.");
      })
      .then(function () {
         setTimeout(function () {
            refresh_all();
         }, 500);
      });
};
