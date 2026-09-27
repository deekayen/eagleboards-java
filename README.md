# Eagle Board Scheduler

This program runs the check-in desk and the room assignments on a board of
review night. Scouts and adults sign themselves in on a laptop or tablet at the
door. You sit at the admin computer, put each scout with a board and a room, and
record the result when they come out.

It runs on one computer at the event. It does not need the internet, only a
local network that the check-in station can reach.

**If you are here to run an event night, this page is the whole manual.** The
technical material lives in [RUNNING.md](RUNNING.md).

## Before the first night

You need three things.

1. **A computer to run it on.** A laptop is fine. A Raspberry Pi is fine. It
   needs Java installed; see [RUNNING.md](RUNNING.md) if it is not set up yet.
2. **A second screen for the door.** A laptop or tablet with a web browser,
   on the same network as the first computer. This is the check-in station.
3. **Your room list.** Which rooms you have, and whether each one is for
   project proposal reviews or for final boards.

## Starting it up

Open the Eagle Board Scheduler shortcut on the desktop.

Java may ask whether to allow access on local or public networks. Click
**Allow**. If you say no, the check-in station will not be able to reach it.

Two windows open:

- a **black window** full of text. Leave it alone. Closing it stops the program.
- a small **grey window** showing a web address, something like
  `http://192.168.1.50:8080`.

That address is what you type into the check-in station's browser.

On the admin computer, use the same address with a word on the end:

| What you want | Address to type |
| --- | --- |
| The check-in screen | the address by itself |
| **The scheduler** (where you will spend the night) | the address + `/scheduler` |
| The record lists | the address + `/admin` |
| Colors and timers | the address + `/configure` |
| Built-in help | the address + `/help` |

## Set up your rooms first

Do this before anyone arrives. The program cannot seat a board without a room.

1. Go to `/admin` on the admin computer.
2. Click the **Rooms** tab.
3. Add each room. Mark it **Project** or **Final**.

Mark rooms by what you will use them for that night, not by what they are
called. The program uses that mark to suggest the right room later.

If you run more than one project review in the same physical room, add it more
than once with different names, like `200A` and `200B`. Each one can hold a
board.

## The night, step by step

### 1. People sign in

At the check-in station, a scout taps **I am a Youth** and an adult taps
**I am 21+**. They fill in their name, contact details, unit, and (for scouts)
whether they are there for a project proposal review or a final board. Adults
say which kind of board they are willing to sit on.

Adults who have served before are recognized once they enter their email, and
the rest of the form fills itself in.

The lists on the screens update themselves about every half minute. You do not
need to press anything to see new arrivals.

### 2. Check the paperwork

This happens away from the computer. Look over the scout's application,
references, and project workbook while they wait.

If something is missing and cannot be fixed tonight, select the scout on the
scheduler and press **Postpone**. They can come back next month.

### 3. Seat the board

On the scheduler, click the scout's name. The program picks a chair, the right
number of members, and a room, and ticks them in the lists.

You can change any of it. Tick and untick adults in the adult list, and click a
different room, until it is the board you want.

Press **Seat Board**. A small window asks you to confirm who is chairing.
Confirm it, and the board members go to the room with the paperwork.

**The scout does not go in yet.** The members read the application, the
references, and the project workbook first. The scout waits outside.

The program will stop you or ask a question in these cases:

| What it sees | What happens |
| --- | --- |
| Fewer than 3 members on a final board (2 on a project review) | Refused. Add more adults. |
| More members than needed | Asks you to confirm. This is fine. |
| More than 6 members on a final board | Refused. National rules cap a board at six. |
| Adults from the scout's own unit | Warns you and names them. You may override it. |
| **Every** member from the scout's own unit | Refused. At least one member must come from outside the unit, and there is no override. |
| A final board put in a project room, or the reverse | Asks you to confirm. |

### 4. Start the review

When the members have finished reading and are ready for the candidate, select
the scout and press **Start Review**.

The program tells you where to find the scout's Scoutmaster or Life to Eagle
coach, who walks them in and introduces them. The **Locate** button looks them
up again if you missed the message.

### 5. Record the result

When the board comes out, select the scout and press **Complete**. Choose
**Approved**, **Adjourned**, or **NotApproved**, and add any notes.

That frees the room and the adults for the next scout.

## Reading the screen

Each scout's row is colored by where they are:

| Color | Meaning |
| --- | --- |
| Pink | Signed in, waiting |
| Pale yellow | Paperwork checked |
| Pale blue | Board is in the room reading |
| Pale green | Scout is in with the board |
| White | Finished |
| Grey | Postponed |

Room cards change color when a board is taking a long time. They are a nudge to
go check, not an alarm, and nothing stops a board that needs longer.

| Stage | Turns yellow | Turns red |
| --- | --- | --- |
| Board reading the paperwork | — | 30 minutes |
| Final board with the scout | 30 minutes | 45 minutes |
| Project review with the scout | 25 minutes | 40 minutes |

You can change these on the `/configure` screen.

## When something goes wrong

**A scout is not in the list.** They have not signed in yet, or they signed in
as an adult by mistake. Check the `/admin` lists.

**"Not enough adults" or no room offered.** Every room of that type is busy, or
too few adults have signed in and marked themselves available for that kind of
board. Wait for a board to finish, or add a room.

**You seated the wrong board.** Select the scout and press **Reset**. That puts
them back to waiting and frees the adults and the room. It works whether the
board is still reading or already interviewing.

**You need to postpone after seating.** Press **Reset** first, then
**Postpone**.

**The check-in station cannot reach the address.** Both machines must be on the
same network. Check the address in the grey window, and check that you clicked
Allow when Java asked about network access.

**The black window closed.** The program stopped. Start it again from the
shortcut. Work already recorded is saved.

## Where the information goes

Everything is written to files on the computer running the program, in a folder
named for tonight's date. Nothing is sent anywhere.

Those files hold the names and emails of adults **and minors**, and the
adults' phone numbers. The program no longer asks a youth for a birthdate or a
phone number, but files from older versions may still hold them. Treat these
files the way you would treat a paper roster: keep them on that machine, do not
email them, and do not put them anywhere shared.

## Support this project

The scheduler is free, and built and kept up by a volunteer. If it helps your
district's board events, you can chip in:
[GitHub Sponsors](https://github.com/sponsors/deekayen) ·
[Ko-fi](https://ko-fi.com/deekayen) ·
[Liberapay](https://liberapay.com/deekayen) ·
[PayPal](https://paypal.me/deekayen) ·
[Venmo](https://venmo.com/drdnorman) ·
[Buy Me a Coffee](https://buymeacoff.ee/deekayen).
The same links are on the scheduler's Settings page.

## For whoever set this up

| Document | What it covers |
| --- | --- |
| [RUNNING.md](RUNNING.md) | Installing, building, command-line options, settings file, known quirks |
| [ARCHITECTURE.md](ARCHITECTURE.md) | How the program is put together |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Making changes, and how they are verified |
| [SECURITY.md](SECURITY.md) | Handling participant data and the API key |
| [PROVENANCE.md](PROVENANCE.md) | Where this code came from |
| [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | Conduct, on the Scout Oath and Law, and youth protection |

## License

Apache-2.0 (see [LICENSE](LICENSE) and [NOTICE](NOTICE)). The project was
previously GPL-2.0, which the bundled dhtmlxSuite UI library required; that
library has been replaced by MIT-licensed Tabulator, so nothing compels
copyleft any more.

## Trademarks

Not affiliated with, endorsed by, or sponsored by Scouting America (Boy Scouts
of America). "Scouts BSA", "Eagle Scout" and related marks belong to their
owner and are used here only to describe what the software is for. The
interface follows published brand guidance for visual consistency; that is not
a claim of any trademark right, and Apache-2.0 §6 grants none.
