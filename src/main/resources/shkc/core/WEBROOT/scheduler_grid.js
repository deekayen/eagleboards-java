// ------------------------------------------------------------------------
// scheduler_grid.js — base grid for the scheduler page, backed by
// Tabulator. Replaces the original SchedulerGrid implementation.
//
//   container_id : id of the <div> that hosts the Tabulator table
//   title        : panel title (informational)
//   url          : "-cells" endpoint path (may already carry a query)
//   columns      : Tabulator column definitions; each must set `field`
//                  to the server column name
//   colnames     : array of server column names to request via cols=
// ------------------------------------------------------------------------

// Display form of UnitName for the scheduler's Unit columns. DISPLAY ONLY --
// the stored value and every CSV export keep the whole word, because the CSV
// is generated server-side and a Tabulator formatter cannot reach it.
//
// A numbered unit collapses to its initial and number, "Troop2" -> "T2": the
// number is what identifies it, the type is obvious in context, and the column
// is 70px on a screen showing four grids at once.
//
// A unit type with NO number keeps the whole word. Abbreviating those is
// exactly the ambiguity the stored value was widened to fix -- "District"
// would become "D", and "Council" and "Community" would both become "C".
// A unit type with an empty number ("Troop" with no unit, which exists in the
// history) also falls through to the whole word rather than a bare "T".
function ebUnitLabel(value) {
   if (!value) {
      return "";
   }
   var numbered = /^([A-Za-z])[A-Za-z]*([0-9]+)$/.exec(value);
   return numbered ? numbered[1] + numbered[2] : value;
}

function SchedulerGrid(container_id, title, url, columns, colnames) {
   if (!container_id) {
      return; // prototype-chain construction
   }

   if (url.indexOf("?") > 0) {
      url = url + "&";
   } else {
      url = url + "?";
   }
   this.url = url;
   this.title = title;
   this.COLNAMEARR = colnames;
   this.containerEl = document.getElementById(container_id);

   // Selection is managed here rather than by Tabulator so that it
   // behaves exactly like the old grid: a click always selects (never
   // toggles off) and programmatic selects don't run the user cascade.
   this._selectedId = null;

   var this_obj = this;

   this.table = new Tabulator("#" + container_id, {
      layout: "fitColumns",
      index: "id",
      columnHeaderSortMulti: false,
      placeholder: "No records",
      columns: columns,
      rowFormatter: function (row) {
         this_obj.styleRow(row);
      }
   });

   this.table.on("rowClick", function (e, row) {
      this_obj.setSelected(row.getIndex());
      this_obj.onUserSelect(row.getIndex());
   });

   // Tabulator initializes asynchronously; data/filter calls must wait.
   this._built = false;
   this.ready = new Promise(function (resolve) {
      this_obj.table.on("tableBuilt", function () {
         this_obj._built = true;
         resolve();
      });
   });
}

SchedulerGrid.prototype.constructor = SchedulerGrid;

// Row coloring; overridden by the adult grid.
SchedulerGrid.prototype.styleRow = function (row) {
   SCHEDULER_styleRowByStatus(row, row.getIndex() === this._selectedId);
};

SchedulerGrid.prototype.isRowSelected = function (id) {
   return id === this._selectedId;
};

// Update the selection marker and restyle the affected rows.
SchedulerGrid.prototype.setSelected = function (id) {
   var prev = this._selectedId;
   this._selectedId = id;
   if (prev && prev !== id && this.doesRowExist(prev)) {
      this.styleRow(this.table.getRow(prev));
   }
   if (id && this.doesRowExist(id)) {
      this.styleRow(this.table.getRow(id));
   }
};

// Called when the USER selects a row (not for programmatic selects).
SchedulerGrid.prototype.onUserSelect = function (id) {
};

SchedulerGrid.prototype.getSelectedRowId = function () {
   return this._selectedId;
};

SchedulerGrid.prototype.clearSelection = function () {
   this.setSelected(null);
};

SchedulerGrid.prototype.load = function () {
   this.refresh();
};

// Fetch rows from the "-cells" endpoint; the base url may already carry
// a query string (e.g. the board grid's filter=), so the cols= parameter
// is appended here rather than via ebFetchRows.
SchedulerGrid.prototype.fetchRows = function () {
   var cols = this.COLNAMEARR;
   var url = this.url + "cols=" + encodeURIComponent(cols.join(","));
   return fetch(url).then(function (r) { return r.text(); }).then(function (text) {
      var doc = ebParseXML(text);
      var out = [];
      var rows = doc.getElementsByTagName("row");
      for (var i = 0; i < rows.length; i++) {
         var rec = { id: rows[i].getAttribute("id") };
         var cells = rows[i].getElementsByTagName("cell");
         for (var c = 0; c < cells.length && c < cols.length; c++) {
            rec[cols[c]] = cells[c].textContent;
         }
         out.push(rec);
      }
      return out;
   });
};

SchedulerGrid.prototype.refresh = function () {
   var t = this;
   var selected = this.getSelectedRowId();
   // replaceData's redraw scrolls the table back to the top; a poll landing
   // mid-scroll (this fires every RefreshTimeSecs, unprompted by the user)
   // must not yank the view out from under whoever is reading it.
   var holder = this.containerEl ? this.containerEl.querySelector(".tabulator-tableholder") : null;
   var scrollTop = holder ? holder.scrollTop : 0;

   Promise.all([this.fetchRows(), SCHEDULER_configReady, this.ready])
      .then(function (results) {
         var rows = results[0];
         t.prepareRows(rows);
         rows = t.mergeIncoming(rows);
         return t.table.replaceData(rows).then(function () {
            if (selected && !t.table.getRow(selected)) {
               t._selectedId = null;    // selected record disappeared
            }
            t.highlight();
            t.doAfterLoad();
            if (holder) {
               holder.scrollTop = scrollTop;
            }
         });
      })
      .catch(function (e) {
         console.log("grid load failed (" + t.title + "): " + e);
      });
};

// Hook to massage row data before it hits the table.
SchedulerGrid.prototype.prepareRows = function (rows) {
};

// Hook for a subclass to reassert a local edit that is still in flight to
// the server (see SchedulerAdultGrid.mergeIncoming) -- a poll's fetch can
// resolve with pre-save data and would otherwise stomp it back.
SchedulerGrid.prototype.mergeIncoming = function (rows) {
   return rows;
};

SchedulerGrid.prototype.doAfterLoad = function () {
};

SchedulerGrid.prototype.getColumnValue = function (row_id, col_id) {
   if (!this._built) {
      return null;
   }
   var row = this.table.getRow(row_id);
   if (row) {
      var v = row.getData()[col_id];
      return (v == null) ? "" : v;
   }
   return null;
};

SchedulerGrid.prototype.setColumnValue = function (row_id, col_id, value) {
   var row = this.table.getRow(row_id);
   if (row) {
      var patch = { id: row_id };
      patch[col_id] = value;
      this.table.updateData([patch]);
   }
};

SchedulerGrid.prototype.forEachRow = function (f) {
   if (!this._built) {
      return;
   }
   var rows = this.table.getRows();
   for (var i = 0; i < rows.length; i++) {
      f(rows[i].getIndex());
   }
};

SchedulerGrid.prototype.doesRowExist = function (id) {
   return this._built && !!this.table.getRow(id);
};

// Programmatic select: does NOT run the user-select cascade, but does
// notify updateSelected (toolbar button state refresh).
SchedulerGrid.prototype.selectById = function (id) {
   if (this.doesRowExist(id)) {
      this.setSelected(id);
      var row = this.table.getRow(id);
      if (row && row.getElement()) {
         row.getElement().scrollIntoView({ block: "nearest" });
      }
      this.updateSelected(id);
   }
};

SchedulerGrid.prototype.updateSelected = function (id) {
};

SchedulerGrid.prototype.selectForRoom = function (room_num) {
   this.selectForRoomExt(room_num, true);
};

SchedulerGrid.prototype.selectForRoomExt = function (room_num, single_select) {
   var this_obj = this;
   var r_id = this.getSelectedRowId();

   if (single_select && r_id && (this.getColumnValue(r_id, "Room") == room_num)) {
      // already on the right row — nothing to do
      return;
   }
   this.clearSelection();
   this.forEachRow(function (b_id) {
      if (this_obj.getColumnValue(b_id, "Room") == room_num) {
         this_obj.selectById(b_id);
      }
   });
};

// Re-apply row colors (e.g. after selection or data changes).
SchedulerGrid.prototype.highlight = function () {
   var rows = this.table.getRows();
   for (var i = 0; i < rows.length; i++) {
      this.styleRow(rows[i]);
   }
};
