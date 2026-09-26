// ------------------------------------------------------------------------
// scheduler_adult_grid.js — the "free adults to add from" grid inside the
// details pane (D-10). Not a separate always-ticked grid: clicking a row
// adds/removes that adult from the selected youth's board-in-progress
// (SchedulerDetailsPane owns what "the board-in-progress" means); a picked
// adult drops out of this list, since they're no longer free -- they show
// up in the details pane's own Members list instead.
//
// Columns:  Last, First, Unit, RM# (Room), Final (FinalBoard), Project
//           (ProjectReview), WB (WoodBadge)
//
// There was an "ST" column here: a 44px person icon rendered from the Room
// field -- plain when free, busy when seated, greyed when Room was "N/A".
// It restated what RM# already shows as text two columns over, the heading
// was an abbreviation spelled out nowhere, and the <img> carried no alt or
// title, so it was unreadable by hover or by screen reader. Removed rather
// than captioned; RM# is the readable form of the same field.
//
// Picks are client-side only (see SchedulerDetailsPane) -- there is no
// server column for "who's currently picked" anymore. Enable/Disable/Link
// live on the right-click menu only (P-1); each already carries the row id
// straight from the click, so none of them need a separate "selected row"
// concept the way picking briefly needed one before this rewrite.
// ------------------------------------------------------------------------

function SchedulerAdultGrid(container_id, toolbar_id, title) {
   var this_obj = null;

   SchedulerGrid.call(this, container_id, title, "/adult-cells",
      [
         { title: "Last", field: "Last", width: 90, headerFilter: "input" },
         { title: "First", field: "First", width: 90, headerFilter: "input" },
         // Display-only shortening; see scheduler_grid.js.
         { title: "Unit", field: "UnitName", width: 70, formatter: function (cell) { return ebUnitLabel(cell.getValue()); },
           headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "RM#", field: "Room", width: 60, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "Final", field: "FinalBoard", width: 90, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         { title: "Project", field: "ProjectReview", widthGrow: 1, headerFilter: "list", headerFilterParams: { valuesLookup: true, clearable: true } },
         // Volunteering toward a Wood Badge ticket item. Filterable, so the
         // ones still waiting for a board are easy to find.
         { title: "WB", field: "WoodBadge", width: 52, hozAlign: "center",
           headerTooltip: "Volunteering toward a Wood Badge ticket item",
           headerFilter: "list", headerFilterParams: { values: { "Y": "Yes", "": "No" }, clearable: true } }
      ],
      // RegTime and Supporting are not shown; auto-select reads them for its
      // tie-breaks, and Start Review uses Supporting to say whom to fetch.
      ["Last", "First", "UnitName", "Room", "FinalBoard", "ProjectReview", "WoodBadge", "RegTime", "Supporting"]);

   this_obj = this;

   this.showAll = false;   // old "Filter" two-state: off = hide assigned/unavailable/picked adults
   this._picked = {};       // r_id -> true, this session's picks for the selected youth

   this.toolbar = document.getElementById(toolbar_id);
   this.buttons = {};
   var names = ["Filter"];
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
      }
   });

   this.setFilterIcon(this.showAll);

   this.ready.then(function () {
      this_obj.table.setFilter(function (data) {
         if (this_obj._picked[data.id]) {
            return false;   // shown in the details pane's Members list instead
         }
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

// Adults are colored by availability, not status. RM# already shows the
// same room/"N/A" as text two columns over, so this is a redundant cue
// rather than the only one (see the file header on the "ST" icon column
// that was removed for the opposite reason: restating RM# with no
// accessible name of its own).
SchedulerAdultGrid.prototype.styleRow = function (row) {
   var el = row.getElement();
   var room = row.getData().Room || "";
   var selected = this.isRowSelected(row.getIndex());
   el.classList.remove("eb-adult-gone", "eb-adult-committed");
   if (room === "N/A") {
      el.classList.add("eb-adult-gone");
   } else if (room.length > 0) {
      el.classList.add("eb-adult-committed");
   }
   el.classList.toggle("eb-adult-selected", !!selected);
};

// Clicking a free adult adds them to the youth currently selected in the
// details pane; SchedulerDetailsPane decides whether that's allowed right
// now (a youth must be selected, and not already Completed/Postponed).
SchedulerAdultGrid.prototype.onUserSelect = function (l_id) {
   if (typeof detailsPane !== "undefined") {
      detailsPane.addMember(l_id);
   }
};

// P-1: Enable/Disable/Link, not View (that's a page setting, not something
// done to this adult). Each of these takes the row id directly rather than
// reading a "selected" row, so right-clicking works the same whether or
// not this row happens to be part of anyone's board-in-progress.
SchedulerAdultGrid.prototype.onContextMenu = function (l_id, e) {
   var this_obj = this;
   var room = this.getColumnValue(l_id, "Room");
   var items = [];
   if (room === "N/A") {
      items.push(this._menuButton("Enable", function () { this_obj.enableAdult(l_id); }));
   } else if (room === "") {
      items.push(this._menuButton("Disable", function () { this_obj.disableAdult(l_id); }));
   }
   items.push(this._menuButton("Link", function () { this_obj.toggleSupportLink(l_id); }));
   ebContextMenu(items, e.clientX, e.clientY);
};

// ebContextMenu takes real <button> elements (it reads their text/title and
// clones the click); build throwaway ones here since these actions no
// longer live in the toolbar at all.
SchedulerAdultGrid.prototype._menuButton = function (label, onClick) {
   var b = document.createElement("button");
   b.type = "button";
   b.textContent = label;
   b.addEventListener("click", onClick);
   return b;
};

SchedulerAdultGrid.prototype.doAfterLoad = function () {
   this.updateHidden(null);
};

// this youth's board is done with them, or the operator removed them --
// either way they're free to be picked again.
SchedulerAdultGrid.prototype.setPicked = function (r_id, state) {
   if (state) {
      this._picked[r_id] = true;
   } else {
      delete this._picked[r_id];
   }
   this.table.refreshFilter();
};

SchedulerAdultGrid.prototype.isPicked = function (r_id) {
   return !!this._picked[r_id];
};

SchedulerAdultGrid.prototype.pickRow = function (r_id) {
   this.setPicked(r_id, true);
};

SchedulerAdultGrid.prototype.unpickAll = function () {
   this._picked = {};
   this.table.refreshFilter();
};

SchedulerAdultGrid.prototype.getCheckedRowIds = function () {
   return Object.keys(this._picked).join();
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
   if (state == null) {
      state = this.showAll;
      this.setFilterIcon(state);
   }
   this.table.refreshFilter();
};

// Picks every adult currently in room_num, for viewing (read-only-ish) a
// board that's already Seated/InProgress -- not for building a new one.
SchedulerAdultGrid.prototype.pickForRoom = function (room_num) {
   var this_obj = this;
   this.unpickAll();
   this.forEachRow(function (r_id) {
      if (this_obj.getColumnValue(r_id, "Room") == room_num) {
         this_obj.setPicked(r_id, true);
      }
   });
};

// Enable/disable an adult by rewriting their Room value.
SchedulerAdultGrid.prototype.updateRoom = function (l_id, room_value) {
   var this_obj = this;
   var l_last = this_obj.getColumnValue(l_id, "Last");
   var l_first = this_obj.getColumnValue(l_id, "First");

   ebSaveRow("/adult-update", "updated", l_id, { Room: room_value })
      .then(function (ok) {
         if (ok) {
            var label = (room_value === "N/A") ? "Disabled" : "Enabled";
            ebMessage(label, l_first + " " + l_last + ".", "adult", "Undo");
         } else {
            ebAlert("Update Error", l_first + " " + l_last + " update failed.", "adult");
         }
      })
      .catch(function () {
         ebAlert("Update Error", l_first + " " + l_last + " update failed.", "adult");
      })
      .then(function () {
         setTimeout(function () {
            refresh_all();
         }, 500);
      });
};

SchedulerAdultGrid.prototype.disableAdult = function (l_id) {
   var this_obj = this;
   var room = this.getColumnValue(l_id, "Room");
   var lname = this.getColumnValue(l_id, "Last");
   var fname = this.getColumnValue(l_id, "First");

   if (room === "N/A") {
      ebAlert("Disable Error", fname + " " + lname + " already disabled.", "adult");
   } else if (room && (room.length > 0)) {
      ebAlert("Disable Error", fname + " " + lname + " is currently assigned to room " + room + ".", "adult");
   } else {
      ebConfirm("Confirm Disable", "Do you want to disable " + fname + " " + lname + "  ?", function (result) {
         if (result == true) {
            this_obj.updateRoom(l_id, "N/A");
         }
      }, "Disable");
   }
};

SchedulerAdultGrid.prototype.enableAdult = function (l_id) {
   var this_obj = this;
   var room = this.getColumnValue(l_id, "Room");
   var lname = this.getColumnValue(l_id, "Last");
   var fname = this.getColumnValue(l_id, "First");

   if (room !== "N/A") {
      ebAlert("Enable Error", fname + " " + lname + " is not disabled.", "adult");
   } else {
      ebConfirm("Confirm Enable", "Do you want to enable " + fname + " " + lname + "  ?", function (result) {
         if (result == true) {
            this_obj.updateRoom(l_id, "");
         }
      }, "Enable");
   }
};

// Link the given adult to the selected youth as someone who came to
// support them -- their Scoutmaster, say -- or undo that. For the adult who
// did not check the youth at sign-in, or signed in before the youth did.
// Start Review and Locate then name them. The same Supporting column the
// sign-in form writes, saved through /adult-update.
SchedulerAdultGrid.prototype.toggleSupportLink = function (l_id) {
   var this_obj = this;
   var s_id = schedulerScoutGrid.getSelectedRowId();
   if (!s_id) {
      ebAlert("Link", "Select the youth in the Youth list first, then right-click the adult to Link.", "adult");
      return;
   }

   var adult = ebEscapeHtml(this.getColumnValue(l_id, "First") + " " + this.getColumnValue(l_id, "Last"));
   var scout = ebEscapeHtml(schedulerScoutGrid.getColumnValue(s_id, "First") + " "
      + schedulerScoutGrid.getColumnValue(s_id, "Last"));
   var supporting = this.getColumnValue(l_id, "Supporting") || "";
   var linked = supporting.split("|").indexOf(s_id) >= 0;
   var updated = withSupportLink(supporting, s_id, !linked);
   var question = linked
      ? "<b>" + adult + "</b> is linked as supporting <b>" + scout + "</b>. Unlink them?"
      : "Link <b>" + adult + "</b> as supporting <b>" + scout + "</b>?"
        + "<br/><br/>Start Review will then say where to find them.";

   ebConfirm(linked ? "Unlink" : "Link", question, function (result) {
      if (!result) {
         return;
      }
      ebSaveRow("/adult-update", "updated", l_id, { Supporting: updated })
         .then(function (ok) {
            if (!ok) {
               ebAlert("Link Error", "The change was not saved.", "adult");
               return;
            }
            this_obj.table.updateData([{ id: l_id, Supporting: updated }]);
            ebMessage(linked ? "Unlinked" : "Linked",
               adult + (linked ? " is no longer linked to " : " is linked to ") + scout, "adult", "Undo");
         })
         .catch(function () {
            ebAlert("Link Error", "The change was not saved.", "adult");
         });
   }, linked ? "Unlink" : "Link");
};
