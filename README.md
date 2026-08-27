# Placement Week Scheduler

## 1. Project Overview

This project is a scheduling and replanning system for a college placement
week: a multi-day event where a fixed number of companies interview a large
pool of shortlisted students across a limited set of rooms and interview
panels.

The core problem it solves has two parts. First, given realistic
constraints — CGPA cutoffs, branch restrictions, limited rooms, limited
panels, and heavily overlapping student shortlists — the system must
produce an initial interview schedule and clearly report anything it could
not fit, rather than failing silently. Second, real placement weeks are
disrupted constantly: a panel drops out, a room becomes unusable, a student
withdraws, a company arrives late. The system must repair the schedule
around each disruption with the smallest possible amount of change, and
report exactly what changed and who needs to be told.

## 2. Key Features

- **Realistic dataset generation** — companies are split into mass
  recruiters, mid-tier, and dream-company tiers with different CGPA
  cutoffs, branch restrictions, panel counts, and interview durations.
  Students are generated with a bell-curve CGPA distribution, and each
  student's shortlist count correlates with their CGPA, producing the kind
  of overlapping-shortlist pressure a real placement week has.
- **Interview scheduling** — a greedy, priority-ordered scheduler assigns
  each eligible (student, company) shortlist pair to a specific panel, room,
  and time slot, enforcing every hard constraint along the way.
- **Student/panel/room constraints** — no student, panel, or room is ever
  double-booked, and CGPA/branch eligibility is enforced both at data
  generation and, independently, inside the scheduler itself.
- **Unscheduled interview reporting** — when no valid slot exists for a
  shortlisted pair, it is recorded with a specific reason rather than
  dropped.
- **Four disruption types** — panel drop, room block, student withdrawal,
  and company delay, each handled by dedicated replanning logic.
- **Tiered replanning** — panel drop, room block, and company delay each
  attempt a repair ladder (cheapest fix first, wider search second, honest
  failure third) before giving up on an affected interview.
- **Coordinator dashboard** — a browser-based interface to generate a
  schedule, inspect it, and trigger any of the four disruptions against the
  live schedule.

## 3. Architecture / Project Structure

| Package | Purpose |
|---|---|
| `model` | Core domain entities: `Company`, `Student`, `Panel`, `Room`, `Interview`, `TimeSlot`, `TimeGrid`, `PriorityTier`. Pure data plus the eligibility/time-grid logic that everything else builds on. |
| `generator` | `DataGenerator` produces a realistic `Dataset` (companies, students, rooms) using a fixed random seed for reproducibility. |
| `scheduler` | `SchedulingEngine` produces the initial interview schedule from a `Dataset`, using `OccupancyTracker` to enforce no-double-booking. Returns a `SchedulingResult` (scheduled interviews + unscheduled entries with reasons). |
| `replan` | `ReplanEngine` handles all four disruption types, each with its own `Disruption` implementation, producing a `ReplanResult` (repair outcomes, locked count, notify list). |
| `service` | `SchedulingStateService` — the only stateful component, holding the live dataset/engine/schedule across HTTP requests. |
| `controller` | `PlacementController` — the 7 REST endpoints, each a thin delegation to the service. |
| `dto` | Request objects used by the four disruption endpoints to keep API input separate from the domain disruption classes. |

## 4. Scheduling Approach

Every candidate (student, company) pair must pass a set of hard constraints
before it is ever considered for a slot:

- The student meets the company's CGPA cutoff and branch restriction.
- The student, the panel, and the room are all simultaneously free for the
  entire duration of the interview.
- The panel is active (not dropped).

Beyond these hard constraints, the order in which candidates are attempted
is governed by a deliberate soft-priority ordering:

1. Students with **fewer total shortlists** are processed first — a
   student with only one or two shortlisted companies has far less room
   for error than a student shortlisted by ten, so they get first claim on
   scarce slots.
2. **Longer interview durations** are processed before shorter ones, so
   short interviews can fill the gaps long ones leave behind rather than
   fragmenting the day.
3. **Earlier company day** is preferred among remaining ties.
4. **Company tier** (mass recruiter, then mid-tier, then dream company) is
   used as a further tie-break.
5. A final deterministic tie-break on student ID and company ID ensures the
   same input always produces the same schedule.

For each candidate, the scheduler searches every active panel and every
room, at every valid start time on the company's assigned day, and books
the first combination that satisfies every hard constraint. If none exists,
the pair is recorded as unscheduled with a specific reason.

## 5. Replanning Approach

Every disruption is handled using the same underlying model:

- **LOCKED** — interviews unrelated to the disruption. These are never
  touched, never re-examined, and never appear in the diff.
- **AFFECTED** — interviews that can no longer proceed as scheduled because
  the resource they depend on is gone or changed.

For panel drop, room block, and company delay, each affected interview goes
through a repair ladder:

1. **Tier 1** — the smallest possible fix: keep everything else about the
   interview identical and change only the one thing that broke (the
   panel, the room, or the start time, depending on the disruption).
2. **Tier 2** — a wider search across other times (and, where applicable,
   other panels/rooms), ranked by proximity to the original slot rather
   than picked arbitrarily.
3. **Tier 3** — no valid repair exists. The interview is left unscheduled
   with a specific reason, never silently dropped.

**Student withdrawal is handled differently on purpose.** It is not a
repair problem — there is no alternative slot to search for, because the
student is not looking for one. Every affected interview is recorded with a
distinct `CANCELLED_WITHDRAWN` outcome rather than being run through the
repair ladder or reported as a failed Tier 3 search, since those are
semantically different situations for a coordinator reading the diff.

**Company delay respects a hard floor.** No repaired interview for a
delayed company is ever placed before the delay threshold, regardless of
how favorably an earlier slot might otherwise rank.

**No-cascade principle:** a repair may only be placed into a slot that is
already free. The system never displaces one already-scheduled interview
to make room for another. If a repair would require bumping someone else,
it is not attempted — the affected interview falls through to the next
tier, or ultimately to Tier 3, instead.

## 6. REST API

| Method | Endpoint | Purpose | Request Body | Response |
|---|---|---|---|---|
| POST | `/api/schedule/generate` | Generates a new dataset and runs the initial scheduling pass, resetting all live state. | none | The full `SchedulingResult` (scheduled interviews + unscheduled entries). |
| GET | `/api/schedule/interviews` | Returns the current live schedule. | none | List of `Interview`. |
| GET | `/api/schedule/unscheduled` | Returns the interviews that could not be scheduled initially. | none | List of `UnscheduledEntry`. |
| POST | `/api/replan/panel-drop` | Triggers a panel-drop disruption against the live schedule. | `{ "panelId": string, "day": int, "unit": int }` | The `ReplanResult` diff. |
| POST | `/api/replan/room-block` | Triggers a room-block disruption. | `{ "roomId": string, "day": int, "unit": int }` | The `ReplanResult` diff. |
| POST | `/api/replan/student-withdrawal` | Triggers a student withdrawing for the rest of the event. | `{ "studentId": string, "day": int, "unit": int }` | The `ReplanResult` diff. |
| POST | `/api/replan/company-delay` | Triggers a company arriving late. | `{ "companyId": string, "delayUntilUnit": int }` | The `ReplanResult` diff. |

Each replan endpoint operates on the same live schedule produced by the
most recent `/api/schedule/generate` call, and its effects persist for
every subsequent request — disruptions accumulate rather than resetting.

## 7. Dashboard

A single-page dashboard (`static/index.html`) lets a coordinator:

- Generate a new schedule and see summary counts (scheduled vs.
  unscheduled).
- Browse the full scheduled-interview list and the unscheduled list with
  reasons.
- Trigger any of the four disruption types through a dedicated form.
- View the resulting diff after each disruption: which interviews were
  repaired (and at which tier), which were cancelled or left unscheduled
  and why, how many interviews were left untouched, and which students
  need to be notified.

## 8. How to Run

Requirements: Java 21, Maven (or an IDE with Maven support, such as
Eclipse).

From the project root:

```
mvn spring-boot:run
```

Or, in Eclipse: right-click `PlacementSchedulerApplication.java` → Run As →
Spring Boot App.

Once running, open:

```
http://localhost:8080
```

The dashboard is served automatically as the default static page.

## 9. Verification

The scheduling and replanning logic was verified using a set of dedicated
Java demo runners (one per component: data generation, scheduling, and one
per disruption type), each executed directly and inspected against
expected outcomes before the REST layer was built on top of them. The REST
API and dashboard were then verified by exercising all seven endpoints
directly through the browser interface and confirming the results matched
the demo runners' output.

With the fixed data-generation seed used throughout development, the
scheduler consistently produces **707 scheduled interviews and 3,241
unscheduled interviews** out of roughly 4,000 total shortlisted pairs. This
reflects a genuine capacity constraint rather than a scheduling defect: the
event's shared pool of 20 rooms cannot physically accommodate the full
volume of demand, particularly on the first day, when the majority of
mass-recruiter interviews are concentrated.

This same constraint shows up during replanning. A company delay applied
to a heavily oversubscribed first-day company produced a full set of Tier 3
(unscheduled) outcomes, while the identical, unmodified replanning logic
applied to a lower-demand fourth-day company produced successful Tier 1
repairs for every affected interview. Both outcomes were observed during
testing. The difference is explained by the available capacity on the
respective days: the heavily loaded Day 1 scenario had no feasible
replacement, while the lower-demand Day 4 scenario had enough free capacity
for Tier 1 repairs.

No automated test suite is included; verification was performed through
the demo runners and manual exercise of the dashboard as described above.

## 10. Design Decisions

**Why state is held in `SchedulingStateService`.** HTTP requests are
stateless by nature, but the scheduling and replanning engines depend on
live, mutating occupancy state that must persist between one disruption and
the next. A single shared service instance holds the current dataset,
scheduling engine, and replan engine, ensuring that a sequence of replan
requests behaves the same way a sequence of operations within one program
run would.

**Why withdrawal uses `CANCELLED_WITHDRAWN` instead of a failed repair.** A
withdrawn student is not an interview the system failed to place — it is
an interview that no longer needs to happen. Reporting it the same way as
a failed search would misrepresent what actually occurred to anyone reading
the diff.

**Why freed slots are not automatically backfilled.** When an interview is
cancelled or moved, the vacated slot is released but never proactively
filled with some other unscheduled student. Doing so would mean silently
re-solving part of the schedule beyond the original disruption, which
conflicts with the goal of minimal, explainable disturbance.

**Why Tier 2 company-delay proximity is measured from the original
interview time.** The delay threshold defines the earliest a replacement
slot is allowed to start, but it does not change what the student
originally expected. Ranking candidate slots by closeness to the original
time — while strictly excluding anything before the delay floor — keeps
the repair as close as possible to the student's original plan within
what the delay actually permits.
