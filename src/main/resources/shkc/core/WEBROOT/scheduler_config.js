// ------------------------------------------------------------------------
// scheduler_config.js — scheduler configuration + shared UI helpers.
//
// Loads the DEFAULT config record (the room-timer settings) from
// /config-autofill?Name=DEFAULT&fmt=json. The server emits pseudo-JSON with
// unquoted keys, so it is parsed with a regex.
//
// Also provides the status labels and icons, the persistent messages, the
// dialog helpers and the right-click menu used by the scheduler page and the
// process_*.js board-lifecycle handlers.
// ------------------------------------------------------------------------

// How long the board may spend convening (status "Seated") before the scout
// is brought in. Red only -- the window is a cap, not something to aim at, so
// there is no yellow stage to warn that the board is approaching it. Override
// in config.properties with ConveneRedMins.
var SCHEDULER_ConveneRedTime = 30;

// Board-type room-card warning thresholds, in minutes since the scout was
// brought in (status "InProgress"). Yellow = running long, Red = overdue
// (the keys keep their old color names; the colors are SPEC.md D-13's). Defaults
// below; override in config.properties with keys ProjectYellowMins /
// ProjectRedMins / FinalYellowMins / FinalRedMins.
var SCHEDULER_ProjectYellowTime = 25;
var SCHEDULER_ProjectRedTime = 40;
var SCHEDULER_FinalYellowTime = 30;
var SCHEDULER_FinalRedTime = 45;

var SCHEDULER_Config = null;

// The server's toJSON emits keys without quotes: {Name: "DEFAULT", ...}
function ebParseLooseJSON(text) {
   var out = {};
   var found = false;
   var re = /([A-Za-z0-9_]+):\s*"((?:[^"\\]|\\.)*)"/g;
   var m;
   while ((m = re.exec(text)) !== null) {
      out[m[1]] = m[2];
      found = true;
   }
   return found ? out : null;
}

// Resolves once the config record has been fetched (or failed and the
// defaults kept). The page waits on this before its first render, so the
// room timers never flash the default thresholds.
//
// RefreshTimeSecs is not read: the Event page listens on /events instead of
// polling (D-15), and the check-in pages load their lists when they open.
//
// The status colors (RegisteredColor..PostponedHiColor) are retired (SPEC.md
// D-19): the server no longer serves them, status colors are the shared
// palette in eb-app.css (D-13), and an older file that still sets them loads
// fine with the keys ignored.
var SCHEDULER_configReady = fetch("/config-autofill?Name=DEFAULT&fmt=json")
   .then(function (r) { return r.text(); })
   .then(function (text) {
      SCHEDULER_Config = ebParseLooseJSON(text);
      if (SCHEDULER_Config) {
         SCHEDULER_ConveneRedTime = parseInt(SCHEDULER_Config.ConveneRedMins, 10) || SCHEDULER_ConveneRedTime;
         SCHEDULER_ProjectYellowTime = parseInt(SCHEDULER_Config.ProjectYellowMins, 10) || SCHEDULER_ProjectYellowTime;
         SCHEDULER_ProjectRedTime = parseInt(SCHEDULER_Config.ProjectRedMins, 10) || SCHEDULER_ProjectRedTime;
         SCHEDULER_FinalYellowTime = parseInt(SCHEDULER_Config.FinalYellowMins, 10) || SCHEDULER_FinalYellowTime;
         SCHEDULER_FinalRedTime = parseInt(SCHEDULER_Config.FinalRedMins, 10) || SCHEDULER_FinalRedTime;
      }
      return SCHEDULER_Config;
   })
   .catch(function (e) {
      console.log("config load failed, using defaults: " + e);
      return null;
   });

// Room timer state for a board: "ok", "warn" (running long) or "over"
// (overdue), each drawn with its own clock (scheduler_event.js). The
// clock runs on MinsSinceLastUpdate, so it restarts by itself when the status
// changes -- which is what keeps the two phases timed apart:
//
//   "Seated"      the board is convening, reading the paperwork before the
//                 scout is called in. One cap, no yellow: the card stays okay
//                 and then goes straight to red once the board has held the
//                 room too long.
//   "InProgress"  the scout is in the room. Board-type specific yellow/red.
function SCHEDULER_timerState(status, btype, mins) {
   if (status === "Seated") {
      return mins >= SCHEDULER_ConveneRedTime ? "over" : "ok";
   }
   if (status !== "InProgress") {
      return "ok";
   }
   var yellow = (btype === "Final") ? SCHEDULER_FinalYellowTime : SCHEDULER_ProjectYellowTime;
   var red = (btype === "Final") ? SCHEDULER_FinalRedTime : SCHEDULER_ProjectRedTime;
   if (mins >= red) {
      return "over";
   }
   return mins >= yellow ? "warn" : "ok";
}

// What a timer state means, for the card's tooltip and screen readers --
// the color is never the only cue (D-13).
function SCHEDULER_timerMeaning(status, btype, state) {
   if (status === "Seated") {
      return state === "over"
         ? "Convening longer than " + SCHEDULER_ConveneRedTime + " min. Time to bring the youth in."
         : "Convening: the members are reading the paperwork.";
   }
   if (state === "over") {
      return "Review running past " + ((btype === "Final") ? SCHEDULER_FinalRedTime : SCHEDULER_ProjectRedTime) + " min.";
   }
   if (state === "warn") {
      return "Review running past " + ((btype === "Final") ? SCHEDULER_FinalYellowTime : SCHEDULER_ProjectYellowTime) + " min.";
   }
   return "Review under way.";
}

// ------------------------------------------------------------------------
// Status: text plus an icon, never color alone (D-13). The stored values
// keep their names; these are what the screen says.
// ------------------------------------------------------------------------
var status2Label = {
   "Registered": "Waiting",
   "Verified": "Waiting",
   "Seated": "Seated",
   "InProgress": "In review",
   "Completed": "Completed",
   "Postponed": "Postponed"
};

// Shapes, not colors, carry the distinction: a hollow ring waiting, a
// half-filled ring convening, a filled circle under way, a check for done,
// two bars for on hold.
var status2IconPath = {
   "Registered": "<circle cx='6' cy='6' r='4.25' fill='none' stroke='currentColor' stroke-width='1.7'/>",
   "Verified": "<circle cx='6' cy='6' r='4.25' fill='none' stroke='currentColor' stroke-width='1.7'/>",
   "Seated": "<path d='M6 1.5a4.5 4.5 0 000 9 4.5 4.5 0 010-9z' fill='currentColor'/><circle cx='6' cy='6' r='4.25' fill='none' stroke='currentColor' stroke-width='1.7'/>",
   "InProgress": "<circle cx='6' cy='6' r='4.5' fill='currentColor'/>",
   "Completed": "<path d='M2.2 6.3l2.4 2.4 5.2-5.6' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'/>",
   "Postponed": "<rect x='2.8' y='2' width='2' height='8' fill='currentColor'/><rect x='7.2' y='2' width='2' height='8' fill='currentColor'/>"
};

function SCHEDULER_statusLabel(status) {
   return status2Label[status] || status;
}

// A status pill: icon plus text, colored by the theme (eb-app.css), with the
// status as a class so each one can be told apart in high contrast too.
function SCHEDULER_statusHtml(status) {
   var path = status2IconPath[status];
   var icon = path
      ? "<svg class='eb-icon' viewBox='0 0 12 12' aria-hidden='true' focusable='false'>" + path + "</svg>"
      : "";
   return "<span class='eb-pill eb-pill-" + String(status).toLowerCase() + "'>" + icon
      + ebEscapeHtml(SCHEDULER_statusLabel(status)) + "</span>";
}

var status2numMap = {
   "Registered": 0,
   "Verified": 1,
   "Seated": 2,
   "InProgress": 3,
   "Completed": 4,
   "Postponed": 5
};

function sort2num(status) {
   var v = status2numMap[status];
   return (v != null) ? v : -1;
}

function sort_status(a, b) {
   return sort2num(a) - sort2num(b);
}

// Queue order by RegNum.
//
// RegNum is assigned at sign-in by RegisterScoutHandler: "P" plus a counter
// when the youth matches a pre-registration record (by ID, or by email), "W"
// plus a counter when nothing matches -- a walk-in. Each counter increments
// in sign-in order, so ordering by prefix then number keeps everyone in the
// order they arrived WITHIN their group, while holding walk-ins below the
// pre-registered queue where their lower priority is visible.
function regnum2rank(regnum) {
   var s = regnum || "";
   if (s.charAt(0) === "P") { return 0; }   // pre-registered
   if (s.charAt(0) === "W") { return 1; }   // walk-in
   return 2;                                // unprefixed/legacy: last
}

function sort_regnum(a, b) {
   var rank = regnum2rank(a) - regnum2rank(b);
   if (rank !== 0) {
      return rank;
   }
   // Numeric, not lexical: "W10" must follow "W9", not sit between W1 and W2.
   var na = parseInt(String(a || "").replace(/^[A-Za-z]+/, ""), 10);
   var nb = parseInt(String(b || "").replace(/^[A-Za-z]+/, ""), 10);
   return (isNaN(na) ? 0 : na) - (isNaN(nb) ? 0 : nb);
}

// What a RegNum means, since "P" and "W" say nothing on their own.
function SCHEDULER_regnumMeaning(regnum) {
   var v = regnum || "";
   var n = v.replace(/^[A-Za-z]+/, "");
   if (v.charAt(0) === "P") {
      return "Pre-registered: matched a sign-up. #" + n + " of the pre-registered to sign in.";
   }
   if (v.charAt(0) === "W") {
      return "Walk-in: no sign-up matched. #" + n + " of the walk-ins to sign in. Walk-ins wait behind the pre-registered.";
   }
   return "";
}

// Display form of UnitName. DISPLAY ONLY -- the stored value and every CSV
// export keep the whole word.
//
// A numbered unit collapses to its initial and number, "Troop2" -> "T2": the
// number is what identifies it and the type is obvious in context.
//
// A unit type with NO number keeps the whole word. Abbreviating those is
// exactly the ambiguity the stored value was widened to fix -- "District"
// would become "D", and "Council" and "Community" would both become "C".
function ebUnitLabel(value) {
   if (!value) {
      return "";
   }
   var numbered = /^([A-Za-z])[A-Za-z]*([0-9]+)$/.exec(value);
   return numbered ? numbered[1] + numbered[2] : value;
}

function ebBoardTypeLabel(btype) {
   return btype === "Project" ? "Project review" : (btype === "Final" ? "Final board" : btype);
}

// ------------------------------------------------------------------------
// Persistent messages, dialogs and menus.
// ------------------------------------------------------------------------

// D-14: a problem stays on screen next to what it's about until it's fixed
// or dismissed -- no self-dismissing toast. region is "main" | "scout" |
// "adult" | "room", matching the #<region>-messages slot beside the part of
// the page it concerns; omit it for a page-wide message. A new message in a
// region replaces that region's old one rather than stacking.
function ebMessageSlot(region) {
   return document.getElementById((region || "main") + "-messages");
}

// undoLabel, when given, adds an Undo button (O-2) that posts
// /restore-board and refreshes on success, or shows why not on failure
// (typically "Board changed since -- can't undo automatically", from
// something else having touched the same records first). Ctrl+Z presses the
// most recent one (P-3). The server keeps one level of undo, so every Undo
// message goes to the one page-wide slot, where each replaces the last and
// none is left sitting beside a different youth than it is about.
//
// A message in the "scout" region is about the youth selected when it was
// shown; the details pane hides it while another youth is selected (see
// renderDetails in scheduler_event.js).
function ebShowMessage(region, kind, title, html, undoLabel) {
   if (undoLabel) {
      region = "main";
   }
   var slot = ebMessageSlot(region);
   if (!slot) {
      return;
   }
   slot.innerHTML = "";
   var m = document.createElement("div");
   m.className = "eb-message eb-message-" + kind;
   if (region === "scout" && typeof selection !== "undefined" && selection.youthId) {
      m.setAttribute("data-youth", selection.youthId);
   }
   m.setAttribute("role", kind === "error" ? "alert" : "status");
   var actionsHtml = "<span class='eb-message-actions'>"
      + (undoLabel ? "<button type='button' class='eb-message-undo' title='Undo (Ctrl+Z)'>" + undoLabel + "</button>" : "")
      + "<button type='button' class='eb-message-dismiss' aria-label='Dismiss'>&times;</button>"
      + "</span>";
   m.innerHTML = "<span class='eb-message-body'><b>" + title + ":</b> " + html + "</span>" + actionsHtml;
   m.querySelector(".eb-message-dismiss").addEventListener("click", function () {
      m.remove();
   });
   if (undoLabel) {
      var undo = m.querySelector(".eb-message-undo");
      ebLatestUndo = undo;
      undo.addEventListener("click", function () {
         undo.disabled = true;
         ebAction("/restore-board", {}).then(function (res) {
            if (res.ok) {
               m.remove();
               refresh_all();
            } else {
               ebAlert("Undo error", res.text || "Could not undo.", region);
            }
         });
      });
   }
   slot.appendChild(m);
}

var ebLatestUndo = null;

// Ctrl+Z (P-3): the Undo on screen, if one is still showing.
function ebUndoLatest() {
   if (ebLatestUndo && ebLatestUndo.isConnected && !ebLatestUndo.disabled) {
      ebLatestUndo.click();
      return true;
   }
   return false;
}

function ebAlert(title, html, region, undoLabel) {
   ebShowMessage(region, "error", title, html, undoLabel);
}

function ebMessage(title, html, region, undoLabel) {
   ebShowMessage(region, "ok", title, html, undoLabel);
}

function ebDialogEl() {
   var dlg = document.createElement("dialog");
   dlg.className = "eb-dialog";
   document.body.appendChild(dlg);
   return dlg;
}

// Confirm dialog (P-4: native <dialog>/showModal). callback(true|false).
// Esc/backdrop close the same as Cancel. okLabel defaults to "OK" but
// should usually be the concrete verb the title asks about (P-4: buttons
// are verbs that answer the title).
function ebConfirm(title, html, callback, okLabel) {
   var dlg = ebDialogEl();
   dlg.innerHTML = "<h2 class='eb-dialog-title'>" + title + "</h2>"
      + "<div class='eb-dialog-body'>" + html + "</div>"
      + "<div class='eb-dialog-buttons'>"
      + "<button type='button' class='eb-cancel' data-name='Cancel'>Cancel</button>"
      + "<button type='button' class='eb-ok eb-accent'>" + (okLabel || "OK") + "</button>"
      + "</div>";
   var result = false;
   dlg.querySelector(".eb-ok").addEventListener("click", function () { result = true; dlg.close(); });
   dlg.querySelector(".eb-cancel").addEventListener("click", function () { dlg.close(); });
   dlg.addEventListener("close", function () {
      dlg.remove();
      if (callback) { callback(result); }
   });
   dlg.showModal();
   dlg.querySelector(".eb-ok").focus();
}

// Modal with arbitrary body HTML and named buttons (P-4: native <dialog>).
// buttons: [{name, label}], callback(name, bodyElement) — return false from
// callback to keep the dialog open (for validation). Esc/backdrop close
// without calling back, same as a Cancel button with no handler. The last
// button is the one the dialog is for, and is styled as such.
function ebModalForm(title, bodyHtml, buttons, callback) {
   var dlg = ebDialogEl();
   var btnHtml = "";
   for (var i = 0; i < buttons.length; i++) {
      var cls = (buttons[i].name === "Cancel") ? "eb-cancel" : "eb-accent";
      btnHtml += "<button type='button' class='" + cls + "' data-name='" + buttons[i].name + "'>" + buttons[i].label + "</button>";
   }
   dlg.innerHTML = "<h2 class='eb-dialog-title'>" + title + "</h2>"
      + "<form class='eb-dialog-body' method='dialog'></form>"
      + "<div class='eb-dialog-buttons'>" + btnHtml + "</div>";
   var body = dlg.querySelector(".eb-dialog-body");
   body.innerHTML = bodyHtml;
   var btns = dlg.querySelectorAll(".eb-dialog-buttons button");
   for (var b = 0; b < btns.length; b++) {
      (function (btn) {
         btn.addEventListener("click", function () {
            var keep = callback && callback(btn.getAttribute("data-name"), body);
            if (keep !== false) {
               dlg.close();
            }
         });
      })(btns[b]);
   }
   // Enter in a text field presses the dialog's own action, not Cancel.
   body.addEventListener("submit", function (ev) {
      ev.preventDefault();
      var action = dlg.querySelector(".eb-dialog-buttons .eb-accent");
      if (action) {
         action.click();
      }
   });
   dlg.addEventListener("close", function () { dlg.remove(); });
   dlg.showModal();
   var first = body.querySelector("input,select,textarea");
   if (first) { first.focus(); }
   return { close: function () { dlg.close(); }, body: body };
}

// P-1: a right-click menu built on the Popover API. items: an array of
// {label, run, disabled} (a disabled or missing item is left out); x/y:
// viewport coordinates (e.g. a contextmenu event's clientX/clientY).
//
// A manual popover, dismissed here rather than by the browser: on a Mac the
// contextmenu event fires on mouse-down, and an "auto" popover took the
// mouse-up that followed as a click outside and closed at once.
function ebContextMenu(items, x, y) {
   ebCloseContextMenu();

   var enabled = items.filter(function (item) { return item && !item.disabled; });
   if (enabled.length === 0) {
      return;
   }

   var menu = document.createElement("div");
   menu.id = "eb-context-menu";
   menu.className = "eb-context-menu";
   menu.setAttribute("popover", "manual");
   menu.setAttribute("role", "menu");

   enabled.forEach(function (item) {
      var b = document.createElement("button");
      b.type = "button";
      b.setAttribute("role", "menuitem");
      b.textContent = item.label;
      b.addEventListener("click", function () {
         ebCloseContextMenu();
         item.run();
      });
      menu.appendChild(b);
   });

   // Arrow keys move between items; Escape or Tab closes.
   menu.addEventListener("keydown", function (ev) {
      var buttons = Array.prototype.slice.call(menu.querySelectorAll("button"));
      var i = buttons.indexOf(document.activeElement);
      if (ev.key === "ArrowDown" || ev.key === "ArrowUp") {
         ev.preventDefault();
         var n = (i + (ev.key === "ArrowDown" ? 1 : -1) + buttons.length) % buttons.length;
         buttons[n].focus();
      } else if (ev.key === "Escape" || ev.key === "Tab") {
         ev.preventDefault();
         ebCloseContextMenu();
      }
   });

   document.body.appendChild(menu);
   menu.showPopover();
   // Keep it on screen near the right and bottom edges.
   var w = menu.offsetWidth;
   var h = menu.offsetHeight;
   menu.style.left = Math.max(4, Math.min(x, window.innerWidth - w - 4)) + "px";
   menu.style.top = Math.max(4, Math.min(y, window.innerHeight - h - 4)) + "px";
   menu.querySelector("button").focus();

   // Close on a press anywhere else -- a fresh one, not the mouse-up of the
   // right-click that opened it -- and when the window loses focus or resizes.
   setTimeout(function () {
      document.addEventListener("pointerdown", ebContextMenuOutside, true);
      window.addEventListener("blur", ebCloseContextMenu);
      window.addEventListener("resize", ebCloseContextMenu);
   }, 0);
}

function ebContextMenuOutside(ev) {
   var menu = document.getElementById("eb-context-menu");
   if (menu && !menu.contains(ev.target)) {
      ebCloseContextMenu();
   }
}

function ebCloseContextMenu() {
   document.removeEventListener("pointerdown", ebContextMenuOutside, true);
   window.removeEventListener("blur", ebCloseContextMenu);
   window.removeEventListener("resize", ebCloseContextMenu);
   var menu = document.getElementById("eb-context-menu");
   if (menu) {
      menu.remove();
   }
}
