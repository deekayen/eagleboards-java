// ------------------------------------------------------------------------
// scheduler_event.js — the Event page (scheduler.html): the youth queue, the
// room cards and the details pane, all visible at once (SPEC.md O-3).
//
//   youthStore / adultStore / roomStore  the rows the server has, fetched
//        from the -cells endpoints. The process_*.js handlers read them
//        through getColumnValue, the same name the old grids used.
//   boardBuilder  the board being built for a waiting youth, or the new
//        members of a board already seated: the members picked, the chair
//        marked, the room chosen. Client-side only until Seat board or Save
//        members posts it.
//   render()  redraws the three panels from those. Nothing else touches the
//        page's lists, so a refresh can never leave two panels disagreeing.
//
// Nothing polls (SPEC.md D-15). The server's /events stream says when any
// data changed and the page re-reads then; the room timers tick on the
// minute from the last read, without asking the server again.
// ------------------------------------------------------------------------

// ------------------------------------------------------------ data stores
function EventTable(path, cols) {
   this.path = path;
   this.cols = cols;
   this.rows = [];
   this.byId = {};
   this.signature = "";
}

// Resolves true when anything differs from what was already loaded.
EventTable.prototype.load = function () {
   var t = this;
   return ebFetchRows(this.path, this.cols).then(function (rows) {
      var signature = JSON.stringify(rows);
      if (signature === t.signature) {
         return false;
      }
      t.signature = signature;
      t.loadedAt = Date.now();
      t.rows = rows;
      t.byId = {};
      rows.forEach(function (r) { t.byId[r.id] = r; });
      return true;
   });
};

EventTable.prototype.get = function (id) {
   return this.byId[id] || null;
};

// "" for a blank column, null for a row that does not exist -- the contract
// the process_*.js handlers were written against.
EventTable.prototype.getColumnValue = function (id, col) {
   var row = this.byId[id];
   if (!row) {
      return null;
   }
   var v = row[col];
   return (v == null) ? "" : v;
};

// A local patch ahead of the next load, for a change the server has already
// accepted (see SendSeatRequest). Clears the signature so that load redraws.
EventTable.prototype.setColumnValue = function (id, col, value) {
   var row = this.byId[id];
   if (row) {
      row[col] = value;
      this.signature = "";
   }
};

EventTable.prototype.forEachRow = function (f) {
   this.rows.forEach(function (r) { f(r.id); });
};

var youthStore = new EventTable("/youth-cells",
   ["RegNum", "MinsSinceLastUpdate", "Last", "First", "UnitName", "BoardType", "Room", "Status",
    "Leader", "LastUpdateTime", "BoardMembersIDs", "BoardChairID", "BoardChair", "BoardMembers",
    "Result", "Notes"]);
var adultStore = new EventTable("/adult-cells",
   ["Last", "First", "UnitName", "Room", "FinalBoard", "ProjectReview", "WoodBadge", "RegTime", "Supporting"]);
var roomStore = new EventTable("/room-cells", ["Room", "BoardType", "Scout", "Leaders"]);

function isWaiting(status) {
   // "Verified" is accepted for legacy records only; nothing sets it now.
   return status === "Registered" || status === "Verified";
}

function isOnBoard(status) {
   return status === "Seated" || status === "InProgress";
}

function isFinished(status) {
   return status === "Completed" || status === "Postponed";
}

// Minutes since a youth's status last changed, as of now: the server's count
// when the rows were read, plus the minutes since. Lets the room timers tick
// between reads without asking the server (D-15).
function minsOf(s) {
   var base = parseInt(s.MinsSinceLastUpdate, 10);
   if (isNaN(base)) {
      return "";
   }
   return base + Math.floor((Date.now() - (youthStore.loadedAt || Date.now())) / 60000);
}

function fullName(row) {
   return (row.First + " " + row.Last).trim();
}

// Values read back from the data files carry their escapes (SPEC.md D-1): a
// comma is stored as "~", so a list saved as "A,B" comes back after a restart
// as "A~B". Show a value with its commas, and split a list on either.
function shown(value) {
   return (value || "").replace(/~/g, ",");
}

function names(list) {
   return (list || "").split(/[,~]/).map(function (n) { return n.trim(); }).filter(Boolean);
}

function roleFor(adult, btype) {
   return btype === "Project" ? adult.ProjectReview : adult.FinalBoard;
}

// Room "" or "-" is free; "N/A" is the Disable marker for someone gone home.
function adultIsFreeRow(adult) {
   return adult.Room === "" || adult.Room === "-";
}

function roomIsFree(room) {
   return !room.Scout || room.Scout === "-";
}

function byRoomName(a, b) {
   return String(a.Room).localeCompare(String(b.Room), undefined, { numeric: true });
}

function byName(a, b) {
   return (a.Last + " " + a.First).localeCompare(b.Last + " " + b.First);
}

// The youth whose board is in a room, if any.
function youthInRoom(roomName) {
   for (var i = 0; i < youthStore.rows.length; i++) {
      var s = youthStore.rows[i];
      if (s.Room === roomName && isOnBoard(s.Status)) {
         return s;
      }
   }
   return null;
}

// ---------------------------------------------------------- board builder
// The board being built for the selected waiting youth (D-10). Picks live
// here, not in the data: there is no server column for "who's picked".
var boardBuilder = {
   scoutId: null,
   picked: [],
   chairId: null,
   roomId: null,
   problems: [],
   needsProposal: false,
   // Changing the members of a board already seated, rather than building
   // one for a waiting youth: the room is fixed and Save members posts it.
   editing: false,

   memberIds: function () {
      return this.picked.slice();
   },

   reset: function () {
      this.picked = [];
      this.chairId = null;
      this.roomId = null;
      this.problems = [];
      this.editing = false;
   },

   // Start from the board as it sits now, chair first.
   startEditing: function (s) {
      this.reset();
      this.scoutId = s.id;
      this.editing = true;
      this.needsProposal = false;
      this.chairId = s.BoardChairID || null;
      var chair = this.chairId;
      this.picked = adultStore.rows
         .filter(function (a) { return a.Room === s.Room; })
         .sort(function (a, b) { return (a.id === chair ? -1 : b.id === chair ? 1 : byName(a, b)); })
         .map(function (a) { return a.id; });
      this.roomId = roomIdFor(s.Room);
      this.settleChair();
   },

   add: function (id) {
      if (this.picked.indexOf(id) < 0) {
         this.picked.push(id);
      }
      this.settleChair();
   },

   remove: function (id) {
      this.picked = this.picked.filter(function (p) { return p !== id; });
      if (this.chairId === id) {
         this.chairId = null;
      }
      this.settleChair();
   },

   // Keep a chair marked whenever someone picked may chair this board type:
   // the operator's choice if still valid, else the first qualified pick.
   settleChair: function () {
      var btype = youthStore.getColumnValue(this.scoutId, "BoardType");
      var qualified = this.picked.filter(function (id) {
         var a = adultStore.get(id);
         return a && roleFor(a, btype) === "Chair";
      });
      if (qualified.indexOf(this.chairId) < 0) {
         this.chairId = qualified.length > 0 ? qualified[0] : null;
      }
   }
};

// The shapes proposeBoard and fillBoard take (process_seat.js). Every adult
// is included; the proposal itself skips those who are not free.
function scoutForProposal(s_id) {
   return {
      id: s_id,
      uname: youthStore.getColumnValue(s_id, "UnitName"),
      btype: youthStore.getColumnValue(s_id, "BoardType")
   };
}

function adultsForProposal() {
   var adults = adultStore.rows.map(function (a) {
      return {
         id: a.id, uname: a.UnitName, final: a.FinalBoard, project: a.ProjectReview,
         room: a.Room, regTime: a.RegTime, woodBadge: a.WoodBadge, supporting: a.Supporting
      };
   });
   // How long each has waited to volunteer: since sign-in, or since the last
   // board they sat on was completed.
   var boards = youthStore.rows.map(function (s) {
      return { status: s.Status, memberIds: s.BoardMembersIDs, lastUpdate: s.LastUpdateTime };
   });
   var since = freeSinceTimes(adults, boards);
   adults.forEach(function (a) { a.freeSince = since[a.id]; });
   return adults;
}

// The other youth still waiting, in queue order.
function waitingForProposal(s_id) {
   return youthStore.rows
      .filter(function (s) { return s.id !== s_id && isWaiting(s.Status); })
      .sort(function (a, b) { return sort_regnum(a.RegNum, b.RegNum); })
      .map(function (s) { return { id: s.id, uname: s.UnitName, btype: s.BoardType, regnum: s.RegNum }; });
}

// The first free room of the youth's board type, or of any type.
function proposeRoom(btype) {
   var free = roomStore.rows.filter(roomIsFree).sort(byRoomName);
   var same = free.filter(function (r) { return r.BoardType === btype; });
   return same.length > 0 ? same[0].id : (free.length > 0 ? free[0].id : null);
}

// A fresh proposal for the whole board (D-5).
function proposeForBuilder() {
   var s_id = boardBuilder.scoutId;
   var proposal = proposeBoard(scoutForProposal(s_id), adultsForProposal(), waitingForProposal(s_id));
   boardBuilder.picked = [];
   if (proposal.chairId) {
      boardBuilder.picked.push(proposal.chairId);
   }
   boardBuilder.picked = boardBuilder.picked.concat(proposal.memberIds);
   boardBuilder.chairId = proposal.chairId;
   boardBuilder.problems = proposal.problems;
   boardBuilder.settleChair();
   if (!boardBuilder.roomId || !roomStore.get(boardBuilder.roomId) || !roomIsFree(roomStore.get(boardBuilder.roomId))) {
      boardBuilder.roomId = proposeRoom(youthStore.getColumnValue(s_id, "BoardType"));
   }
}

// ------------------------------------------------------------- selection
var selection = { youthId: null, roomId: null };

function selectYouth(s_id) {
   var s = youthStore.get(s_id);
   if (!s) {
      return;
   }
   if (boardBuilder.scoutId !== s_id) {
      boardBuilder.reset();
      boardBuilder.scoutId = s_id;
      boardBuilder.needsProposal = false;
      if (isWaiting(s.Status)) {
         proposeForBuilder();
      }
   }
   selection.youthId = s_id;
   selection.roomId = isOnBoard(s.Status) ? roomIdFor(s.Room) : (isWaiting(s.Status) ? boardBuilder.roomId : null);
   render();
}

function selectRoom(r_id) {
   var room = roomStore.get(r_id);
   if (!room) {
      return;
   }
   var occupant = youthInRoom(room.Room);
   if (occupant) {
      selectYouth(occupant.id);
      return;
   }
   // A free room: the target for the waiting youth being built, if any.
   var current = selection.youthId && youthStore.get(selection.youthId);
   if (current && isWaiting(current.Status)) {
      boardBuilder.roomId = r_id;
   } else {
      selection.youthId = null;
   }
   selection.roomId = r_id;
   render();
}

function roomIdFor(roomName) {
   for (var i = 0; i < roomStore.rows.length; i++) {
      if (roomStore.rows[i].Room === roomName) {
         return roomStore.rows[i].id;
      }
   }
   return null;
}

// --------------------------------------------------------------- helpers
function el(id) {
   return document.getElementById(id);
}

function h(text) {
   return ebEscapeHtml(text);
}

function icon(path, cls) {
   return "<svg class='eb-icon " + (cls || "") + "' viewBox='0 0 12 12' aria-hidden='true' focusable='false'>" + path + "</svg>";
}

var ICON_CHAIR = "<path d='M6 1.2 7.5 4.4 11 4.9 8.5 7.3 9.1 10.8 6 9.1 2.9 10.8 3.5 7.3 1 4.9 4.5 4.4Z' fill='currentColor'/>";
// The room timer's clocks (SPEC.md D-13), one per state and each a different
// outline, so the state reads without color: a stopwatch (crown and button)
// on time, a timer dial with its elapsed wedge running long, an alarm clock
// (bells and feet) overdue.
var ICON_STOPWATCH = "<circle cx='6' cy='6.8' r='4.2' fill='none' stroke='currentColor' stroke-width='1.3'/><path d='M4.7 1.2h2.6M6 1.2v1.4M9.3 3.5l.8-.8M6 6.8V4.6' fill='none' stroke='currentColor' stroke-width='1.3' stroke-linecap='round'/>";
var ICON_TIMER = "<circle cx='6' cy='6' r='4.6' fill='none' stroke='currentColor' stroke-width='1.3'/><path d='M6 6V2.8A3.2 3.2 0 0 1 9.2 6Z' fill='currentColor'/>";
var ICON_ALARM = "<circle cx='6' cy='6.6' r='3.9' fill='none' stroke='currentColor' stroke-width='1.3'/><path d='M1.4 3.2 3.2 1.4M10.6 3.2 8.8 1.4M3.7 9.8l-.8 1M8.3 9.8l.8 1M6 4.6v2l1.3.9' fill='none' stroke='currentColor' stroke-width='1.3' stroke-linecap='round' stroke-linejoin='round'/>";
var ICON_WARN = "<path d='M6 1.2 11.2 10.5H.8Z' fill='none' stroke='currentColor' stroke-width='1.3' stroke-linejoin='round'/><path d='M6 4.6v2.8' stroke='currentColor' stroke-width='1.3' stroke-linecap='round'/><circle cx='6' cy='9' r='.75' fill='currentColor'/>";
var ICON_ERROR = "<circle cx='6' cy='6' r='4.8' fill='none' stroke='currentColor' stroke-width='1.3'/><path d='M4 4l4 4M8 4 4 8' stroke='currentColor' stroke-width='1.3' stroke-linecap='round'/>";
var ICON_OK = "<path d='M2.2 6.3l2.4 2.4 5.2-5.6' fill='none' stroke='currentColor' stroke-width='1.6' stroke-linecap='round' stroke-linejoin='round'/>";
var ICON_REMOVE = "<path d='M3 3l6 6M9 3 3 9' stroke='currentColor' stroke-width='1.4' stroke-linecap='round'/>";

function timerIcon(state) {
   return icon(state === "over" ? ICON_ALARM : state === "warn" ? ICON_TIMER : ICON_STOPWATCH);
}

// The late states in words, for a screen reader: the card's name is its text,
// and the timer's tooltip is not part of it.
function timerWords(state) {
   return state === "over" ? "<span class='eb-visually-hidden'>, overdue</span>"
      : state === "warn" ? "<span class='eb-visually-hidden'>, running long</span>" : "";
}

function minsText(mins) {
   return (mins === "" || mins == null) ? "" : mins + " min";
}

function adultDetail(a, btype) {
   var role = roleFor(a, btype);
   var parts = [ebUnitLabel(a.UnitName)];
   if (role === "Chair") {
      parts.push("May chair");
   } else if (role === "Member") {
      parts.push("Member");
   } else if (role === "Unavailable") {
      parts.push("No thanks to " + (btype === "Project" ? "project reviews" : "final boards"));
   }
   if (a.WoodBadge === "Y") {
      parts.push("Wood Badge");
   }
   return parts.filter(function (p) { return !!p; }).join(" · ");
}

// Where an adult is, in words: for Locate, Start review and the supporting list.
function adultWhere(room) {
   if (room === "" || room === "-") {
      return "main room";
   }
   if (room === "N/A") {
      return "marked as gone home";
   }
   return "on the board in room " + room;
}

// ---------------------------------------------------------------- render
function render() {
   // Drop a selection whose record went away (deleted on the Admin page).
   if (selection.youthId && !youthStore.get(selection.youthId)) {
      selection.youthId = null;
   }
   if (selection.roomId && !roomStore.get(selection.roomId)) {
      selection.roomId = null;
   }
   var s = selection.youthId && youthStore.get(selection.youthId);
   if (s && boardBuilder.scoutId === s.id) {
      if (isOnBoard(s.Status) && boardBuilder.editing) {
         // Changing this board's members: keep the operator's edits.
      } else if (!isWaiting(s.Status)) {
         // Seated (here or in another window): nothing to build. If they
         // come back to waiting -- Undo, Reset -- propose afresh.
         boardBuilder.reset();
         boardBuilder.needsProposal = true;
      } else if (boardBuilder.needsProposal) {
         boardBuilder.needsProposal = false;
         proposeForBuilder();
         selection.roomId = boardBuilder.roomId;
      }
   }
   if (s && isOnBoard(s.Status)) {
      selection.roomId = roomIdFor(s.Room);
   }
   renderQueue();
   renderRooms();
   renderDetails();
}

// ----------------------------------------------------------------- queue
function renderQueue() {
   var show = el("queue-show").value;
   var find = el("queue-find").value.trim().toLowerCase();
   var matches = function (s) {
      if (!find) {
         return true;
      }
      return (s.First + " " + s.Last + " " + s.Last + " " + s.UnitName + " " + ebUnitLabel(s.UnitName)
         + " " + s.Room + " " + s.RegNum).toLowerCase().indexOf(find) >= 0;
   };

   var groups = [
      { key: "waiting", title: "Waiting", test: function (s) { return isWaiting(s.Status); },
        sort: function (a, b) { return sort_regnum(a.RegNum, b.RegNum); } },
      { key: "onboard", title: "On a board", test: function (s) { return isOnBoard(s.Status); },
        sort: function (a, b) { return String(a.Room).localeCompare(String(b.Room), undefined, { numeric: true }); } },
      { key: "finished", title: "Finished", test: function (s) { return isFinished(s.Status); },
        sort: function (a, b) { return a.LastUpdateTime < b.LastUpdateTime ? 1 : (a.LastUpdateTime > b.LastUpdateTime ? -1 : 0); } }
   ];
   var visible = {
      active: ["waiting", "onboard"], waiting: ["waiting"], onboard: ["onboard"],
      finished: ["finished"], all: ["waiting", "onboard", "finished"]
   }[show] || ["waiting", "onboard"];

   var html = "";
   groups.forEach(function (g) {
      if (visible.indexOf(g.key) < 0) {
         return;
      }
      var rows = youthStore.rows.filter(g.test).filter(matches).sort(g.sort);
      html += "<div class='eb-group' role='presentation'>" + h(g.title) + " (" + rows.length + ")</div>";
      if (rows.length === 0) {
         html += "<p class='eb-hint eb-group-empty'>" + (find ? "No one matches." : "No one.") + "</p>";
      }
      rows.forEach(function (s) {
         var sub = [s.RegNum, ebUnitLabel(s.UnitName)];
         sub.push(isOnBoard(s.Status) ? "Room " + s.Room : ebBoardTypeLabel(s.BoardType));
         if (s.Status === "Completed" && s.Result) {
            sub.push(s.Result === "NotApproved" ? "Not approved" : s.Result);
         }
         var mins = minsOf(s);
         var tip = isWaiting(s.Status) ? "Waiting " + minsText(mins)
            : isOnBoard(s.Status) ? SCHEDULER_statusLabel(s.Status) + " for " + minsText(mins)
            : minsText(mins) + " since finishing";
         var selected = s.id === selection.youthId;
         html += "<div class='eb-queue-item" + (selected ? " selected" : "") + "' role='option' tabindex='"
            + (selected ? "0" : "-1") + "' aria-selected='" + selected + "' data-id='" + h(s.id) + "'>"
            + "<span class='eb-queue-main'><span class='eb-queue-name'>" + h(fullName(s)) + "</span>"
            + "<span class='eb-hint' title='" + h(SCHEDULER_regnumMeaning(s.RegNum)) + "'>" + h(sub.filter(Boolean).join(" · ")) + "</span></span>"
            + "<span class='eb-queue-side'>" + (isWaiting(s.Status) ? "" : SCHEDULER_statusHtml(s.Status))
            + "<span class='eb-hint' title='" + h(tip) + "'>" + h(minsText(mins)) + "</span></span>"
            + "</div>";
      });
   });
   var list = el("queue-list");
   var hadFocus = list.contains(document.activeElement);
   list.innerHTML = html;
   // Keep one item reachable by Tab when nothing is selected yet.
   if (!list.querySelector(".eb-queue-item[tabindex='0']")) {
      var first = list.querySelector(".eb-queue-item");
      if (first) {
         first.tabIndex = 0;
      }
   }
   if (hadFocus) {
      var keep = list.querySelector(".eb-queue-item[tabindex='0']");
      if (keep) {
         keep.focus({ preventScroll: true });
      }
   }
}

// ----------------------------------------------------------------- rooms
function renderRooms() {
   var html = "";
   var rooms = roomStore.rows.slice().sort(byRoomName);
   if (rooms.length === 0) {
      html = "<p class='eb-hint'>No rooms yet. Add room adds the first.</p>";
   }
   rooms.forEach(function (r) {
      var s = youthInRoom(r.Room);
      var selected = r.id === selection.roomId;
      var target = !s && r.id === boardBuilder.roomId && selection.youthId === boardBuilder.scoutId && !!boardBuilder.scoutId;
      var timer = "";
      var state = "ok";
      var phase = "";
      if (s) {
         var mins = minsOf(s) || 0;
         state = SCHEDULER_timerState(s.Status, s.BoardType, mins);
         phase = s.Status === "Seated" ? "Convening" : "In review";
         timer = "<span class='eb-timer eb-timer-" + state + "' title='" + h(SCHEDULER_timerMeaning(s.Status, s.BoardType, state)) + "'>"
            + timerIcon(state) + h(minsText(mins)) + timerWords(state) + "</span>";
      }
      var body;
      if (s) {
         var members = names(r.Leaders);
         body = "<span class='eb-room-youth'>" + h(fullName(s)) + "</span>"
            + "<span class='eb-room-members'>" + members.map(function (n) {
               var chair = s.BoardChair && n === s.BoardChair.trim();
               return "<span" + (chair ? " class='eb-chair' title='Chair'" : "") + ">"
                  + (chair ? icon(ICON_CHAIR) : "") + h(n) + "</span>";
            }).join("") + "</span>";
      } else {
         body = "<span class='eb-room-free'>" + (target ? "Seating " + h(fullName(youthStore.get(boardBuilder.scoutId))) + " here" : "Free") + "</span>";
      }
      html += "<button type='button' class='eb-room eb-room-" + state + (selected ? " selected" : "") + (target ? " target" : "")
         + "' data-id='" + h(r.id) + "' aria-pressed='" + selected + "'>"
         + "<span class='eb-room-head'><span class='eb-room-name'>Room " + h(r.Room) + "</span>" + timer + "</span>"
         + "<span class='eb-hint'>" + h(ebBoardTypeLabel(r.BoardType)) + (phase ? " · " + phase : "") + "</span>"
         + body + "</button>";
   });
   el("room-grid").innerHTML = html;
   var room = selection.roomId && roomStore.get(selection.roomId);
   el("room-move").disabled = !room;
   el("room-rename").disabled = !room;
   el("room-remove").disabled = !room || !roomIsFree(room);
   el("room-remove").title = !room ? "Select a room to remove"
      : (roomIsFree(room) ? "Remove room " + room.Room : "Room " + room.Room + " has a board in it");
}

// --------------------------------------------------------------- details
function renderDetails() {
   var s = selection.youthId && youthStore.get(selection.youthId);
   var room = selection.roomId && roomStore.get(selection.roomId);
   el("details-empty").hidden = !!(s || room);
   el("details-room-only").hidden = !(room && !s);
   el("details-body").hidden = !s;

   if (!s && room) {
      el("dr-title").textContent = "Room " + room.Room;
      el("dr-sub").textContent = ebBoardTypeLabel(room.BoardType) + " · Free";
      return;
   }
   if (!s) {
      return;
   }

   el("d-name").textContent = fullName(s);
   var sub = [ebUnitLabel(s.UnitName), ebBoardTypeLabel(s.BoardType), s.RegNum];
   if (s.Leader) {
      sub.push("Leader: " + s.Leader);
   }
   el("d-sub").textContent = sub.filter(Boolean).join(" · ");
   el("d-sub").title = SCHEDULER_regnumMeaning(s.RegNum);
   el("d-status").innerHTML = SCHEDULER_statusHtml(s.Status);
   Array.prototype.forEach.call(el("scout-messages").children, function (m) {
      var about = m.getAttribute("data-youth");
      m.hidden = !!about && about !== s.id;
   });

   var editing = isOnBoard(s.Status) && boardBuilder.editing && boardBuilder.scoutId === s.id;
   el("d-build").hidden = !(isWaiting(s.Status) || editing);
   el("d-active").hidden = !isOnBoard(s.Status) || editing;
   el("d-finished").hidden = !isFinished(s.Status);

   if (isWaiting(s.Status) || editing) {
      renderBuilder(s);
   } else if (isOnBoard(s.Status)) {
      renderActive(s);
   } else {
      renderFinished(s);
   }
   renderSupport(s);
}

function renderBuilder(s) {
   var btype = s.BoardType;
   var editing = !isWaiting(s.Status);

   // Changing a seated board keeps its room; building a new one picks one.
   el("d-room").hidden = editing;
   el("d-room-fixed").hidden = !editing;
   el("d-room-fixed").textContent = "Room " + s.Room + " · " + ebBoardTypeLabel(btype)
      + " · " + (s.Status === "Seated" ? "convening" : "in review") + ". The timer keeps running.";
   el("d-seat").hidden = editing;
   el("d-postpone").hidden = editing;
   el("d-start-over").hidden = editing;
   el("d-save-members").hidden = !editing;
   el("d-cancel-change").hidden = !editing;

   // Room: free rooms of this board type first, then the rest, each labeled.
   var free = roomStore.rows.filter(roomIsFree).sort(byRoomName);
   var same = free.filter(function (r) { return r.BoardType === btype; });
   var other = free.filter(function (r) { return r.BoardType !== btype; });
   var opts = "<option value=''>" + (free.length ? "Choose a room" : "No free rooms") + "</option>";
   var option = function (r) {
      return "<option value='" + h(r.id) + "'" + (r.id === boardBuilder.roomId ? " selected" : "") + ">"
         + h(r.Room + " · " + ebBoardTypeLabel(r.BoardType)) + "</option>";
   };
   if (same.length) {
      opts += "<optgroup label='" + h(ebBoardTypeLabel(btype)) + " rooms'>" + same.map(option).join("") + "</optgroup>";
   }
   if (other.length) {
      opts += "<optgroup label='Other rooms'>" + other.map(option).join("") + "</optgroup>";
   }
   el("d-room").innerHTML = opts;

   // Members, with the chair marked by a radio among those who may chair.
   var list = "";
   boardBuilder.picked.forEach(function (id) {
      var a = adultStore.get(id);
      if (!a) {
         return;
      }
      var canChair = roleFor(a, btype) === "Chair";
      var sameUnit = s.UnitName && a.UnitName === s.UnitName;
      list += "<li class='eb-member' data-id='" + h(id) + "'>"
         + "<span class='eb-member-main'><span>" + h(fullName(a)) + "</span>"
         + "<span class='eb-hint'>" + h(adultDetail(a, btype)) + (sameUnit ? " · <span class='eb-warn-text'>Same unit</span>" : "") + "</span></span>"
         + (canChair ? "<label class='eb-check' title='This member chairs the board'><input type='radio' name='d-chair' value='"
            + h(id) + "'" + (id === boardBuilder.chairId ? " checked" : "") + "/> Chair</label>" : "")
         + "<button type='button' class='eb-icon-button' data-remove='" + h(id) + "' title='Remove from this board' aria-label='Remove "
         + h(fullName(a)) + "'>" + icon(ICON_REMOVE) + "</button></li>";
   });
   el("d-members").innerHTML = list || "<li class='eb-hint'>No one yet. Add members below, or Fill the rest.</li>";

   // Rules, checked as members are added: the same pure functions Seat
   // board applies (process_seat.js), and the server enforces again.
   var rules = builderRules(s);
   el("d-rules").innerHTML = rules.map(function (r) {
      return "<li class='eb-rule eb-rule-" + r.kind + "'>" + icon(r.kind === "error" ? ICON_ERROR : r.kind === "warn" ? ICON_WARN : ICON_OK)
         + "<span>" + h(r.text) + "</span></li>";
   }).join("");

   var blocked = rules.some(function (r) { return r.kind === "error"; });
   el("d-seat").disabled = blocked;
   el("d-save-members").disabled = blocked || (editing && !builderChanged(s));
   var minSize = membersBesideChair(btype) + 1;
   el("d-fill").disabled = boardBuilder.picked.length >= minSize && !!boardBuilder.chairId;
   el("d-start-over").disabled = false;

   renderAdultList(s);
}

// [{kind: "error"|"warn"|"ok", text}] for the board being built.
function builderRules(s) {
   var btype = s.BoardType;
   var editing = !isWaiting(s.Status);
   var rules = [];
   var picked = boardBuilder.picked.map(function (id) { return adultStore.get(id); }).filter(Boolean);

   picked.forEach(function (a) {
      if (a.Room === "N/A") {
         rules.push({ kind: "error", text: fullName(a) + " has been marked as gone home." });
      } else if (!adultIsFreeRow(a) && !(editing && a.Room === s.Room)) {
         rules.push({ kind: "error", text: fullName(a) + " is now on the board in room " + a.Room + "." });
      } else if (roleFor(a, btype) === "Unavailable") {
         rules.push({ kind: "error", text: fullName(a) + " said no thanks to " + (btype === "Project" ? "project reviews." : "final boards.") });
      }
   });

   var verdict = (btype === "Project") ? checkProjectSize(picked.length) : checkBoardSize(picked.length);
   var min = (btype === "Project") ? PROJECT_MIN_MEMBERS : BOARD_MIN_MEMBERS;
   if (verdict === "too-few") {
      rules.push({ kind: "error", text: "Needs at least " + min + " members; has " + picked.length + "." });
   } else if (verdict === "too-many") {
      rules.push({ kind: "error", text: "No more than " + BOARD_MAX_MEMBERS + " members; has " + picked.length + "." });
   } else if (verdict === "over-preferred") {
      rules.push({ kind: "warn", text: picked.length + " members, more than the usual " + min + ". "
         + (editing ? "Save members" : "Seat board") + " asks you to confirm." });
   }

   if (picked.length > 0 && !boardBuilder.chairId) {
      rules.push({ kind: "error", text: "No one here may chair a " + ebBoardTypeLabel(btype).toLowerCase()
         + ". Add a chair, or promote someone on People." });
   }

   var members = picked.map(function (a) { return { id: a.id, uname: a.UnitName, last: a.Last, first: a.First }; });
   var conflicts = findUnitConflicts(s.UnitName, members);
   if (conflicts.length > 0) {
      if (!hasNonUnitMember(s.UnitName, members)) {
         rules.push({ kind: "error", text: "Everyone here is in " + s.UnitName + ", the youth's own unit. Add someone from outside it." });
      } else {
         rules.push({ kind: "warn", text: conflicts.map(function (c) { return c.first + " " + c.last; }).join(", ")
            + (conflicts.length === 1 ? " is" : " are") + " in " + s.UnitName + ", the youth's own unit. "
            + (editing ? "Save members" : "Seat board") + " asks you to confirm." });
      }
   }

   var room = boardBuilder.roomId && roomStore.get(boardBuilder.roomId);
   if (editing) {
      // The board keeps its room.
   } else if (!room) {
      rules.push({ kind: "error", text: "Choose a room." });
   } else if (!roomIsFree(room)) {
      rules.push({ kind: "error", text: "Room " + room.Room + " is taken now. Choose another." });
   } else if (room.BoardType !== btype) {
      rules.push({ kind: "warn", text: "Room " + room.Room + " is set up for " + ebBoardTypeLabel(room.BoardType).toLowerCase() + "s." });
   }

   boardBuilder.problems.forEach(function (p) {
      rules.push({ kind: "warn", text: p });
   });

   if (!rules.some(function (r) { return r.kind === "error" || r.kind === "warn"; })) {
      if (!editing) {
         rules.push({ kind: "ok", text: "Ready to seat." });
      } else if (builderChanged(s)) {
         rules.push({ kind: "ok", text: "Ready to save." });
      } else {
         rules.push({ kind: "ok", text: "No changes yet. Remove someone, or add someone below." });
      }
   }
   return rules;
}

// Whether the members or chair picked differ from the board as it sits.
function builderChanged(s) {
   var now = adultStore.rows.filter(function (a) { return a.Room === s.Room; }).map(function (a) { return a.id; }).sort();
   var picked = boardBuilder.picked.slice().sort();
   return now.join(",") !== picked.join(",") || (boardBuilder.chairId || "") !== (s.BoardChairID || "");
}

function renderAdultList(s) {
   var btype = s.BoardType;
   // Changing a seated board: someone taken off it can be put back.
   var ownRoom = isWaiting(s.Status) ? null : s.Room;
   var isFreeHere = function (a) { return adultIsFreeRow(a) || a.Room === ownRoom; };
   var everyone = el("d-show-everyone").checked;
   var find = el("d-find-adult").value.trim().toLowerCase();
   var rows = adultStore.rows.filter(function (a) {
      if (boardBuilder.picked.indexOf(a.id) >= 0) {
         return false;
      }
      if (find && (a.First + " " + a.Last + " " + a.Last + " " + a.UnitName + " " + ebUnitLabel(a.UnitName))
            .toLowerCase().indexOf(find) < 0) {
         return false;
      }
      var available = isFreeHere(a) && roleFor(a, btype) !== "Unavailable";
      return everyone || available;
   });
   // Those who may chair this board type first, then by name.
   rows.sort(function (a, b) {
      var ca = roleFor(a, btype) === "Chair" ? 0 : 1;
      var cb = roleFor(b, btype) === "Chair" ? 0 : 1;
      return (ca - cb) || byName(a, b);
   });

   var html = rows.map(function (a) {
      var free = isFreeHere(a);
      var available = free && roleFor(a, btype) !== "Unavailable";
      var sameUnit = s.UnitName && a.UnitName === s.UnitName;
      var where = a.Room === ownRoom ? " · Leaving this board"
         : free ? "" : " · " + (a.Room === "N/A" ? "Gone home" : "Room " + a.Room);
      return "<li class='eb-adult" + (available ? "" : " eb-unavailable") + "' data-id='" + h(a.id) + "'>"
         + "<span class='eb-member-main'><span>" + h(fullName(a)) + "</span>"
         + "<span class='eb-hint'>" + h(adultDetail(a, btype) + where)
         + (sameUnit ? " · <span class='eb-warn-text'>Same unit</span>" : "") + "</span></span>"
         + "<button type='button' data-add='" + h(a.id) + "'" + (available ? "" : " disabled") + " aria-label='Add " + h(fullName(a)) + "'>Add</button>"
         + "</li>";
   }).join("");
   el("d-adults").innerHTML = html || "<li class='eb-hint'>" + (find ? "No one matches." : "No other adults are free for this board.") + "</li>";
}

function renderActive(s) {
   var mins = minsOf(s) || 0;
   var state = SCHEDULER_timerState(s.Status, s.BoardType, mins);
   el("d-active-room").innerHTML = "Room " + h(s.Room) + " · " + h(ebBoardTypeLabel(s.BoardType)) + " · "
      + "<span class='eb-timer eb-timer-" + state + "' title='" + h(SCHEDULER_timerMeaning(s.Status, s.BoardType, state)) + "'>"
      + timerIcon(state) + (s.Status === "Seated" ? "convening " : "in review ") + h(minsText(mins)) + timerWords(state) + "</span>";

   var members = adultStore.rows.filter(function (a) { return a.Room === s.Room; });
   members.sort(function (a, b) { return (a.id === s.BoardChairID ? -1 : b.id === s.BoardChairID ? 1 : byName(a, b)); });
   el("d-active-members").innerHTML = members.map(function (a) {
      var chair = a.id === s.BoardChairID;
      return "<li class='eb-member'><span class='eb-member-main'><span>" + (chair ? icon(ICON_CHAIR) : "") + h(fullName(a)) + "</span>"
         + "<span class='eb-hint'>" + h(ebUnitLabel(a.UnitName)) + (chair ? " · <span class='eb-accent-text'>Chairing this board</span>" : "")
         + "</span></span></li>";
   }).join("") || "<li class='eb-hint'>No members recorded.</li>";

   var step = PRIMARY_STEP[s.Status];
   el("d-primary").textContent = step.label;
   el("d-primary").title = step.tip + " (Ctrl+Enter)";
}

function renderFinished(s) {
   var facts = [];
   if (s.Status === "Completed") {
      facts.push(["Result", s.Result === "NotApproved" ? "Not approved" : (s.Result || "None recorded")]);
      facts.push(["Chair", shown(s.BoardChair)]);
      facts.push(["Members", names(s.BoardMembers).join(", ")]);
      if (s.Notes) {
         facts.push(["Notes", shown(s.Notes)]);
      }
   } else {
      facts.push(["Postponed", "Sent away before a board, usually because the paperwork wasn't in order."]);
   }
   el("d-facts").innerHTML = facts.map(function (f) {
      return "<dt>" + h(f[0]) + "</dt><dd>" + h(f[1] || "—") + "</dd>";
   }).join("");
}

// Adults who said they came to support this youth, and where they are now.
function supportingAdults(s_id) {
   return adultStore.rows.filter(function (a) {
      return (a.Supporting || "").split("|").indexOf(s_id) >= 0;
   }).map(function (a) {
      return { id: a.id, name: fullName(a), room: (a.Room === "" || a.Room === "-") ? "Main" : a.Room };
   });
}

function renderSupport(s) {
   var list = supportingAdults(s.id).map(function (a) {
      return "<li class='eb-member'><span class='eb-member-main'><span>" + h(a.name) + "</span>"
         + "<span class='eb-hint'>" + h(adultWhere(a.room === "Main" ? "" : a.room)) + "</span></span>"
         + "<button type='button' class='eb-icon-button' data-unlink='" + h(a.id) + "' title='No longer linked to this youth' aria-label='Unlink "
         + h(a.name) + "'>" + icon(ICON_REMOVE) + "</button></li>";
   }).join("");
   el("d-support").innerHTML = list || "<li class='eb-hint'>No one linked. Locate looks for leaders and parents by name and unit.</li>";
}

// The details pane's primary action follows the status (D-11, O-4).
var PRIMARY_STEP = {
   "Seated": { label: "Start review", run: "ProcessStartReview",
               tip: "Bring the youth in once the members have finished reading" },
   "InProgress": { label: "Complete", run: "ProcessCompleteBoard",
                   tip: "Record the result and free the room and its members" }
};

function runPrimary() {
   var s = selection.youthId && youthStore.get(selection.youthId);
   if (!s) {
      return;
   }
   if (isWaiting(s.Status)) {
      if (!el("d-seat").disabled) {
         ProcessSeatBoard(s.id);
      }
      return;
   }
   if (boardBuilder.editing && boardBuilder.scoutId === s.id) {
      if (!el("d-save-members").disabled) {
         ProcessChangeMembers(s.id);
      }
      return;
   }
   var step = PRIMARY_STEP[s.Status];
   if (step) {
      window[step.run](s.id);
   }
}

// ----------------------------------------------------- Locate and Link
// Locate a youth's leaders (by the Leader column) and parents (same unit and
// same last name) among the adults signed in. Those linked at sign-in come
// first and are not guessed at again. Returns HTML lines, "" if nobody.
function SCHEDULER_locateText(s_id, parents) {
   var s = youthStore.get(s_id);
   if (!s) {
      return "";
   }
   var s_last_lower = s.Last.toLowerCase();
   var s_leader_lower = (s.Leader || "").toLowerCase();
   var leader_match = [];
   var parent_match = [];

   adultStore.rows.forEach(function (a) {
      var found = false;
      if (s_leader_lower.indexOf(a.Last.toLowerCase()) >= 0) {
         if (s.UnitName === a.UnitName) {
            leader_match.push(a);
            found = true;
         } else if (s_leader_lower.indexOf(a.First.toLowerCase()) >= 0) {
            leader_match.push(a);
         }
      }
      if (!found && a.UnitName === s.UnitName && s_last_lower === a.Last.toLowerCase()) {
         parent_match.push(a);
      }
   });
   if (leader_match.length > 0 && parents === false) {
      parent_match = [];
   }

   var supporting = supportingAdults(s_id);
   var linked = supporting.map(function (a) { return a.id; });
   var notLinked = function (a) { return linked.indexOf(a.id) < 0; };
   leader_match = leader_match.filter(notLinked);
   parent_match = parent_match.filter(notLinked);

   var lines = [];
   supporting.forEach(function (a) {
      lines.push("Supporting: <b>" + h(a.name) + "</b>, " + h(adultWhere(a.room === "Main" ? "" : a.room)));
   });
   leader_match.forEach(function (a) {
      lines.push("Leader: <b>" + h(fullName(a)) + "</b>, " + h(adultWhere(a.Room)));
   });
   parent_match.forEach(function (a) {
      lines.push("Parent: <b>" + h(fullName(a)) + "</b>, " + h(adultWhere(a.Room)));
   });
   return lines.join("<br/>");
}

function SCHEDULER_locateAdults(s_id, parents) {
   var s = youthStore.get(s_id);
   if (!s) {
      return;
   }
   var text = SCHEDULER_locateText(s_id, parents);
   if (!text) {
      // Locate never changes anything, so this never offers Undo.
      ebAlert("Locate", "No leader or parent of " + h(fullName(s)) + " has signed in"
         + (s.Leader ? " (leader on file: " + h(s.Leader) + ")" : "") + ".", "scout");
      return;
   }
   ebMessage("Located", h(fullName(s)) + (s.Room && s.Room !== "N/A" ? " (room " + h(s.Room) + ")" : "")
      + "<br/>" + text, "scout");
}

// Kept for process_start.js, which names whom to fetch at Start review.
function SCHEDULER_supportingAdults(s_id) {
   return supportingAdults(s_id);
}

// Link an adult to the youth as someone who came to support them -- their
// Scoutmaster, say -- or undo that, for the adult who did not tick the youth
// at sign-in, or signed in before the youth did. The same Supporting column
// the sign-in form writes, saved through /adult-update.
function toggleSupportLink(l_id, s_id) {
   var a = adultStore.get(l_id);
   var s = youthStore.get(s_id);
   if (!a || !s) {
      return;
   }
   var linked = (a.Supporting || "").split("|").indexOf(s_id) >= 0;
   var updated = withSupportLink(a.Supporting, s_id, !linked);
   ebSaveRow("/adult-update", "updated", l_id, { Supporting: updated })
      .then(function (ok) {
         if (!ok) {
            ebAlert("Link error", "The change was not saved.", "scout");
            return;
         }
         ebMessage(linked ? "Unlinked" : "Linked",
            h(fullName(a)) + (linked ? " is no longer linked to " : " is linked to ") + h(fullName(s)) + ".", "scout", "Undo");
         refresh_all();
      })
      .catch(function () {
         ebAlert("Link error", "The change was not saved.", "scout");
      });
}

function linkAdultDialog(s_id) {
   var s = youthStore.get(s_id);
   var linked = supportingAdults(s_id).map(function (a) { return a.id; });
   var candidates = adultStore.rows.filter(function (a) { return linked.indexOf(a.id) < 0; }).sort(byName);
   if (candidates.length === 0) {
      ebAlert("Link", "Every adult signed in is already linked to " + h(fullName(s)) + ".", "scout");
      return;
   }
   var opts = candidates.map(function (a) {
      return "<option value='" + h(a.id) + "'>" + h(a.Last + ", " + a.First + " (" + ebUnitLabel(a.UnitName) + ")") + "</option>";
   }).join("");
   ebModalForm("Link an adult to " + h(fullName(s)) + "?",
      "<label>Adult<br/><select name='Adult'>" + opts + "</select></label>"
      + "<p class='eb-hint'>Start review then says where to find them, so someone can fetch them to introduce the youth.</p>",
      [{ name: "Cancel", label: "Cancel" }, { name: "Link", label: "Link" }],
      function (name, body) {
         if (name === "Link") {
            toggleSupportLink(body.querySelector("select[name='Adult']").value, s_id);
         }
      });
}

// Enable/disable an adult by rewriting their Room value ("N/A" = gone home).
function setAdultGone(l_id, gone) {
   var a = adultStore.get(l_id);
   if (!a) {
      return;
   }
   ebSaveRow("/adult-update", "updated", l_id, { Room: gone ? "N/A" : "" })
      .then(function (ok) {
         if (ok) {
            ebMessage(gone ? "Disabled" : "Enabled", h(fullName(a)) + (gone ? " is marked as gone home." : " is back."), "adult", "Undo");
         } else {
            ebAlert("Update error", h(fullName(a)) + " was not updated.", "adult");
         }
      })
      .catch(function () {
         ebAlert("Update error", h(fullName(a)) + " was not updated.", "adult");
      })
      .then(refresh_all);
}

function adultMenu(l_id, e) {
   var a = adultStore.get(l_id);
   var s_id = selection.youthId;
   if (!a) {
      return;
   }
   var linked = s_id && (a.Supporting || "").split("|").indexOf(s_id) >= 0;
   ebContextMenu([
      { label: "Add to this board", disabled: !adultIsFreeRow(a),
        run: function () { boardBuilder.add(l_id); render(); } },
      { label: "Disable (gone home)", disabled: !adultIsFreeRow(a), run: function () { setAdultGone(l_id, true); } },
      { label: "Enable (back)", disabled: a.Room !== "N/A", run: function () { setAdultGone(l_id, false); } },
      { label: linked ? "Unlink from this youth" : "Link to this youth", disabled: !s_id,
        run: function () { toggleSupportLink(l_id, s_id); } }
   ], e.clientX, e.clientY);
}

function youthMenu(s_id, e) {
   var s = youthStore.get(s_id);
   if (!s) {
      return;
   }
   var step = PRIMARY_STEP[s.Status];
   ebContextMenu([
      { label: "Seat board", disabled: !isWaiting(s.Status) || el("d-seat").disabled, run: runPrimary },
      { label: step ? step.label : "", disabled: !step, run: runPrimary },
      { label: "Locate", run: function () { SCHEDULER_locateAdults(s_id, true); } },
      { label: "Change members…", disabled: !isOnBoard(s.Status), run: function () { changeMembers(s_id); } },
      { label: "Link an adult…", run: function () { linkAdultDialog(s_id); } },
      { label: "Reset…", disabled: !isOnBoard(s.Status), run: function () { ProcessResetBoard(s_id); } },
      { label: "Postpone…", disabled: !isWaiting(s.Status), run: function () { ProcessPostponeBoard(s_id); } }
   ], e.clientX, e.clientY);
}

// ----------------------------------------------------------------- rooms
function addRoomDialog() {
   ebModalForm("Add a room",
      "<label>Room<br/><input type='text' name='Room' size='10' autocomplete='off'/></label><br/><br/>"
      + "<label>Used for<br/><select name='BoardType'>"
      + "<option value='Final'>Final boards</option>"
      + "<option value='Project'>Project reviews</option>"
      + "</select></label>",
      [{ name: "Cancel", label: "Cancel" }, { name: "Add", label: "Add room" }],
      function (name, body) {
         if (name !== "Add") {
            return;
         }
         var room = body.querySelector("input[name='Room']").value.trim();
         var type = body.querySelector("select[name='BoardType']").value;
         if (room === "") {
            ebAlert("Add room", "Give the room a name or number.", "room");
            return false;
         }
         if (roomStore.rows.some(function (r) { return r.Room === room; })) {
            ebAlert("Add room", "Room " + h(room) + " already exists.", "room");
            return false;
         }
         // A renamed room keeps its old ID, so "ROOM:101" may belong to a room
         // now called something else; take the next free ID if so.
         var id = "ROOM:" + room;
         for (var n = 2; roomStore.get(id); n++) {
            id = "ROOM:" + room + "-" + n;
         }
         ebSaveRow("/room-update", "inserted", id, { Room: room, BoardType: type })
            .then(function (ok) {
               if (!ok) {
                  ebAlert("Add room error", "Room " + h(room) + " was not added.", "room");
               }
            })
            .catch(function () {
               ebAlert("Add room error", "Room " + h(room) + " was not added.", "room");
            })
            .then(refresh_all);
      });
}

// Rename from the card. The server moves anyone on the room's board to the
// new name with it (/rename-room); an Admin-table edit would strand them.
function renameRoomDialog() {
   var room = selection.roomId && roomStore.get(selection.roomId);
   if (!room) {
      return;
   }
   ebModalForm("Rename room " + h(room.Room) + "?",
      "<label>New name<br/><input type='text' name='Room' size='12' autocomplete='off' value='" + h(room.Room) + "'/></label>"
      + (roomIsFree(room) ? "" : "<p class='eb-hint'>The board in it moves to the new name with it.</p>"),
      [{ name: "Cancel", label: "Cancel" }, { name: "Rename", label: "Rename" }],
      function (name, body) {
         if (name !== "Rename") {
            return;
         }
         var newName = body.querySelector("input[name='Room']").value.trim();
         if (newName === "") {
            ebAlert("Rename room", "Give the room a name or number.", "room");
            return false;
         }
         if (newName === room.Room) {
            return;
         }
         if (roomStore.rows.some(function (r) { return r.id !== room.id && r.Room === newName; })) {
            ebAlert("Rename room", "Room " + h(newName) + " already exists.", "room");
            return false;
         }
         ebAction("/rename-room", { RoomID: room.id, Room: newName })
            .then(function (res) {
               if (res.ok) {
                  ebMessage("Renamed", "Room " + h(room.Room) + " is now room " + h(newName) + ".", "room", "Undo");
               } else {
                  ebAlert("Rename error", "Room " + h(room.Room) + " was not renamed.<br/>" + h(res.text), "room");
               }
            })
            .catch(function () {
               ebAlert("Rename error", "Room " + h(room.Room) + " was not renamed.", "room");
            })
            .then(refresh_all);
      });
}

// Switch a room between final boards and project reviews. A board already
// in it is not disturbed (evening test section 17).
function switchRoomType(room) {
   var type = room.BoardType === "Project" ? "Final" : "Project";
   ebSaveRow("/room-update", "updated", room.id, { BoardType: type })
      .then(function (ok) {
         if (ok) {
            ebMessage("Room type", "Room " + h(room.Room) + " is now for " + h(ebBoardTypeLabel(type).toLowerCase()) + "s.", "room", "Undo");
         } else {
            ebAlert("Room error", "Room " + h(room.Room) + " was not changed.", "room");
         }
      })
      .catch(function () {
         ebAlert("Room error", "Room " + h(room.Room) + " was not changed.", "room");
      })
      .then(refresh_all);
}

function removeRoom() {
   var room = selection.roomId && roomStore.get(selection.roomId);
   if (!room) {
      return;
   }
   if (!roomIsFree(room)) {
      ebAlert("Remove room", "Room " + h(room.Room) + " has a board in it.", "room");
      return;
   }
   ebConfirm("Remove room " + h(room.Room) + "?", "It comes off the list for the rest of this event. You can add it again.",
      function (result) {
         if (!result) {
            return;
         }
         ebSaveRow("/room-update", "deleted", room.id)
            .then(function (ok) {
               if (!ok) {
                  ebAlert("Remove room error", "Room " + h(room.Room) + " was not removed.", "room");
               } else {
                  selection.roomId = null;
               }
            })
            .catch(function () {
               ebAlert("Remove room error", "Room " + h(room.Room) + " was not removed.", "room");
            })
            .then(refresh_all);
      }, "Remove");
}

// Move a board to a free room, or swap the boards in two rooms.
function moveRoomDialog() {
   var from = selection.roomId && roomStore.get(selection.roomId);
   if (!from) {
      return;
   }
   var describe = function (r) {
      var s = youthInRoom(r.Room);
      return r.Room + " · " + ebBoardTypeLabel(r.BoardType) + (s ? " · " + fullName(s) : " · Free");
   };
   var opts = roomStore.rows.filter(function (r) { return r.id !== from.id; }).sort(byRoomName).map(function (r) {
      return "<option value='" + h(r.id) + "'>" + h(describe(r)) + "</option>";
   }).join("");
   if (!opts) {
      ebAlert("Move", "There is no other room to move to.", "room");
      return;
   }
   ebModalForm("Move room " + h(from.Room) + "?",
      "<p>" + h(describe(from)) + "</p>"
      + "<label>To<br/><select name='RmID2'>" + opts + "</select></label>"
      + "<p class='eb-hint'>If that room has a board too, the two boards swap rooms.</p>",
      [{ name: "Cancel", label: "Cancel" }, { name: "Move", label: "Move" }],
      function (name, body) {
         if (name !== "Move") {
            return;
         }
         var to = roomStore.get(body.querySelector("select[name='RmID2']").value);
         if (!to) {
            return false;
         }
         var go = function () { changeRooms(from, to); };
         if (from.BoardType !== to.BoardType) {
            ebConfirm("Move anyway?", "Room " + h(from.Room) + " is set up for " + h(ebBoardTypeLabel(from.BoardType).toLowerCase())
               + "s and room " + h(to.Room) + " for " + h(ebBoardTypeLabel(to.BoardType).toLowerCase()) + "s.",
               function (ok) { if (ok) { go(); } }, "Move anyway");
         } else {
            go();
         }
      });
}

function changeRooms(from, to) {
   ebAction("/room-change", { RmID1: from.id, RmID2: to.id })
      .then(function (res) {
         if (res.ok) {
            // The cards already show the move (D-14); the message is only
            // there to offer Undo (O-2).
            ebMessage("Moved", "Room " + h(from.Room) + " ↔ room " + h(to.Room) + ".", "room", "Undo");
         } else {
            ebAlert("Move error", "Room " + h(from.Room) + " ↔ room " + h(to.Room) + " failed.<br/>" + h(res.text), "room");
         }
      })
      .catch(function () {
         ebAlert("Move error", "Room " + h(from.Room) + " ↔ room " + h(to.Room) + " failed.", "room");
      })
      .then(refresh_all);
}

// --------------------------------------------------------------- loading
var loading = null;
var loadAgain = false;

// Re-read everything and redraw if anything changed. Called when /events
// says something changed, and by the process_*.js handlers after each action.
// A change that arrives mid-read gets one more read once this one finishes.
function refresh_all() {
   if (loading) {
      loadAgain = true;
      return loading;
   }
   loading = Promise.all([youthStore.load(), adultStore.load(), roomStore.load()])
      .then(function (changed) {
         if (changed.indexOf(true) >= 0) {
            render();
         }
      })
      .catch(function (e) {
         console.log("refresh failed: " + e);
      })
      .then(function () {
         loading = null;
         if (loadAgain) {
            loadAgain = false;
            refresh_all();
         }
      });
   return loading;
}

// SPEC.md D-15: listen instead of polling. The first message on every
// (re)connection re-reads, so nothing that happened while the connection was
// down is missed. A drop is said once it has lasted a few seconds -- long
// enough not to flash for a blip, soon enough that nobody works on stale data.
var connectionTimer = null;

function listenForChanges() {
   var feed = new EventSource("/events");
   feed.onmessage = function () {
      refresh_all();
   };
   feed.onopen = function () {
      clearTimeout(connectionTimer);
      connectionTimer = null;
      el("connection-messages").innerHTML = "";
   };
   feed.onerror = function () {
      if (connectionTimer) {
         return;
      }
      connectionTimer = setTimeout(function () {
         ebAlert("Connection", "Lost touch with the scheduler, so this screen may be out of date. "
            + "Trying again every few seconds; is it still running?", "connection");
      }, 4000);
   };
}

// The timers tick on the minute (D-15) without a read: queue, room cards,
// and the open board's own timer.
function tickTimers() {
   renderQueue();
   renderRooms();
   var s = selection.youthId && youthStore.get(selection.youthId);
   if (s && isOnBoard(s.Status) && !boardBuilder.editing) {
      renderActive(s);
   }
}

// --------------------------------------------------- changing a seated board
function changeMembers(s_id) {
   var s = youthStore.get(s_id);
   if (!s || !isOnBoard(s.Status)) {
      return;
   }
   selection.youthId = s_id;
   boardBuilder.startEditing(s);
   render();
   el("d-find-adult").focus();
}

function stopChangingMembers() {
   boardBuilder.reset();
   boardBuilder.needsProposal = true;
   render();
}

// ------------------------------------------------------ check-in address
// Where the tablets at the door reach the check-in page, from the server:
// this browser usually has the scheduler open as localhost, which a tablet
// cannot use.
var checkinUrls = [];

function loadCheckinAddress() {
   fetch("/checkin-address")
      .then(function (r) { return r.json(); })
      .then(function (data) {
         checkinUrls = data.urls || [];
         var where = el("checkin-address");
         if (checkinUrls.length === 0) {
            where.textContent = "Check-in address: this computer isn't on a network, so the tablets can't reach it.";
         } else {
            where.innerHTML = "Check-in address: " + checkinUrls.map(function (u) {
               return "<a href='" + h(u) + "' target='_blank' rel='noopener'>" + h(u) + "</a>";
            }).join(" or ");
         }
         el("checkin-qr").hidden = checkinUrls.length === 0;
      })
      .catch(function () {
         el("checkin-address").textContent = "Check-in address: couldn't be found.";
      });
}

// A big QR code for a tablet's camera, with the address spelled out for
// typing. Black on white whatever the theme: that is what a camera reads.
function showCheckinQr() {
   if (checkinUrls.length === 0) {
      return;
   }
   var dlg = ebDialogEl();
   dlg.classList.add("eb-qr-dialog");
   var picker = checkinUrls.length > 1
      ? "<label>Network <select name='url'>" + checkinUrls.map(function (u) {
           return "<option>" + h(u) + "</option>";
        }).join("") + "</select></label>"
      : "";
   dlg.innerHTML = "<h2 class='eb-dialog-title'>Sign in at the door</h2>"
      + "<div class='eb-dialog-body'>"
      + "<img class='eb-qr' alt='QR code for the check-in page'/>"
      + "<p class='eb-qr-url'></p>"
      + "<p class='eb-hint'>Point a tablet's camera at this, or type the address into its browser.</p>"
      + picker + "</div>"
      + "<div class='eb-dialog-buttons'><button type='button' class='eb-accent'>Close</button></div>";
   var show = function (u) {
      dlg.querySelector(".eb-qr").src = "/checkin-qr?url=" + encodeURIComponent(u);
      dlg.querySelector(".eb-qr-url").textContent = u;
   };
   show(checkinUrls[0]);
   var select = dlg.querySelector("select");
   if (select) {
      select.addEventListener("change", function () { show(select.value); });
   }
   dlg.querySelector(".eb-dialog-buttons button").addEventListener("click", function () { dlg.close(); });
   dlg.addEventListener("close", function () { dlg.remove(); });
   dlg.showModal();
}

// ---------------------------------------------------------------- events
el("queue-show").addEventListener("change", renderQueue);
el("queue-find").addEventListener("input", renderQueue);

el("queue-list").addEventListener("click", function (ev) {
   var item = ev.target.closest(".eb-queue-item");
   if (item) {
      selectYouth(item.getAttribute("data-id"));
      el("queue-list").querySelector(".eb-queue-item[tabindex='0']").focus({ preventScroll: true });
   }
});
el("queue-list").addEventListener("contextmenu", function (ev) {
   var item = ev.target.closest(".eb-queue-item");
   if (item) {
      ev.preventDefault();
      selectYouth(item.getAttribute("data-id"));
      youthMenu(item.getAttribute("data-id"), ev);
   }
});
// Arrow keys move through the list; Enter or Space selects (listbox pattern).
el("queue-list").addEventListener("keydown", function (ev) {
   var items = Array.prototype.slice.call(el("queue-list").querySelectorAll(".eb-queue-item"));
   var i = items.indexOf(document.activeElement);
   if (i < 0) {
      return;
   }
   var next = null;
   if (ev.key === "ArrowDown") {
      next = items[Math.min(i + 1, items.length - 1)];
   } else if (ev.key === "ArrowUp") {
      next = items[Math.max(i - 1, 0)];
   } else if (ev.key === "Home") {
      next = items[0];
   } else if (ev.key === "End") {
      next = items[items.length - 1];
   } else if (ev.key === "Enter" || ev.key === " ") {
      ev.preventDefault();
      selectYouth(items[i].getAttribute("data-id"));
      return;
   }
   if (next) {
      ev.preventDefault();
      selectYouth(next.getAttribute("data-id"));
      var focused = el("queue-list").querySelector(".eb-queue-item[tabindex='0']");
      if (focused) {
         focused.focus();
         focused.scrollIntoView({ block: "nearest" });
      }
   }
});

el("room-grid").addEventListener("click", function (ev) {
   var card = ev.target.closest(".eb-room");
   if (card) {
      selectRoom(card.getAttribute("data-id"));
   }
});
el("room-grid").addEventListener("contextmenu", function (ev) {
   var card = ev.target.closest(".eb-room");
   if (!card) {
      return;
   }
   ev.preventDefault();
   selectRoom(card.getAttribute("data-id"));
   var room = roomStore.get(card.getAttribute("data-id"));
   ebContextMenu([
      { label: "Move or swap board…", run: moveRoomDialog },
      { label: "Rename…", run: renameRoomDialog },
      { label: room.BoardType === "Project" ? "Use for final boards" : "Use for project reviews",
        run: function () { switchRoomType(room); } },
      { label: "Remove room", disabled: !roomIsFree(room), run: removeRoom }
   ], ev.clientX, ev.clientY);
});
el("room-add").addEventListener("click", addRoomDialog);
el("room-move").addEventListener("click", moveRoomDialog);
el("room-rename").addEventListener("click", renameRoomDialog);
el("room-remove").addEventListener("click", removeRoom);

el("d-room").addEventListener("change", function () {
   boardBuilder.roomId = this.value || null;
   selection.roomId = boardBuilder.roomId;
   render();
});
el("d-members").addEventListener("click", function (ev) {
   var remove = ev.target.closest("[data-remove]");
   if (remove) {
      boardBuilder.remove(remove.getAttribute("data-remove"));
      boardBuilder.problems = [];
      render();
   }
});
el("d-members").addEventListener("change", function (ev) {
   if (ev.target.name === "d-chair") {
      boardBuilder.chairId = ev.target.value;
      render();
   }
});
el("d-fill").addEventListener("click", function () {
   var s_id = boardBuilder.scoutId;
   var fill = fillBoard(scoutForProposal(s_id), adultsForProposal(), boardBuilder.memberIds(), waitingForProposal(s_id));
   if (fill.chairId) {
      boardBuilder.add(fill.chairId);
      boardBuilder.chairId = fill.chairId;
   }
   fill.memberIds.forEach(function (id) { boardBuilder.add(id); });
   boardBuilder.problems = fill.problems;
   if (!boardBuilder.roomId) {
      boardBuilder.roomId = proposeRoom(youthStore.getColumnValue(s_id, "BoardType"));
   }
   render();
});
el("d-start-over").addEventListener("click", function () {
   proposeForBuilder();
   render();
});
el("d-seat").addEventListener("click", runPrimary);
el("d-save-members").addEventListener("click", runPrimary);
el("d-cancel-change").addEventListener("click", stopChangingMembers);
el("d-change").addEventListener("click", function () { changeMembers(selection.youthId); });
el("checkin-qr").addEventListener("click", showCheckinQr);
el("d-primary").addEventListener("click", runPrimary);
el("d-postpone").addEventListener("click", function () { ProcessPostponeBoard(selection.youthId); });
el("d-reset").addEventListener("click", function () { ProcessResetBoard(selection.youthId); });
el("d-locate").addEventListener("click", function () { SCHEDULER_locateAdults(selection.youthId, true); });
el("d-link").addEventListener("click", function () { linkAdultDialog(selection.youthId); });
el("d-support").addEventListener("click", function (ev) {
   var unlink = ev.target.closest("[data-unlink]");
   if (unlink) {
      toggleSupportLink(unlink.getAttribute("data-unlink"), selection.youthId);
   }
});

el("d-show-everyone").addEventListener("change", function () { renderAdultList(youthStore.get(selection.youthId)); });
el("d-find-adult").addEventListener("input", function () { renderAdultList(youthStore.get(selection.youthId)); });
el("d-adults").addEventListener("click", function (ev) {
   var add = ev.target.closest("[data-add]");
   if (add && !add.disabled) {
      boardBuilder.add(add.getAttribute("data-add"));
      render();
   }
});
el("d-adults").addEventListener("contextmenu", function (ev) {
   var row = ev.target.closest(".eb-adult");
   if (row) {
      ev.preventDefault();
      adultMenu(row.getAttribute("data-id"), ev);
   }
});

// P-3: Ctrl+Enter (Cmd+Enter on a Mac) is the primary action at every step;
// Ctrl+Z / Cmd+Z presses the Undo on screen, outside text fields.
document.addEventListener("keydown", function (ev) {
   var mod = ev.ctrlKey || ev.metaKey;
   if (!mod || document.querySelector("dialog[open]")) {
      return;
   }
   if (ev.key === "Enter") {
      ev.preventDefault();
      runPrimary();
   } else if ((ev.key === "z" || ev.key === "Z") && !ev.shiftKey) {
      var t = ev.target;
      var typing = t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA") && t.type !== "checkbox" && t.type !== "radio";
      if (!typing && ebUndoLatest()) {
         ev.preventDefault();
      }
   }
});

SCHEDULER_configReady.then(function () {
   refresh_all().then(function () {
      render();
      listenForChanges();
      setInterval(tickTimers, 20000);
   });
});
loadCheckinAddress();
