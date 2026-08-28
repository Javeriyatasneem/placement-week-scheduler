# Placement Week Scheduler

## 1. Project Overview

This project is a scheduling and replanning system for a college placement
week: a multi-day event where companies interview a large pool of shortlisted
students across a limited set of rooms and interview panels.

The system solves two related problems. First, it creates an initial interview
schedule while respecting constraints such as CGPA cutoffs, branch
restrictions, room availability, panel availability, interview duration, and
student availability. Any shortlisted interviews that cannot be placed are
reported instead of being silently dropped.

Second, it handles common placement-week disruptions: a panel drops out, a room
becomes unavailable, a student withdraws, or a company is delayed. The system
tries to repair the affected interviews with the smallest possible change and
reports exactly what was changed and who needs to be notified.

## 2. Key Features

- **Realistic dataset generation** — companies are divided into mass
  recruiters, mid-tier, and dream-company tiers with different CGPA cutoffs,
  branch restrictions, panel counts, and interview durations. Students use a
  bell-curve CGPA distribution, and shortlist counts correlate with CGPA to
  create realistic overlapping demand.
- **Interview scheduling** — a greedy, priority-ordered scheduler assigns each
  eligible student-company pair to a panel, room, and time slot while enforcing
  the hard constraints.
- **Eligibility and resource constraints** — CGPA and branch eligibility are
  checked, and students, panels, and rooms cannot be double-booked.
- **Unscheduled interview reporting** — interviews that cannot be placed are
  recorded with a reason rather than being silently discarded.
- **Four disruption types** — panel drop, room block, student withdrawal, and
  company delay.
- **Tiered replanning** — panel drop, room block, and company delay use a repair
  ladder that attempts the smallest change first, then a wider search, and
  finally reports failure when no valid repair exists.
- **Coordinator dashboard** — a browser-based interface allows a coordinator
  to generate the schedule, inspect it, and trigger all four disruption types
  against the live schedule.

## 3. Architecture / Project Structure

| Package | Purpose |
|---|---|
| `model` | Core domain entities: `Company`, `Student`, `Panel`, `Room`, `Interview`, `TimeSlot`, `TimeGrid`, and `PriorityTier`. |
| `generator` | `DataGenerator` produces a reproducible `Dataset` containing companies, students, and rooms. |
| `scheduler` | `SchedulingEngine` creates the initial schedule using `OccupancyTracker` and returns a `SchedulingResult` containing scheduled and unscheduled entries. |
| `replan` | `ReplanEngine` handles the four disruption types using the corresponding `Disruption` implementations and produces `ReplanResult` objects. |
| `service` | `SchedulingStateService` holds the live dataset, scheduling state, and replanning state across HTTP requests. |
| `controller` | `PlacementController` exposes the REST API through seven endpoints. |
| `dto` | Request objects used by the disruption endpoints to keep API input separate from the domain disruption classes. |

## 4. Scheduling Approach

Each shortlisted student-company pair is checked against the hard constraints
before a slot is assigned:

- The student meets the company's CGPA cutoff and branch restriction.
- The student is free for the complete interview duration.
- The selected panel is active and free for the complete interview duration.
- The selected room is free for the complete interview duration.
- The interview is placed on the company's assigned day.

Candidates are processed using a deliberate priority order:

1. Students with **fewer total shortlists** are processed first, because they
   have fewer alternative opportunities if a slot is lost.
2. **Longer interviews** are processed before shorter interviews to reduce
   fragmentation of available time.
3. **Earlier company day** is preferred among remaining ties.
4. **Company tier** is used as an additional tie-break.
5. Student ID and company ID provide deterministic final tie-breaks.

For each candidate, the scheduler searches active panels and rooms across the
valid start times for the company's assigned day. The first combination that
satisfies all hard constraints is booked. If no valid combination exists, the
candidate is recorded as unscheduled with a specific reason.

## 5. Replanning Approach

Replanning separates interviews into two categories:

- **LOCKED** — interviews unrelated to the disruption. They are left untouched.
- **AFFECTED** — interviews that can no longer proceed because the disruption
  has changed or removed a required resource.

For panel drop, room block, and company delay, affected interviews go through a
repair ladder:

1. **Tier 1** — make the smallest possible change while keeping the rest of
   the interview unchanged.
2. **Tier 2** — perform a wider search across other valid times and, where
   applicable, other panels or rooms.
3. **Tier 3** — if no valid repair exists, leave the interview unscheduled and
   report the reason.

**Student withdrawal is handled differently.** A withdrawn student does not
need a replacement interview slot. Their affected interviews are therefore
reported as `CANCELLED_WITHDRAWN` rather than as failed repairs.

**Company delay respects a hard floor.** A delayed company's repaired
interviews are never placed before the specified delay threshold.

**No-cascade principle:** a repair is only placed into a slot that is already
free. The system does not displace another scheduled interview to make room
for a repair.

## 6. REST API

| Method | Endpoint | Purpose | Request Body |
|---|---|---|---|
| POST | `/api/schedule/generate` | Generates a new dataset and initial schedule, resetting live state. | None |
| GET | `/api/schedule/interviews` | Returns the current scheduled interviews. | None |
| GET | `/api/schedule/unscheduled` | Returns the current unscheduled entries. | None |
| POST | `/api/replan/panel-drop` | Applies a panel-drop disruption. | `{"panelId":"C1-P1","day":1,"unit":10}` |
| POST | `/api/replan/room-block` | Applies a room-block disruption. | `{"roomId":"R1","day":1,"unit":10}` |
| POST | `/api/replan/student-withdrawal` | Withdraws a student from the event. | `{"studentId":"S1","day":1,"unit":10}` |
| POST | `/api/replan/company-delay` | Delays a company. | `{"companyId":"C31","delayUntilUnit":2}` |

All replan endpoints operate on the same live schedule produced by the most
recent `/api/schedule/generate` call. Disruptions therefore accumulate across
requests instead of resetting the schedule.

## 7. Dashboard

The single-page dashboard provides:

- Schedule generation and summary counts.
- A view of scheduled interviews.
- A view of unscheduled interviews and their reasons.
- Forms for all four disruption types.
- Replan results showing affected interviews, repair tiers, cancellations or
  unscheduled outcomes, locked interviews, and students who need notification.

## 8. How to Run

### Requirements

- Java 21
- Maven
- Eclipse or another Java IDE (optional)

### From the project root

```bash
mvn spring-boot:run
```

### From Eclipse

Run `PlacementSchedulerApplication.java` as a Spring Boot application.

Once the application starts, open:

```text
http://localhost:8081
```

The dashboard is served as the default static page.

## 9. Verification

The scheduling and replanning logic was verified using dedicated Java demo
runners for data generation, scheduling, and the individual disruption types.
The REST API and dashboard were then exercised manually to confirm that the
HTTP results matched the verified Java logic.

With the fixed data-generation seed used during development, the scheduler
produces:

- **707 scheduled interviews**
- **3,241 unscheduled interviews**
- Approximately **4,000 total shortlisted pairs**

The high unscheduled count reflects the intentionally constrained placement
week dataset and limited shared room capacity, particularly on Day 1 where
mass recruiters create high demand.

### Individual disruption tests

Each disruption below was tested independently against a freshly generated
schedule.

| Disruption | Affected | Outcome |
|---|---:|---|
| Panel Drop (`C1-P1`, Day 1, unit 10) | 9 | 7 Tier 1 repairs, 2 Tier 3 unscheduled |
| Room Block (`R1`, Day 1, unit 10) | 8 | 8 Tier 3 unscheduled |
| Student Withdrawal (`S1`, Day 1, unit 10) | 3 | 3 `CANCELLED_WITHDRAWN` |
| Company Delay, Day 1 company (`C1`, delay until unit 20) | 16 | 16 Tier 3 unscheduled |
| Company Delay, Day 4 company (`C31`, delay until unit 2) | 4 | 4 Tier 1 repairs |

The Day 1 and Day 4 company-delay cases demonstrate that the same replanning
logic can produce different outcomes depending on the available capacity of
the affected day.

### Sequential state-persistence test

The four disruption types were also triggered one after another through the
dashboard without refreshing or regenerating the schedule. This confirmed
that the live state is carried from one request to the next.

| Step | Disruption | Affected | Locked | Schedule size after |
|---:|---|---:|---:|---:|
| 1 | Panel Drop (`C1-P1`, Day 1, unit 10) | 9 | 698 | 705 |
| 2 | Room Block (`R1`, Day 1, unit 10) | 8 | 697 | 697 |
| 3 | Student Withdrawal (`S1`, Day 1, unit 10) | 3 | 694 | 694 |
| 4 | Company Delay (`C31`, delay until unit 2) | 4 | 690 | 694 |

The locked count at each step matches the schedule size produced by the
previous step, providing a simple check that the disruptions were applied to
the cumulative live schedule rather than to a freshly generated schedule.

No automated test suite is included. Verification was performed through the
demo runners and manual dashboard testing.

## 10. Design Decisions

### Why state is held in `SchedulingStateService`

HTTP requests are stateless, but the scheduling and replanning engines use
live, mutating occupancy state. `SchedulingStateService` therefore holds the
current dataset, schedule, and engine state so that sequential disruptions
operate on the evolving schedule.

### Why withdrawal uses `CANCELLED_WITHDRAWN`

A withdrawn student does not need an alternative slot. Treating the cancelled
interview as a failed repair would confuse two different situations: an
interview that could not be scheduled and an interview that no longer needs
to happen.

### Why freed slots are not automatically backfilled

When an interview is cancelled or moved, the released slot is not
automatically given to another unscheduled student. This avoids silently
re-solving the schedule beyond the requested disruption and keeps each replan
small and explainable.

### Why company-delay repairs stay close to the original time

The delay threshold defines the earliest permitted replacement time, but the
repair is still ranked by proximity to the original interview time. This
keeps the repaired schedule as close as possible to the original plan while
respecting the delay.

### Verification demos

The repository also contains small Java demo runners used during development
to verify individual scheduling and replanning scenarios. They are retained
as development/verification examples; the Spring REST layer uses the same
core classes directly.
