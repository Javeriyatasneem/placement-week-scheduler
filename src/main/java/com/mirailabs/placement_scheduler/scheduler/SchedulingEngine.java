package com.mirailabs.placement_scheduler.scheduler;

import com.mirailabs.placement_scheduler.generator.Dataset;
import com.mirailabs.placement_scheduler.model.*;

import java.util.*;

/**
 * Greedy scheduling engine (no external constraint-solver library - see
 * project notes for why: a hand-written greedy + backtracking algorithm is
 * easier to explain and defend than a black-box solver).
 *
 * ORDERING STRATEGY (the key design decision - four levels, in order):
 *
 *  1. PRIMARY: fewer total shortlists first. This is "most constrained
 *     variable first" - a topper with 10 shortlists has plenty of fallback
 *     options if one slot is taken; a student with only 1-2 shortlists does
 *     not, so they get first claim on scarce slots.
 *
 *  2. SECONDARY: longer interview duration first (Longest Processing Time
 *     bin-packing heuristic) - placing long interviews first lets short ones
 *     fill whatever gaps are left over, instead of fragmenting the day.
 *
 *  3. TERTIARY: earlier company day first (Day 1 before Day 4) - keeps
 *     scheduling roughly chronological among remaining ties.
 *
 *  4. QUATERNARY: company tier, MASS_RECRUITER before MID_TIER before
 *     DREAM_COMPANY - a fallback signal only used when the above three are
 *     still tied.
 *
 *  5. FINAL: deterministic tie-break on student ID then company ID, so
 *     runs are reproducible instead of depending on incidental list order.
 *
 * For each (student, company) pair, we try every active panel x every room
 * x every valid start time on the company's day. If no valid combination
 * exists, we record why in an UnscheduledEntry - never a silent drop.
 */
public class SchedulingEngine {

    private final OccupancyTracker studentOccupancy = new OccupancyTracker();
    private final OccupancyTracker panelOccupancy = new OccupancyTracker();
    private final OccupancyTracker roomOccupancy = new OccupancyTracker();

    /** One (student, company) interview we still need to try to place. */
    private static class Candidate {
        final Student student;
        final Company company;
        Candidate(Student student, Company company) {
            this.student = student;
            this.company = company;
        }
    }

    public SchedulingResult schedule(Dataset dataset) {
        Map<String, Company> companiesById = new HashMap<>();
        for (Company c : dataset.getCompanies()) {
            companiesById.put(c.getId(), c);
        }

        // Build the full list of interviews we need to attempt, one per
        // (student, shortlisted company) pair.
        List<Candidate> candidates = new ArrayList<>();
        for (Student student : dataset.getStudents()) {
            if (student.isWithdrawn()) {
                continue; // withdrawn students are skipped entirely - handled in replanning
            }
            for (String companyId : student.getShortlistedCompanyIds()) {
                Company company = companiesById.get(companyId);
                if (company != null) {
                    candidates.add(new Candidate(student, company));
                }
            }
        }

        // 1. Fewer shortlists first (fairness). 2. Longer duration first
        // (packing efficiency). 3. Earlier day first. 4. Mass recruiter ->
        // mid-tier -> dream company (tier enum's declared order already
        // matches this, so ordinal() works directly). 5. Deterministic
        // final tie-break on student ID then company ID.
        candidates.sort(
                Comparator.comparingInt((Candidate c) -> c.student.getShortlistedCompanyIds().size())
                        .thenComparingInt(c -> -c.company.getInterviewDurationUnits())
                        .thenComparingInt(c -> c.company.getDay())
                        .thenComparingInt(c -> c.company.getTier().ordinal())
                        .thenComparing((Candidate c) -> c.student.getId())
                        .thenComparing(c -> c.company.getId())
        );

        List<Interview> scheduled = new ArrayList<>();
        List<UnscheduledEntry> unscheduled = new ArrayList<>();

        for (Candidate candidate : candidates) {
            Optional<Interview> placed = tryPlaceInterview(candidate.student, candidate.company, dataset.getRooms());
            if (placed.isPresent()) {
                scheduled.add(placed.get());
            } else {
                unscheduled.add(new UnscheduledEntry(candidate.student.getId(), candidate.company.getId(),
                        reasonFor(candidate.student, candidate.company)));
            }
        }

        return new SchedulingResult(scheduled, unscheduled);
    }

    /**
     * Tries every active panel x every room x every valid start unit for this
     * company's interview duration, on the company's assigned day, honoring
     * any delay the company currently has. Returns the first valid slot found
     * (first-fit among the pre-sorted candidate order, not a globally optimal
     * search - good enough here, and easy to defend: "I optimized packing via
     * ordering strategy rather than exhaustive search, given the time
     * constraints of the assignment").
     */
    private Optional<Interview> tryPlaceInterview(Student student, Company company, List<Room> rooms) {
        // Hard constraint: eligibility (CGPA cutoff + branch restriction).
        // Checked here explicitly so the SCHEDULER enforces it directly,
        // rather than only trusting that the data generator already filtered
        // shortlists - important once replanning starts reassigning students.
        if (!company.isEligible(student)) {
            return Optional.empty();
        }

        int day = company.getDay();
        int durationUnits = company.getInterviewDurationUnits();
        int earliestStart = company.getDelayInUnits(); // 0 unless a delay disruption is active

        List<Panel> activePanels = new ArrayList<>();
        for (Panel p : company.getPanels()) {
            if (p.isActive()) {
                activePanels.add(p);
            }
        }
        if (activePanels.isEmpty()) {
            return Optional.empty(); // all panels dropped - nothing to try
        }

        for (int start = earliestStart; start + durationUnits <= TimeGrid.UNITS_PER_DAY; start++) {
            TimeSlot slot = new TimeSlot(day, start, start + durationUnits);

            if (!studentOccupancy.isFree(student.getId(), slot)) {
                continue; // student already busy in this window - try next start time
            }

            for (Panel panel : activePanels) {
                if (!panelOccupancy.isFree(panel.getId(), slot)) {
                    continue;
                }
                for (Room room : rooms) {
                    if (isRoomBlocked(room, slot)) {
                        continue;
                    }
                    if (!roomOccupancy.isFree(room.getId(), slot)) {
                        continue;
                    }

                    // Found a valid combination - book it.
                    studentOccupancy.occupy(student.getId(), slot);
                    panelOccupancy.occupy(panel.getId(), slot);
                    roomOccupancy.occupy(room.getId(), slot);

                    return Optional.of(new Interview(student.getId(), company.getId(),
                            panel.getId(), room.getId(), slot));
                }
            }
        }

        return Optional.empty();
    }

    private boolean isRoomBlocked(Room room, TimeSlot slot) {
        for (int unit = slot.getStartUnit(); unit < slot.getEndUnit(); unit++) {
            if (room.isBlocked(slot.getDay(), unit)) {
                return true;
            }
        }
        return false;
    }

    /** Best-effort human-readable reason, used only for the unscheduled report. */
    private String reasonFor(Student student, Company company) {
        if (!company.isEligible(student)) {
            return "Student does not meet CGPA cutoff or branch restriction for this company";
        }
        boolean anyActivePanel = company.getPanels().stream().anyMatch(Panel::isActive);
        if (!anyActivePanel) {
            return "All panels for this company are inactive/dropped";
        }
        return "No common free student/panel/room slot found on Day " + company.getDay()
                + " (student's schedule likely already full, or panels/rooms fully booked)";
    }

    // Exposed so the replanner can reuse the same live occupancy state
    // instead of rebuilding it from scratch after a disruption.
    public OccupancyTracker getStudentOccupancy() { return studentOccupancy; }
    public OccupancyTracker getPanelOccupancy() { return panelOccupancy; }
    public OccupancyTracker getRoomOccupancy() { return roomOccupancy; }
}
