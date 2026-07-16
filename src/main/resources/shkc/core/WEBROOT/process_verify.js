// ------------------------------------------------------------------------
// process_verify.js — Registered -> Verified transition (/verify-board).
// ------------------------------------------------------------------------

function InitializeVerifyBoard() {
   // dialogs are created on demand now; kept for scheduler.html parity
}

// The three items a scout's paperwork must show before they can be verified.
// Gate only: all boxes must be ticked to enable Verify, but nothing about the
// checks is stored — the server just records the scout as Verified.
var VERIFY_CHECKS = [
   { name: "beneficiary", label: "Beneficiary signature" },
   { name: "unitleader", label: "Unit leader signature" },
   { name: "lifepurpose", label: "Life purpose statement" }
];

function ProcessVerifyBoard(s_id) {
   var s_status = schedulerScoutGrid.getColumnValue(s_id, "Status");
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");

   if (s_status != "Registered") {
      ebAlert("Verify Error",
         "Invalid Status: '" + s_status + "'<br/>Expected: 'Registered'");
      return;
   }

   var rows = "<p>Confirm the scout's paperwork before verifying:</p>";
   for (var i = 0; i < VERIFY_CHECKS.length; i++) {
      rows += "<label><input type='checkbox' name='" + VERIFY_CHECKS[i].name
         + "'/> " + VERIFY_CHECKS[i].label + "</label><br/>";
   }

   var modal = ebModalForm("Verify Board: " + s_first + " " + s_last, rows,
      [{ name: "Verify", label: "Verify" }, { name: "Cancel", label: "Cancel" }],
      function (name) {
         if (name == "Verify") {
            SendVerifyRequest(s_id);
         }
      });

   // Keep the Verify button disabled until every box is checked.
   var verifyBtn = modal.body.parentNode.querySelector(
      ".eb-modal-buttons button[data-name='Verify']");
   var boxes = modal.body.querySelectorAll("input[type='checkbox']");
   function updateGate() {
      var allChecked = true;
      for (var i = 0; i < boxes.length; i++) {
         if (!boxes[i].checked) { allChecked = false; break; }
      }
      verifyBtn.disabled = !allChecked;
   }
   for (var b = 0; b < boxes.length; b++) {
      boxes[b].addEventListener("change", updateGate);
   }
   updateGate();
}

function SendVerifyRequest(s_id) {
   var s_last = schedulerScoutGrid.getColumnValue(s_id, "Last");
   var s_first = schedulerScoutGrid.getColumnValue(s_id, "First");

   ebAction("/verify-board", { ScoutID: s_id })
      .then(function (res) {
         if (res.ok) {
            ebMessage("Verified", s_first + " " + s_last + " Verified OK.");
         } else {
            ebAlert("Verify Error",
               s_first + " " + s_last + " Verify Failed.<br/> " + res.text);
         }
      })
      .catch(function () {
         ebAlert("Verify Error", s_first + " " + s_last + " Verify Failed.");
      })
      .then(function () {
         setTimeout(function () {
            refresh_all();
         }, 500);
      });
}
