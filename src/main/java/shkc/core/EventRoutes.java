package shkc.core;

/**
 * The list of every URL the scheduler answers and which class answers it.
 *
 * <p>To add an endpoint, write a handler class (see {@link BoardHandler},
 * {@link RecordCellsHandler} and {@link RecordUpdateHandler}) and add one line
 * here. The static pages (the Event page, Admin, Settings, Help, and the
 * check-in pages) are not listed: {@link WebServer} serves those from
 * {@code WEBROOT} when no handler matches.
 */
final class EventRoutes {
   private EventRoutes() {
   }

   /** Adds every handler to the server. */
   static void register(WebServer server, EventData data) {
      // Sign-in, as used by the tablets.
      server.addHandler("/register-youth",             new RegisterScoutHandler(data));
      server.addHandler("/register-adult",             new RegisterAdultHandler(data));

      // The tables on the Admin page: read (cells) and edit (update).
      server.addHandler("/update-config",              new UpdateConfigHandler(data));
      server.addHandler("/youth-cells",                new ScoutCellsHandler(data));
      server.addHandler("/youth-scheduled-cells",      new ScoutsScheduledCellsHandler(data));
      server.addHandler("/adult-cells",                new AdultCellsHandler(data));
      server.addHandler("/adult-history-cells",        new AdultHistoryCellsHandler(data));
      server.addHandler("/room-cells",                 new RoomCellsHandler(data));
      server.addHandler("/approved-proposals-cells",   new ApprovedProposalsCellsHandler(data));
      server.addHandler("/youth-update",               new ScoutUpdateHandler(data));
      server.addHandler("/youth-scheduled-update",     new ScoutsScheduledUpdateHandler(data));
      server.addHandler("/adult-update",               new AdultUpdateHandler(data));
      server.addHandler("/adult-history-update",       new AdultHistoryUpdateHandler(data));
      server.addHandler("/room-update",                new RoomUpdateHandler(data));

      // The Event page: a board's steps, in order, and the corrections.
      server.addHandler("/room-change",                new RoomChangeHandler(data));
      server.addHandler("/seat-board",                 new SeatBoardHandler(data));
      server.addHandler("/inprogress-board",           new InProgressBoardHandler(data));
      server.addHandler("/complete-board",             new CompleteBoardHandler(data));
      server.addHandler("/postpone-board",             new PostponeBoardHandler(data));
      server.addHandler("/reset-board",                new ResetBoardHandler(data));
      server.addHandler("/restore-board",              new RestoreBoardHandler(data));
      server.addHandler("/change-board-members",       new ChangeBoardMembersHandler(data));
      server.addHandler("/rename-room",                new RenameRoomHandler(data));

      // The shared check-in pages' calls (eagleboards-shared/checkin).
      new CheckInApi(data.getLock(), data.getScouts(), data.getScoutsScheduled(), data.getAdults(),
         data.getAdultHistory(), data.getConfig()).register(server);
      server.addHandler("/checkin-address",            new CheckInAddress.AddressHandler());
      server.addHandler("/checkin-qr",                 new CheckInAddress.QrHandler());

      // Every POST above may change what a page shows; /events tells the
      // pages that are listening (SPEC.md D-15, nothing polls).
      ChangeFeed changeFeed = new ChangeFeed();
      server.setChangeFeed(changeFeed);
      server.addHandler("/events",                     changeFeed);

      // The old email autofill finds one address it's given, but no longer
      // lists them all: its list mode gave any device on the venue network
      // every email in the adult history and the pre-registrations. The
      // sign-in pages look one address up at a time (/api/*-lookup); the
      // settings' names are no one's personal information.
      server.addHandler("/adult-autofill",             new AutoFillHandler<>(data, data.getAdultHistory(), "Email", false));
      server.addHandler("/youth-autofill",             new AutoFillHandler<>(data, data.getScoutsScheduled(), "Email", false));
      server.addHandler("/config-autofill",            new AutoFillHandler<>(data, data.getConfig(), "Name", true));
   }
}
