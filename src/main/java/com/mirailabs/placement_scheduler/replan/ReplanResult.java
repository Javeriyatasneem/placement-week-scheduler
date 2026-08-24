package com.mirailabs.placement_scheduler.replan;

import java.util.List;
import java.util.Set;

/**
 * Full output of one replan run: the disruption that triggered it, every
 * repair attempt (Tier 1/2/3), and the derived "who needs to know" list.
 *
 * Deliberately does NOT include the LOCKED set - per the assignment's own
 * "clear diff" requirement, untouched interviews shouldn't clutter the
 * output. The demo prints the locked COUNT separately, not the full list.
 */
public class ReplanResult {

    private final PanelDropDisruption disruption;
    private final List<RepairOutcome> repairOutcomes; // Tier 1 + Tier 2 + Tier 3, in that processing order
    private final int lockedInterviewCount;
    private final Set<String> studentsToNotify;

    public ReplanResult(PanelDropDisruption disruption, List<RepairOutcome> repairOutcomes,
                         int lockedInterviewCount, Set<String> studentsToNotify) {
        this.disruption = disruption;
        this.repairOutcomes = repairOutcomes;
        this.lockedInterviewCount = lockedInterviewCount;
        this.studentsToNotify = studentsToNotify;
    }

    public PanelDropDisruption getDisruption() { return disruption; }
    public List<RepairOutcome> getRepairOutcomes() { return repairOutcomes; }
    public int getLockedInterviewCount() { return lockedInterviewCount; }
    public Set<String> getStudentsToNotify() { return studentsToNotify; }

    public long countByTier(RepairOutcome.Tier tier) {
        return repairOutcomes.stream().filter(o -> o.getTier() == tier).count();
    }
}
