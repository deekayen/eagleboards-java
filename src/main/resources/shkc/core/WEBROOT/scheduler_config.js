// ------------------------------------------------------------------------
// scheduler_config.js — scheduler configuration + shared UI helpers.
//
// Loads the DEFAULT config record (refresh/alert timings and the status
// row colors) from /config-autofill?Name=DEFAULT&fmt=json. The server
// emits pseudo-JSON with unquoted keys, so it is parsed with a regex.
//
// Also provides the small dialog/toast/modal helpers used by the
// scheduler page and the process_*.js board-lifecycle handlers.
// ------------------------------------------------------------------------

var SCHEDULER_refreshTime = 10;

// How long the board may spend convening (status "Seated") before the scout
// is brought in. Red only -- the window is a cap, not something to aim at, so
// there is no yellow stage to warn that the board is approaching it. Override
// in config.properties with ConveneRedMins.
var SCHEDULER_ConveneRedTime = 30;

// Board-type room-card warning thresholds, in minutes since the scout was
// brought in (status "InProgress"). Yellow = warning, Red = overdue. Defaults
// below; override in config.properties with keys ProjectYellowMins /
// ProjectRedMins / FinalYellowMins / FinalRedMins.
var SCHEDULER_ProjectYellowTime = 25;
var SCHEDULER_ProjectRedTime = 40;
var SCHEDULER_FinalYellowTime = 30;
var SCHEDULER_FinalRedTime = 45;

var SCHEDULER_Config = null;

// Status appearance (D-13) is theme-driven: eb-ui.css defines a background
// (and its own dark-mode variant) for each of these classes, applied to the
// row alongside eb-row-selected. No JS color map, no inline styles.
var status2Class = {
   "Registered": "eb-status-registered",
   "Verified": "eb-status-verified",
   "Seated": "eb-status-seated",
   "InProgress": "eb-status-inprogress",
   "Completed": "eb-status-completed",
   "Postponed": "eb-status-postponed"
};

// A small currentColor glyph beside the status text, so status is never
// color alone (D-13). Shapes, not colors, carry the distinction: a hollow
// ring waiting, a half-filled ring convening, a filled circle under way, a
// check for done, two bars for on hold.
var status2IconPath = {
   "Registered": "<circle cx='6' cy='6' r='4.25' fill='none' stroke='currentColor' stroke-width='1.7'/>",
   "Verified": "<circle cx='6' cy='6' r='4.25' fill='none' stroke='currentColor' stroke-width='1.7'/><circle cx='6' cy='6' r='1.6' fill='currentColor'/>",
   "Seated": "<path d='M6 1.5a4.5 4.5 0 000 9 4.5 4.5 0 010-9z' fill='currentColor'/><circle cx='6' cy='6' r='4.25' fill='none' stroke='currentColor' stroke-width='1.7'/>",
   "InProgress": "<circle cx='6' cy='6' r='4.5' fill='currentColor'/>",
   "Completed": "<path d='M2.2 6.3l2.4 2.4 5.2-5.6' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'/>",
   "Postponed": "<rect x='2.8' y='2' width='2' height='8' fill='currentColor'/><rect x='7.2' y='2' width='2' height='8' fill='currentColor'/>"
};

// Tabulator "Status" column formatter for the scout and board grids.
function SCHEDULER_statusCellHtml(status) {
   var path = status2IconPath[status];
   var icon = path
      ? "<svg class='eb-status-icon' viewBox='0 0 12 12' aria-hidden='true' focusable='false'>" + path + "</svg>"
      : "";
   return "<span class='eb-status-cell'>" + icon + status + "</span>";
}

// Per-status color overrides read from config.properties (RegisteredColor..
// PostponedHiColor). These stay readable for an older file that still sets
// them (SPEC.md D-13); a new config does not, and the theme default in
// eb-ui.css applies with no override at all.
var status2Override = {};
var status2SelectedOverride = {};

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
// defaults kept). Grids wait on this before painting status colors.
var SCHEDULER_configReady = fetch("/config-autofill?Name=DEFAULT&fmt=json")
   .then(function (r) { return r.text(); })
   .then(function (text) {
      SCHEDULER_Config = ebParseLooseJSON(text);
      if (SCHEDULER_Config) {
         SCHEDULER_refreshTime = parseInt(SCHEDULER_Config.RefreshTimeSecs, 10) || SCHEDULER_refreshTime;
         SCHEDULER_ConveneRedTime = parseInt(SCHEDULER_Config.ConveneRedMins, 10) || SCHEDULER_ConveneRedTime;
         SCHEDULER_ProjectYellowTime = parseInt(SCHEDULER_Config.ProjectYellowMins, 10) || SCHEDULER_ProjectYellowTime;
         SCHEDULER_ProjectRedTime = parseInt(SCHEDULER_Config.ProjectRedMins, 10) || SCHEDULER_ProjectRedTime;
         SCHEDULER_FinalYellowTime = parseInt(SCHEDULER_Config.FinalYellowMins, 10) || SCHEDULER_FinalYellowTime;
         SCHEDULER_FinalRedTime = parseInt(SCHEDULER_Config.FinalRedMins, 10) || SCHEDULER_FinalRedTime;

         status2Override["Registered"] = SCHEDULER_Config.RegisteredColor;
         status2Override["Verified"] = SCHEDULER_Config.VerifiedColor;
         status2Override["Seated"] = SCHEDULER_Config.SeatedColor;
         status2Override["InProgress"] = SCHEDULER_Config.InProgressColor;
         status2Override["Completed"] = SCHEDULER_Config.CompletedColor;
         status2Override["Postponed"] = SCHEDULER_Config.PostponedColor;

         status2SelectedOverride["Registered"] = SCHEDULER_Config.RegisteredHiColor;
         status2SelectedOverride["Verified"] = SCHEDULER_Config.VerifiedHiColor;
         status2SelectedOverride["Seated"] = SCHEDULER_Config.SeatedHiColor;
         status2SelectedOverride["InProgress"] = SCHEDULER_Config.InProgressHiColor;
         status2SelectedOverride["Completed"] = SCHEDULER_Config.CompletedHiColor;
         status2SelectedOverride["Postponed"] = SCHEDULER_Config.PostponedHiColor;
      }
      console.log(SCHEDULER_Config);
      return SCHEDULER_Config;
   })
   .catch(function (e) {
      console.log("config load failed, using defaults: " + e);
      return null;
   });

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

// Tabulator column sorter for the Status column.
function sort_status(a, b) {
   return sort2num(a) - sort2num(b);
}

// Tabulator column sorter for the RegNum ("#") column.
//
// RegNum is assigned at sign-in by RegisterScoutHandler: "P" plus a counter
// when the youth matches a pre-registration record (by ID, or by email), "W"
// plus a counter when nothing matches -- a walk-in. Each counter increments
// in sign-in order, so ordering by prefix then number keeps everyone in the
// order they arrived WITHIN their group, while holding walk-ins below the
// pre-registered queue where their lower priority is visible.
//
// The column previously declared sorter:"number", which cannot work on these
// values -- parseFloat("W1") is NaN, so clicking the header produced an
// arbitrary order.
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

// Applies the status class (D-13, D-16: see eb-ui.css) to a Tabulator row
// element, plus any config.properties color override for an older file
// (set as custom properties the class's CSS reads with a var() fallback --
// see SCHEDULER_statusIconHtml's comment above status2Override).
function SCHEDULER_styleRowByStatus(row, selected) {
   var el = row.getElement();
   var status = row.getData().Status;

   for (var key in status2Class) {
      el.classList.remove(status2Class[key]);
   }
   if (status2Class[status]) {
      el.classList.add(status2Class[status]);
   }
   el.classList.toggle("eb-row-selected", !!selected);

   var override = status2Override[status];
   var selectedOverride = status2SelectedOverride[status];
   if (override) {
      el.style.setProperty("--eb-status-override", override);
   } else {
      el.style.removeProperty("--eb-status-override");
   }
   if (selectedOverride) {
      el.style.setProperty("--eb-status-override-selected", selectedOverride);
   } else {
      el.style.removeProperty("--eb-status-override-selected");
   }
}

// ------------------------------------------------------------------------
// Dialog / toast / modal helpers (vanilla toast, confirm and
// modal-form dialog helpers).
// ------------------------------------------------------------------------

function ebToastContainer() {
   var c = document.getElementById("eb-toasts");
   if (!c) {
      c = document.createElement("div");
      c.id = "eb-toasts";
      document.body.appendChild(c);
   }
   return c;
}

// kind: "error" | "ok" | "warn" | "info"
function ebToast(title, html, kind, expireMs) {
   var t = document.createElement("div");
   t.className = "eb-toast eb-toast-" + (kind || "info");
   t.innerHTML = "<div class='eb-toast-title'>" + title + "</div><div class='eb-toast-body'>" + html + "</div>";
   t.addEventListener("click", function () {
      if (t.parentNode) { t.parentNode.removeChild(t); }
   });
   ebToastContainer().appendChild(t);
   if (expireMs !== -1) {
      setTimeout(function () {
         if (t.parentNode) { t.parentNode.removeChild(t); }
      }, expireMs || 6000);
   }
   return t;
}

function ebAlert(title, html) {
   ebToast(title, html, "error", 8000);
}

function ebMessage(title, html) {
   ebToast(title, html, "ok", 6000);
}

function ebModalOverlay() {
   var ov = document.createElement("div");
   ov.className = "eb-modal-overlay";
   return ov;
}

// Confirm dialog. callback(true|false).
function ebConfirm(title, html, callback) {
   var ov = ebModalOverlay();
   var box = document.createElement("div");
   box.className = "eb-modal";
   box.innerHTML = "<div class='eb-modal-title'>" + title + "</div>"
      + "<div class='eb-modal-body'>" + html + "</div>"
      + "<div class='eb-modal-buttons'>"
      + "<button class='eb-ok'>OK</button>"
      + "<button class='eb-cancel'>Cancel</button>"
      + "</div>";
   ov.appendChild(box);
   document.body.appendChild(ov);
   function done(result) {
      document.body.removeChild(ov);
      if (callback) { callback(result); }
   }
   box.querySelector(".eb-ok").addEventListener("click", function () { done(true); });
   box.querySelector(".eb-cancel").addEventListener("click", function () { done(false); });
   box.querySelector(".eb-ok").focus();
}

// Modal with arbitrary body HTML and named buttons.
// buttons: [{name, label}], callback(name, bodyElement) — return false from
// callback to keep the dialog open (for validation).
function ebModalForm(title, bodyHtml, buttons, callback) {
   var ov = ebModalOverlay();
   var box = document.createElement("div");
   box.className = "eb-modal";
   var btnHtml = "";
   for (var i = 0; i < buttons.length; i++) {
      btnHtml += "<button data-name='" + buttons[i].name + "'>" + buttons[i].label + "</button>";
   }
   box.innerHTML = "<div class='eb-modal-title'>" + title + "</div>"
      + "<div class='eb-modal-body'></div>"
      + "<div class='eb-modal-buttons'>" + btnHtml + "</div>";
   var body = box.querySelector(".eb-modal-body");
   body.innerHTML = bodyHtml;
   ov.appendChild(box);
   document.body.appendChild(ov);
   var btns = box.querySelectorAll(".eb-modal-buttons button");
   for (var b = 0; b < btns.length; b++) {
      (function (btn) {
         btn.addEventListener("click", function () {
            var keep = callback && callback(btn.getAttribute("data-name"), body);
            if (keep !== false) {
               document.body.removeChild(ov);
            }
         });
      })(btns[b]);
   }
   var first = body.querySelector("input,select,textarea");
   if (first) { first.focus(); }
   return { close: function () { if (ov.parentNode) { document.body.removeChild(ov); } }, body: body };
}
