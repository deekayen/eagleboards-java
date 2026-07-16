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

// Board-type room-card warning thresholds, in minutes since a board was
// seated. Yellow = warning, Red = overdue. Defaults below; override in
// config.properties with keys ProjectYellowMins / ProjectRedMins /
// FinalYellowMins / FinalRedMins.
var SCHEDULER_ProjectYellowTime = 25;
var SCHEDULER_ProjectRedTime = 40;
var SCHEDULER_FinalYellowTime = 40;
var SCHEDULER_FinalRedTime = 50;

var SCHEDULER_Config = null;

var status2StyleMap = {};
var status2SelectedStyleMap = {};

function SCHEDULER_setDefaultColors() {
   status2StyleMap["Registered"] = "#ffcccc";
   status2StyleMap["Verified"] = "#ffffcc";
   status2StyleMap["Seated"] = "#ccffff";
   status2StyleMap["InProgress"] = "#ccffcc";
   status2StyleMap["Completed"] = "#ffffff";
   status2StyleMap["Postponed"] = "#909090";

   status2SelectedStyleMap["Registered"] = "#ff6666";
   status2SelectedStyleMap["Verified"] = "#ffff66";
   status2SelectedStyleMap["Seated"] = "#66ffff";
   status2SelectedStyleMap["InProgress"] = "#66ff66";
   status2SelectedStyleMap["Completed"] = "#eeeeee";
   status2SelectedStyleMap["Postponed"] = "#9f7f7f";
}

SCHEDULER_setDefaultColors();

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
         SCHEDULER_ProjectYellowTime = parseInt(SCHEDULER_Config.ProjectYellowMins, 10) || SCHEDULER_ProjectYellowTime;
         SCHEDULER_ProjectRedTime = parseInt(SCHEDULER_Config.ProjectRedMins, 10) || SCHEDULER_ProjectRedTime;
         SCHEDULER_FinalYellowTime = parseInt(SCHEDULER_Config.FinalYellowMins, 10) || SCHEDULER_FinalYellowTime;
         SCHEDULER_FinalRedTime = parseInt(SCHEDULER_Config.FinalRedMins, 10) || SCHEDULER_FinalRedTime;

         status2StyleMap["Registered"] = SCHEDULER_Config.RegisteredColor || status2StyleMap["Registered"];
         status2StyleMap["Verified"] = SCHEDULER_Config.VerifiedColor || status2StyleMap["Verified"];
         status2StyleMap["Seated"] = SCHEDULER_Config.SeatedColor || status2StyleMap["Seated"];
         status2StyleMap["InProgress"] = SCHEDULER_Config.InProgressColor || status2StyleMap["InProgress"];
         status2StyleMap["Completed"] = SCHEDULER_Config.CompletedColor || status2StyleMap["Completed"];
         status2StyleMap["Postponed"] = SCHEDULER_Config.PostponedColor || status2StyleMap["Postponed"];

         status2SelectedStyleMap["Registered"] = SCHEDULER_Config.RegisteredHiColor || status2SelectedStyleMap["Registered"];
         status2SelectedStyleMap["Verified"] = SCHEDULER_Config.VerifiedHiColor || status2SelectedStyleMap["Verified"];
         status2SelectedStyleMap["Seated"] = SCHEDULER_Config.SeatedHiColor || status2SelectedStyleMap["Seated"];
         status2SelectedStyleMap["InProgress"] = SCHEDULER_Config.InProgressHiColor || status2SelectedStyleMap["InProgress"];
         status2SelectedStyleMap["Completed"] = SCHEDULER_Config.CompletedHiColor || status2SelectedStyleMap["Completed"];
         status2SelectedStyleMap["Postponed"] = SCHEDULER_Config.PostponedHiColor || status2SelectedStyleMap["Postponed"];
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

// Returns {background, selected} styling info for a status.
function status2style(status, selected) {
   if (selected) {
      return status2SelectedStyleMap[status];
   }
   return status2StyleMap[status];
}

// Applies status coloring to a Tabulator row element.
function SCHEDULER_styleRowByStatus(row, selected) {
   var el = row.getElement();
   var status = row.getData().Status;
   var bg = status2style(status, selected);
   if (bg) {
      el.style.backgroundColor = bg;
   } else {
      el.style.backgroundColor = "";
   }
   el.style.textDecoration = selected ? "underline" : "";
   el.style.color = "#000000";
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
