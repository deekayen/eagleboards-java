# Code of Conduct

This project exists to run Eagle Scout boards of review. Everyone who takes part
in it — writing code, filing issues, reviewing changes, or operating the app at
an event — is already held to a standard, and this project does not invent a
second one. The standard is Scouting America's.

## The standard

**The Scout Oath**

> On my honor I will do my best to do my duty to God and my country and to obey
> the Scout Law; to help other people at all times; to keep myself physically
> strong, mentally awake, and morally straight.

**The Scout Law**

> A Scout is Trustworthy, Loyal, Helpful, Friendly, Courteous, Kind, Obedient,
> Cheerful, Thrifty, Brave, Clean, and Reverent.

Adults registered with Scouting America agree to abide by the Scout Oath and
Law, to comply with Youth Protection policies, and to follow the *Guide to Safe
Scouting*. Those obligations do not stop at the door of a code repository. If
you would not say it in front of a troop, do not put it in an issue.

## What that looks like here

Four points of the Law carry most of the weight in a project like this one.

- **Trustworthy.** Say what a change actually does. If a test is failing, report
  it failing. The person relying on this software is a volunteer running a board
  of review on a weeknight, and they cannot read Java to check your work — the
  test suite exists precisely because trust has to be mechanical here.
- **Helpful.** Explain a rejected idea rather than dismissing it. Leave the
  codebase, and the person you were working with, better than you found them.
- **Courteous** and **Kind.** Disagree with the design, not the designer.
  Contributors range from professional developers to volunteers who have never
  opened a terminal, and both are welcome.
- **Clean.** Keep language and content fit for a Scouting audience.

Harassment, insults, personal or political attacks, sexualized language or
imagery, and publishing someone's private information are incompatible with the
Oath and Law and are not tolerated in any project space. This applies regardless
of age, disability, ethnicity, gender, national origin, race, religion, or any
other personal characteristic.

## Youth protection

**This is the part that matters most.** The application handles records that
describe minors: names, units, contact details, and the outcome of their boards
of review.

- **Youth Protection policies apply in every project space**, not only at
  events. Adults working on this project in any Scouting-facing capacity are
  expected to hold current Youth Protection training.
- **Barriers to abuse apply to digital contact too.** No one-on-one contact
  between an adult and a youth, in person or online. Communication with a youth
  participant includes a parent or another registered adult.
- **Reporting suspected abuse is mandatory and immediate.** Report to local law
  enforcement and to the local council Scout executive. Do not open a GitHub
  issue, do not investigate it yourself, and do not wait for a maintainer.

## Participant data

Never put real participant data in this repository or anywhere connected to it.
That means no names, contact details, unit affiliations, or board results in a
commit, an issue, a screenshot, or a log excerpt. Use synthetic data in every
example, and read [SECURITY.md](SECURITY.md) before you attach anything.

`.gitignore` and `scripts/hooks/pre-commit` guard against the common mistakes,
but they are a backstop, not a substitute for looking at what you are about to
publish.

## Reporting a concern

**Youth safety concerns do not come here.** Contact local law enforcement and
your council Scout executive, immediately and directly.

For conduct within the project — harassment, hostile behavior, or anything in
this document — contact the maintainer directly rather than raising it in
public. Reports are handled discreetly, and the privacy of whoever reports is
respected.

## Enforcement

Maintainers clarify these standards and respond to behavior that falls short of
them. They may edit, remove, or reject comments, commits, issues, and other
contributions, and may bar someone from the project. Conduct that violates
Scouting America's membership standards is referred to the council, which is the
body that can actually act on it.

## Basis

Built on the Scout Oath and Scout Law, Scouting America's Youth Protection
policies, and the *Guide to Safe Scouting*, which are the governing documents
for everyone this software serves. Where this file and Scouting America policy
disagree, Scouting America policy controls.
