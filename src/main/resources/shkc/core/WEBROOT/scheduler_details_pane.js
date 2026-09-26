// ------------------------------------------------------------------------
// scheduler_details_pane.js — D-10: the selected youth's board is built
// here, beside the queue -- the room, the members with the chair marked,
// the rules checked as members are added, and the free adults to add from
// (that part is schedulerLeaderGrid itself, filtered to exclude whoever's
// already picked; see scheduler_adult_grid.js).
//
// D-11: one status-driven primary action, not three always-visible
// buttons. The label and the endpoint it calls follow the youth's status
// (Registered/Verified -> Seat Board, Seated -> Start Review, InProgress
// -> Complete); Reset/Postpone/Locate stay separate, secondary actions.
//
// Picks live on schedulerLeaderGrid (getCheckedRowIds/pickRow/setPicked/
// unpickAll) rather than here, so ProcessSeatBoard in process_seat.js
// needs no change at all -- it already reads member ids through that same
// method name, which used to mean "checked in the grid" and now means
// "picked in the pane".
// ------------------------------------------------------------------------

function SchedulerDetailsPane(containerId, toolbarId, messagesId) {
   this.el = document.getElementById(containerId);
   this.toolbar = document.getElementById(toolbarId);
   this.messagesId = messagesId;
   this.scoutId = null;
   this.buttons = {};
   var names = ["Primary", "Fill", "Clear", "Locate", "Reset", "Postpone"];
   for (var i = 0; i < names.length; i++) {
      this.buttons[names[i]] = this.toolbar.querySelector("[data-action='" + names[i] + "']");
   }

   var this_obj = this;
   this.toolbar.addEventListener("click", function (ev) {
      var btn = ev.target.closest("button[data-action]");
      if (!btn || btn.disabled) {
         return;
      }
      this_obj.onAction(btn.getAttribute("data-action"));
   });

   this.render();   // shows the empty state; safe before the other grids exist
}

// The single source of truth for "which concrete step comes next" (D-11,
// O-4: never a generic label). Reused by the primary button's own label
// and by SCHEDULER_scoutForProposal-adjacent code that needs the same verb.
SchedulerDetailsPane.PRIMARY_STEP = {
   "Registered": { label: "Seat Board", run: "ProcessSeatBoard" },
   "Verified": { label: "Seat Board", run: "ProcessSeatBoard" },
   "Seated": { label: "Start Review", run: "ProcessStartReview" },
   "InProgress": { label: "Complete", run: "ProcessCompleteBoard" }
};

SchedulerDetailsPane.prototype.onAction = function (action) {
   var s_id = this.scoutId;
   if (action === "Fill") {
      this.fillTheRest();
      return;
   }
   if (action === "Clear") {
      schedulerLeaderGrid.unpickAll();
      this.render();
      return;
   }
   if (!s_id) {
      return;
   }
   if (action === "Primary") {
      var status = schedulerScoutGrid.getColumnValue(s_id, "Status");
      var step = SchedulerDetailsPane.PRIMARY_STEP[status];
      if (step) {
         window[step.run](s_id);
      }
   } else if (action === "Locate") {
      SCHEDULER_locateAdults(s_id, true);
   } else if (action === "Reset") {
      ProcessResetBoard(s_id);
   } else if (action === "Postpone") {
      ProcessPostponeBoard(s_id);
   }
};

// Called whenever the Youth grid's selection changes (SCHEDULER_selectScout
// in scheduler.html). Resets and re-proposes only when the selection is
// actually a DIFFERENT youth -- reselecting the same one (a stray refresh,
// a duplicate click) must not throw away picks the operator is mid-edit on.
SchedulerDetailsPane.prototype.onScoutSelected = function (s_id) {
   var isNew = (s_id !== this.scoutId);
   this.scoutId = s_id;

   if (isNew && s_id) {
      schedulerLeaderGrid.unpickAll();
      var status = schedulerScoutGrid.getColumnValue(s_id, "Status");
      if (status === "Seated" || status === "InProgress") {
         var room = schedulerScoutGrid.getColumnValue(s_id, "Room");
         schedulerLeaderGrid.pickForRoom(room);
      } else if (status === "Registered" || status === "Verified") {
         this.autoSelect(s_id);
      }
   }
   this.render();
};

// Same proposal auto-select already ran on youth-select before this
// rewrite (SPEC.md D-5); see proposeBoard in process_seat.js.
SchedulerDetailsPane.prototype.autoSelect = function (s_id) {
   roomView.unselectAll();
   var proposal = proposeBoard(
      SCHEDULER_scoutForProposal(s_id),
      SCHEDULER_adultsForProposal(),
      SCHEDULER_waitingForProposal(s_id));
   if (proposal.chairId) {
      schedulerLeaderGrid.pickRow(proposal.chairId);
   }
   proposal.memberIds.forEach(function (l_id) {
      schedulerLeaderGrid.pickRow(l_id);
   });
   this._proposalProblems = proposal.problems;

   var rm_found = null;
   var s_btype = schedulerScoutGrid.getColumnValue(s_id, "BoardType");
   for (var r = 0; r < roomView.items.length; r++) {
      var data = roomView.items[r];
      if ((data.Scout == "" || data.Scout == "-") && (data.BoardType == s_btype)) {
         roomView.select(data.id, true);
         rm_found = data.id;
         break;
      }
   }
   if (!rm_found) {
      this._proposalProblems = this._proposalProblems.concat(["No " + s_btype + " rooms available."]);
   }
};

// D-12, partially: proposeBoard has no notion of "keep these, fill the
// rest" (it proposes a whole board from its own ranking -- see SPEC.md
// D-5, "same algorithm ... in all three", so extending the algorithm
// itself is a cross-version change, not a Java-only one). Confirms before
// replacing anything already picked, rather than silently discarding
// manual work.
SchedulerDetailsPane.prototype.fillTheRest = function () {
   var s_id = this.scoutId;
   if (!s_id) {
      return;
   }
   var this_obj = this;
   var doFill = function () {
      schedulerLeaderGrid.unpickAll();
      this_obj.autoSelect(s_id);
      this_obj.render();
   };
   var alreadyPicked = schedulerLeaderGrid.getCheckedRowIds();
   if (alreadyPicked) {
      ebConfirm("Fill the Rest",
         "This proposes a whole board from scratch and replaces the members "
         + "already picked here, rather than adding to them.<br/><br/>Propose a new board?",
         function (result) {
            if (result) {
               doFill();
            }
         }, "Propose Board");
   } else {
      doFill();
   }
};

SchedulerDetailsPane.prototype.removeMember = function (l_id) {
   schedulerLeaderGrid.setPicked(l_id, false);
   this.render();
};

// Called by schedulerLeaderGrid.onUserSelect when a free adult is clicked.
SchedulerDetailsPane.prototype.addMember = function (l_id) {
   if (!this.scoutId) {
      ebAlert("Error", "Select a youth first.", "scout");
      return;
   }
   var status = schedulerScoutGrid.getColumnValue(this.scoutId, "Status");
   if (status !== "Registered" && status !== "Verified") {
      return;   // board already built or seated; nothing to add to
   }
   schedulerLeaderGrid.pickRow(l_id);
   this.render();
};

// Whether l_id, among this board's current picks, reads as the chair for
// display -- the same first-qualified-wins tie-break ProcessSeatBoard uses
// (process_seat.js), so the live preview never disagrees with what
// submitting would actually pick as the default. The operator can still
// choose a different qualified chair in the confirmation dialog at submit.
SchedulerDetailsPane.prototype._chairId = function (btype, memberIds) {
   for (var i = 0; i < memberIds.length; i++) {
      var role = (btype === "Project")
         ? schedulerLeaderGrid.getColumnValue(memberIds[i], "ProjectReview")
         : schedulerLeaderGrid.getColumnValue(memberIds[i], "FinalBoard");
      if (role === "Chair") {
         return memberIds[i];
      }
   }
   return null;
};

SchedulerDetailsPane.prototype.render = function () {
   var s_id = this.scoutId;
   var empty = this.el.querySelector(".eb-details-empty");
   var content = this.el.querySelector(".eb-details-content");

   if (!s_id) {
      empty.style.display = "";
      content.style.display = "none";
      this._setButtons(null);
      return;
   }
   empty.style.display = "none";
   content.style.display = "block";

   var status = schedulerScoutGrid.getColumnValue(s_id, "Status");
   var s_btype = schedulerScoutGrid.getColumnValue(s_id, "BoardType");
   var memberIdsStr = schedulerLeaderGrid.getCheckedRowIds();
   var memberIds = memberIdsStr ? memberIdsStr.split(",") : [];
   var chairId = this._chairId(s_btype, memberIds);

   this._setButtons(status);

   // Room: whichever room card is selected/targeted, or the youth's own
   // Room once seated.
   var roomLabel = "No room selected";
   if (status === "Seated" || status === "InProgress") {
      roomLabel = schedulerScoutGrid.getColumnValue(s_id, "Room") || roomLabel;
   } else {
      var rm_id = roomView.getSelected();
      if (rm_id) {
         var rm_data = roomView.get(rm_id);
         roomLabel = rm_data ? rm_data.Room : roomLabel;
      }
   }
   this.el.querySelector(".eb-details-room").textContent = "Room: " + roomLabel;

   // Members, chair marked first.
   var list = this.el.querySelector(".eb-details-members-list");
   list.innerHTML = "";
   var canEdit = (status === "Registered" || status === "Verified");
   memberIds.forEach(function (id) {
      if (!id) {
         return;
      }
      var last = schedulerLeaderGrid.getColumnValue(id, "Last");
      var first = schedulerLeaderGrid.getColumnValue(id, "First");
      var isChair = (id === chairId);
      var li = document.createElement("li");
      li.className = "eb-details-member" + (isChair ? " eb-details-chair" : "");
      var icon = isChair
         ? "<svg class='eb-status-icon' viewBox='0 0 12 12' aria-hidden='true'><path d='M6 1.5 7.4 4.6 10.8 5 8.2 7.3 8.9 10.7 6 8.9 3.1 10.7 3.8 7.3 1.2 5 4.6 4.6Z' fill='currentColor'/></svg>"
         : "";
      var removeBtn = canEdit ? "<button type='button' class='eb-details-remove' aria-label='Remove'>&times;</button>" : "";
      li.innerHTML = icon + "<span>" + ebEscapeHtml(first + " " + last) + (isChair ? " (Chair)" : "") + "</span>" + removeBtn;
      if (canEdit) {
         li.querySelector(".eb-details-remove").addEventListener("click", function () {
            detailsPane.removeMember(id);
         });
      }
      list.appendChild(li);
   });
   if (memberIds.length === 0) {
      var none = document.createElement("li");
      none.className = "eb-details-member eb-details-none";
      none.textContent = canEdit ? "No members picked yet -- click a free adult to add them." : "No members.";
      list.appendChild(none);
   }

   // Rule feedback: informational only, same pure functions the actual
   // submit path (ProcessSeatBoard) uses -- never blocks, never overrides
   // that path's own checks.
   var problems = [];
   if (canEdit) {
      var member_arr = memberIds.filter(function (id) { return !!id; }).map(function (id) {
         return { id: id, uname: schedulerLeaderGrid.getColumnValue(id, "UnitName") };
      });
      var s_uname = schedulerScoutGrid.getColumnValue(s_id, "UnitName");
      var conflicts = findUnitConflicts(s_uname, member_arr);
      if (conflicts.length > 0) {
         problems.push(conflicts.length + " member(s) share " + s_uname + " with this youth.");
      }
      var verdict = (s_btype === "Project") ? checkProjectSize(memberIds.length) : checkBoardSize(memberIds.length);
      if (verdict === "too-few") {
         problems.push("Not enough members yet.");
      } else if (verdict === "too-many") {
         problems.push("Too many members (max 6).");
      }
      if (memberIds.length > 0 && !chairId) {
         problems.push("None of the picked members is qualified to chair.");
      }
      if (this._proposalProblems && this._proposalProblems.length > 0) {
         problems = problems.concat(this._proposalProblems);
      }
   }
   var problemsEl = this.el.querySelector(".eb-details-problems");
   problemsEl.innerHTML = problems.map(function (p) { return ebEscapeHtml(p); }).join("<br/>");
   problemsEl.style.display = problems.length > 0 ? "" : "none";
};

SchedulerDetailsPane.prototype._setButtons = function (status) {
   var step = status && SchedulerDetailsPane.PRIMARY_STEP[status];
   if (this.buttons.Primary) {
      this.buttons.Primary.textContent = step ? step.label : "Seat Board";
      this.buttons.Primary.disabled = !step;
   }
   var canEdit = status === "Registered" || status === "Verified";
   if (this.buttons.Fill) {
      this.buttons.Fill.disabled = !canEdit;
   }
   if (this.buttons.Clear) {
      this.buttons.Clear.disabled = !canEdit;
   }
   if (this.buttons.Locate) {
      this.buttons.Locate.disabled = !status;
   }
   if (this.buttons.Reset) {
      this.buttons.Reset.disabled = !(status === "Seated" || status === "InProgress" || status === "Verified");
   }
   if (this.buttons.Postpone) {
      this.buttons.Postpone.disabled = !(status === "Registered" || status === "Verified");
   }
};
