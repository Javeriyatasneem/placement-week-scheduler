package com.mirailabs.placement_scheduler.replan;

import com.mirailabs.placement_scheduler.generator.Dataset;
import com.mirailabs.placement_scheduler.model.*;
import com.mirailabs.placement_scheduler.scheduler.OccupancyTracker;
import com.mirailabs.placement_scheduler.scheduler.SchedulingEngine;

import java.util.*;

/**
 * Handles replanning after a disruption. Currently supports PANEL DROP only
 * (student withdrawal, company delay, room block are NOT implemented yet -
 * by design, per current scope).
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
        for (int start = 0; start + durationUnits <= TimeGrid.UNITS_PER_DAY; start++) {
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
