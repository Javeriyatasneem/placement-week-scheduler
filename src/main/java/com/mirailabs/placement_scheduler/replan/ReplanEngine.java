package com.mirailabs.placement_scheduler.replan;

import com.mirailabs.placement_scheduler.generator.Dataset;
import com.mirailabs.placement_scheduler.model.*;
import com.mirailabs.placement_scheduler.scheduler.OccupancyTracker;
import com.mirailabs.placement_scheduler.scheduler.SchedulingEngine;

import java.util.*;

/**
 * Handles replanning after all four disruption types: panel drop, room
 * block, student withdrawal, and company delay.
 *
 * Reuses the SAME live OccupancyTracker instances from the SchedulingEngine
 * that produced the original schedule - we do NOT rebuild occupancy from
 * scratch. This is what lets us do a scoped repair instead of a full re-solve.
 *
 * ARCHITECTURAL NOTE: Company.getDay() is a single fixed value - a company
 * only ever operates on one day. This means a genuine "different day" repair
 * is not reachable in the current model (there's nowhere else for that
 * company's interview to go). Tier 2 below searches only within the
 * company's existing day, varying start time (and room). If multi-day
 * companies are added later, the cross-day distance ranking would need to
 * be reintroduced here.
 */
public class ReplanEngine {

    private final OccupancyTracker studentOccupancy;
    private final OccupancyTracker panelOccupancy;
    private final OccupancyTracker roomOccupancy;

    public ReplanEngine(SchedulingEngine schedulingEngine) {
        // Reuse the SAME tracker instances - critical. A fresh OccupancyTracker
        // would think every slot is free, defeating the whole point of a
        // scoped repair.
        this.studentOccupancy = schedulingEngine.getStudentOccupancy();
        this.panelOccupancy = schedulingEngine.getPanelOccupancy();
        this.roomOccupancy = schedulingEngine.getRoomOccupancy();
    }

    public ReplanResult replanForPanelDrop(PanelDropDisruption disruption, Dataset dataset,
                                            List<Interview> currentSchedule) {

        Map<String, Company> companiesById = new HashMap<>();
        for (Company c : dataset.getCompanies()) {
            companiesById.put(c.getId(), c);
        }

        // Find and deactivate the panel itself.
        Panel droppedPanel = findPanel(dataset, disruption.getPanelId());
        if (droppedPanel != null) {
            droppedPanel.setActive(false);
        }
        Company ownerCompany = droppedPanel != null ? companiesById.get(droppedPanel.getCompanyId()) : null;

        // Partition into LOCKED vs AFFECTED.
        List<Interview> affected = new ArrayList<>();
        int lockedCount = 0;
        for (Interview interview : currentSchedule) {
            boolean onDroppedPanel = interview.getPanelId().equals(disruption.getPanelId());
            boolean atOrAfterDisruption = isAtOrAfter(interview.getTimeSlot(), disruption);
            if (onDroppedPanel && atOrAfterDisruption) {
                affected.add(interview);
            } else {
                lockedCount++;
            }
        }

        List<RepairOutcome> outcomes = new ArrayList<>();
        Set<String> studentsToNotify = new TreeSet<>();

        for (Interview original : affected) {
            studentsToNotify.add(original.getStudentId());

            // Free up this interview's resources FIRST - a repair may only
            // use slots that are genuinely free, and this interview's own
            // old slot must become available again for consideration
            // (e.g. same room/time with a different panel in Tier 1).
            studentOccupancy.release(original.getStudentId(), original.getTimeSlot());
            panelOccupancy.release(original.getPanelId(), original.getTimeSlot());
            roomOccupancy.release(original.getRoomId(), original.getTimeSlot());

            if (ownerCompany == null) {
                outcomes.add(new RepairOutcome(original, null, RepairOutcome.Tier.TIER_3_UNSCHEDULED,
                        "Owning company for dropped panel could not be resolved"));
                continue;
            }

            Optional<Interview> tier1 = tryTier1(original, ownerCompany);
            if (tier1.isPresent()) {
                outcomes.add(new RepairOutcome(original, tier1.get(), RepairOutcome.Tier.TIER_1_SAME_SLOT_ALT_PANEL, null));
                continue;
            }

            Optional<Interview> tier2 = tryTier2(original, ownerCompany, dataset.getRooms());
            if (tier2.isPresent()) {
                outcomes.add(new RepairOutcome(original, tier2.get(), RepairOutcome.Tier.TIER_2_NEAREST_ALTERNATIVE, null));
                continue;
            }

            // Tier 3 - no cascade attempted, no fix found. Resources stay released
            // (this interview genuinely no longer happens).
            outcomes.add(new RepairOutcome(original, null, RepairOutcome.Tier.TIER_3_UNSCHEDULED,
                    "Panel " + disruption.getPanelId() + " became unavailable; no alternate panel or time slot "
                            + "could be found for this company on Day " + ownerCompany.getDay()));
        }

        return new ReplanResult(disruption, outcomes, lockedCount, studentsToNotify);
    }

    public ReplanResult replanForRoomBlock(RoomBlockDisruption disruption, Dataset dataset,
                                            List<Interview> currentSchedule) {

        Map<String, Company> companiesById = new HashMap<>();
        for (Company c : dataset.getCompanies()) {
            companiesById.put(c.getId(), c);
        }

        // Apply the block itself - room is unavailable for the rest of this day.
        Room blockedRoom = findRoom(dataset, disruption.getRoomId());
        if (blockedRoom != null) {
            blockedRoom.blockFrom(disruption.getDisruptionDay(), disruption.getDisruptionUnit());
        }

        // Partition into LOCKED vs AFFECTED. A room block is day-scoped, so
        // interviews in this room on OTHER days are untouched - unlike the
        // panel drop, which was permanent from its trigger point onward.
        List<Interview> affected = new ArrayList<>();
        int lockedCount = 0;
        for (Interview interview : currentSchedule) {
            boolean inBlockedRoom = interview.getRoomId().equals(disruption.getRoomId());
            boolean sameDay = interview.getTimeSlot().getDay() == disruption.getDisruptionDay();
            boolean overlapsBlock = interview.getTimeSlot().getEndUnit() > disruption.getDisruptionUnit();
            if (inBlockedRoom && sameDay && overlapsBlock) {
                affected.add(interview);
            } else {
                lockedCount++;
            }
        }

        List<RepairOutcome> outcomes = new ArrayList<>();
        Set<String> studentsToNotify = new TreeSet<>();

        for (Interview original : affected) {
            studentsToNotify.add(original.getStudentId());

            studentOccupancy.release(original.getStudentId(), original.getTimeSlot());
            panelOccupancy.release(original.getPanelId(), original.getTimeSlot());
            roomOccupancy.release(original.getRoomId(), original.getTimeSlot());

            Company company = companiesById.get(original.getCompanyId());
            if (company == null) {
                outcomes.add(new RepairOutcome(original, null, RepairOutcome.Tier.TIER_3_UNSCHEDULED,
                        "Owning company for this interview could not be resolved"));
                continue;
            }

            Optional<Interview> tier1 = tryRoomTier1(original, dataset.getRooms());
            if (tier1.isPresent()) {
                outcomes.add(new RepairOutcome(original, tier1.get(), RepairOutcome.Tier.TIER_1_SAME_SLOT_ALT_PANEL, null));
                continue;
            }

            Optional<Interview> tier2 = tryTier2(original, company, dataset.getRooms());
            if (tier2.isPresent()) {
                outcomes.add(new RepairOutcome(original, tier2.get(), RepairOutcome.Tier.TIER_2_NEAREST_ALTERNATIVE, null));
                continue;
            }

            outcomes.add(new RepairOutcome(original, null, RepairOutcome.Tier.TIER_3_UNSCHEDULED,
                    "Room " + disruption.getRoomId() + " became unavailable from Day " + disruption.getDisruptionDay()
                            + " onward; no alternate room or time slot could be found for this interview"));
        }

        return new ReplanResult(disruption, outcomes, lockedCount, studentsToNotify);
    }

    public ReplanResult replanForStudentWithdrawal(StudentWithdrawalDisruption disruption, Dataset dataset,
                                                     List<Interview> currentSchedule) {

        Student student = findStudent(dataset, disruption.getStudentId());
        if (student != null) {
            student.setWithdrawn(true);
        }

        // Partition into LOCKED vs AFFECTED. Unlike panel-drop/room-block,
        // AFFECTED here spans ALL remaining days, not just the disruption
        // day - withdrawal is permanent for the rest of the event.
        List<Interview> affected = new ArrayList<>();
        int lockedCount = 0;
        for (Interview interview : currentSchedule) {
            boolean isThisStudent = interview.getStudentId().equals(disruption.getStudentId());
            boolean atOrAfter = isAtOrAfterWithdrawal(interview.getTimeSlot(), disruption);
            if (isThisStudent && atOrAfter) {
                affected.add(interview);
            } else {
                lockedCount++;
            }
        }

        List<RepairOutcome> outcomes = new ArrayList<>();
        Set<String> studentsToNotify = new TreeSet<>();

        for (Interview original : affected) {
            studentsToNotify.add(original.getStudentId());

            // Release resources - NOT backfilled, per the no-cascade rule.
            studentOccupancy.release(original.getStudentId(), original.getTimeSlot());
            panelOccupancy.release(original.getPanelId(), original.getTimeSlot());
            roomOccupancy.release(original.getRoomId(), original.getTimeSlot());

            // No repair attempted at all - this is a cancellation, not a
            // search for an alternative. The student isn't looking for a
            // new slot; they're gone.
            outcomes.add(new RepairOutcome(original, null, RepairOutcome.Tier.CANCELLED_WITHDRAWN,
                    "Student withdrew as of Day " + disruption.getDisruptionDay() + ", unit "
                            + disruption.getDisruptionUnit() + "; interview cancelled"));
        }

        return new ReplanResult(disruption, outcomes, lockedCount, studentsToNotify);
    }

    public ReplanResult replanForCompanyDelay(CompanyDelayDisruption disruption, Dataset dataset,
                                               List<Interview> currentSchedule) {

        Map<String, Company> companiesById = new HashMap<>();
        for (Company c : dataset.getCompanies()) {
            companiesById.put(c.getId(), c);
        }

        Company company = companiesById.get(disruption.getCompanyId());
        if (company != null) {
            company.setDelayInUnits(disruption.getDelayUntilUnit());
        }

        // Partition into LOCKED vs AFFECTED. Only this company's interviews
        // that start BEFORE the delay threshold are affected - anything
        // already at/after it is fine as scheduled.
        List<Interview> affected = new ArrayList<>();
        int lockedCount = 0;
        for (Interview interview : currentSchedule) {
            boolean isThisCompany = interview.getCompanyId().equals(disruption.getCompanyId());
            boolean startsBeforeDelay = interview.getTimeSlot().getStartUnit() < disruption.getDelayUntilUnit();
            if (isThisCompany && startsBeforeDelay) {
                affected.add(interview);
            } else {
                lockedCount++;
            }
        }

        List<RepairOutcome> outcomes = new ArrayList<>();
        Set<String> studentsToNotify = new TreeSet<>();

        for (Interview original : affected) {
            studentsToNotify.add(original.getStudentId());

            studentOccupancy.release(original.getStudentId(), original.getTimeSlot());
            panelOccupancy.release(original.getPanelId(), original.getTimeSlot());
            roomOccupancy.release(original.getRoomId(), original.getTimeSlot());

            if (company == null) {
                outcomes.add(new RepairOutcome(original, null, RepairOutcome.Tier.TIER_3_UNSCHEDULED,
                        "Owning company for this interview could not be resolved"));
                continue;
            }

            Optional<Interview> tier1 = tryDelayTier1(original, disruption.getDelayUntilUnit());
            if (tier1.isPresent()) {
                outcomes.add(new RepairOutcome(original, tier1.get(), RepairOutcome.Tier.TIER_1_SAME_SLOT_ALT_PANEL, null));
                continue;
            }

            Optional<Interview> tier2 = tryTier2(original, company, dataset.getRooms(), disruption.getDelayUntilUnit());
            if (tier2.isPresent()) {
                outcomes.add(new RepairOutcome(original, tier2.get(), RepairOutcome.Tier.TIER_2_NEAREST_ALTERNATIVE, null));
                continue;
            }

            outcomes.add(new RepairOutcome(original, null, RepairOutcome.Tier.TIER_3_UNSCHEDULED,
                    "Company delayed until unit " + disruption.getDelayUntilUnit()
                            + "; no feasible panel/room/time slot found at or after the delay threshold"));
        }

        return new ReplanResult(disruption, outcomes, lockedCount, studentsToNotify);
    }

    private Student findStudent(Dataset dataset, String studentId) {
        for (Student s : dataset.getStudents()) {
            if (s.getId().equals(studentId)) {
                return s;
            }
        }
        return null;
    }

    /** True if this slot is at or after the withdrawal point (same day+unit or a later day). */
    private boolean isAtOrAfterWithdrawal(TimeSlot slot, StudentWithdrawalDisruption disruption) {
        if (slot.getDay() != disruption.getDisruptionDay()) {
            return slot.getDay() > disruption.getDisruptionDay();
        }
        return slot.getStartUnit() >= disruption.getDisruptionUnit();
    }

    /**
     * DELAY TIER 1: same panel, same room - search forward from the delay
     * threshold for the EARLIEST feasible start (not "old start + delay
     * offset", which could land on an already-booked slot). First free hit
     * at or after minStartUnit wins.
     */
    private Optional<Interview> tryDelayTier1(Interview original, int minStartUnit) {
        int day = original.getTimeSlot().getDay();
        int durationUnits = original.getTimeSlot().getEndUnit() - original.getTimeSlot().getStartUnit();
        int earliestPossible = Math.max(minStartUnit, 0);

        for (int start = earliestPossible; start + durationUnits <= TimeGrid.UNITS_PER_DAY; start++) {
            TimeSlot candidateSlot = new TimeSlot(day, start, start + durationUnits);

            if (!studentOccupancy.isFree(original.getStudentId(), candidateSlot)) {
                continue;
            }
            if (!panelOccupancy.isFree(original.getPanelId(), candidateSlot)) {
                continue;
            }
            if (!roomOccupancy.isFree(original.getRoomId(), candidateSlot)) {
                continue;
            }

            return bookRepair(original, original.getPanelId(), original.getRoomId(), candidateSlot);
        }

        return Optional.empty();
    }

    private Room findRoom(Dataset dataset, String roomId) {
        for (Room r : dataset.getRooms()) {
            if (r.getId().equals(roomId)) {
                return r;
            }
        }
        return null;
    }

    /**
     * ROOM-BLOCK TIER 1: same day, same start time, same panel, same student -
     * only the room changes. Panel-fixed by design (mirrors panel-drop's
     * Tier 1, with room/panel roles swapped). Deterministic: lowest room ID
     * wins among free, unblocked alternatives.
     */
    private Optional<Interview> tryRoomTier1(Interview original, List<Room> rooms) {
        TimeSlot slot = original.getTimeSlot();

        if (!panelOccupancy.isFree(original.getPanelId(), slot)) {
            return Optional.empty(); // panel itself no longer free at this slot - Tier 1 can't hold panel fixed
        }

        List<Room> candidates = new ArrayList<>();
        for (Room room : rooms) {
            if (room.getId().equals(original.getRoomId())) {
                continue; // the disrupted room itself is never a valid candidate
            }
            if (isRoomBlocked(room, slot)) {
                continue;
            }
            if (roomOccupancy.isFree(room.getId(), slot)) {
                candidates.add(room);
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        candidates.sort(Comparator.comparing(Room::getId));
        Room chosen = candidates.get(0);

        return bookRepair(original, original.getPanelId(), chosen.getId(), slot);
    }

    /**
     * ReplanResult#getDisruption() is typed generically enough that either
     * disruption's toString() flows through unchanged - no ReplanResult
     * changes were needed for room-block support.
     */

    /** True if this slot is at or after the disruption point (same day+unit or later). */
    private boolean isAtOrAfter(TimeSlot slot, PanelDropDisruption disruption) {
        if (slot.getDay() != disruption.getDisruptionDay()) {
            return slot.getDay() > disruption.getDisruptionDay();
        }
        return slot.getStartUnit() >= disruption.getDisruptionUnit();
    }

    private Panel findPanel(Dataset dataset, String panelId) {
        for (Company c : dataset.getCompanies()) {
            for (Panel p : c.getPanels()) {
                if (p.getId().equals(panelId)) {
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * TIER 1: same day, same start time, same room, same student - only the
     * panel changes. Cheapest possible fix; tried first for every affected
     * interview.
     */
    private Optional<Interview> tryTier1(Interview original, Company company) {
        TimeSlot slot = original.getTimeSlot();

        // Room must still be free at this exact slot (it will be, since we
        // just released it and no cascade/displacement is allowed - but we
        // verify rather than assume).
        if (!roomOccupancy.isFree(original.getRoomId(), slot)) {
            return Optional.empty();
        }

        List<Panel> candidates = new ArrayList<>();
        for (Panel p : company.getPanels()) {
            if (p.isActive() && panelOccupancy.isFree(p.getId(), slot)) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        // Deterministic: lowest panel ID wins if multiple are free.
        candidates.sort(Comparator.comparing(Panel::getId));
        Panel chosen = candidates.get(0);

        return bookRepair(original, chosen.getId(), original.getRoomId(), slot);
    }

    /**
     * TIER 2: search every other start time on the company's day (the only
     * day that exists for this company - see class-level note), ranked by:
     *   1. absolute time-distance from the original start (nearest first)
     *   2. later start preferred over earlier on a distance tie
     *   3. room ID (ascending)
     *   4. panel ID (ascending)
     * First valid (panel, room) combination found at the nearest ranked
     * slot wins. Never displaces an existing interview - only considers
     * slots that are already free.
     */
    private Optional<Interview> tryTier2(Interview original, Company company, List<Room> rooms) {
        return tryTier2(original, company, rooms, 0);
    }

    /**
     * Overload with an explicit minStartUnit floor, used by company-delay
     * replanning so no candidate before the delay threshold is ever
     * considered - regardless of how "close" it'd otherwise rank. Existing
     * callers (panel-drop, room-block) use the 0-floor overload above,
     * unchanged.
     */
    private Optional<Interview> tryTier2(Interview original, Company company, List<Room> rooms, int minStartUnit) {
        TimeSlot originalSlot = original.getTimeSlot();
        int day = company.getDay();
        int durationUnits = company.getInterviewDurationUnits();
        int originalStart = originalSlot.getStartUnit();

        List<Panel> activePanels = new ArrayList<>();
        for (Panel p : company.getPanels()) {
            if (p.isActive()) {
                activePanels.add(p);
            }
        }
        activePanels.sort(Comparator.comparing(Panel::getId));

        List<Room> sortedRooms = new ArrayList<>(rooms);
        sortedRooms.sort(Comparator.comparing(Room::getId));

        // Build every valid candidate start unit for this day, then rank by
        // proximity per the policy above.
        List<Integer> candidateStarts = new ArrayList<>();
        for (int start = Math.max(minStartUnit, 0); start + durationUnits <= TimeGrid.UNITS_PER_DAY; start++) {
            candidateStarts.add(start);
        }
        candidateStarts.sort((a, b) -> {
            int distA = Math.abs(a - originalStart);
            int distB = Math.abs(b - originalStart);
            if (distA != distB) {
                return Integer.compare(distA, distB);
            }
            // Tie on distance: prefer later start (>= original) over earlier.
            boolean aIsLaterOrEqual = a >= originalStart;
            boolean bIsLaterOrEqual = b >= originalStart;
            if (aIsLaterOrEqual != bIsLaterOrEqual) {
                return aIsLaterOrEqual ? -1 : 1;
            }
            return Integer.compare(a, b); // final numeric fallback, still deterministic
        });

        for (int start : candidateStarts) {
            TimeSlot candidateSlot = new TimeSlot(day, start, start + durationUnits);

            if (!studentOccupancy.isFree(original.getStudentId(), candidateSlot)) {
                continue;
            }

            for (Panel panel : activePanels) {
                if (!panelOccupancy.isFree(panel.getId(), candidateSlot)) {
                    continue;
                }
                for (Room room : sortedRooms) {
                    if (isRoomBlocked(room, candidateSlot)) {
                        continue;
                    }
                    if (!roomOccupancy.isFree(room.getId(), candidateSlot)) {
                        continue;
                    }
                    return bookRepair(original, panel.getId(), room.getId(), candidateSlot);
                }
            }
        }

        return Optional.empty();
    }

    private Optional<Interview> bookRepair(Interview original, String panelId, String roomId, TimeSlot slot) {
        studentOccupancy.occupy(original.getStudentId(), slot);
        panelOccupancy.occupy(panelId, slot);
        roomOccupancy.occupy(roomId, slot);
        return Optional.of(new Interview(original.getStudentId(), original.getCompanyId(), panelId, roomId, slot));
    }

    private boolean isRoomBlocked(Room room, TimeSlot slot) {
        for (int unit = slot.getStartUnit(); unit < slot.getEndUnit(); unit++) {
            if (room.isBlocked(slot.getDay(), unit)) {
                return true;
            }
        }
        return false;
    }
}
