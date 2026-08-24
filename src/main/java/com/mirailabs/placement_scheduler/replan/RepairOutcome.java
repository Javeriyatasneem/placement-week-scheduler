package com.mirailabs.placement_scheduler.replan;

import com.mirailabs.placement_scheduler.model.Interview;

/**
 * The outcome of attempting to repair ONE affected interview.
 * originalInterview is always set. newInterview is set only if repaired
 * (Tier 1 or Tier 2) - null if it landed in Tier 3 (unscheduled), in which
 * case reason explains why.
 */
public class RepairOutcome {

    public enum Tier {
        TIER_1_SAME_SLOT_ALT_PANEL,
        TIER_2_NEAREST_ALTERNATIVE,
        TIER_3_UNSCHEDULED
    }

    private final Interview originalInterview;
    private final Interview newInterview; // null if Tier 3
    private final Tier tier;
    private final String reason; // only meaningful for Tier 3

    public RepairOutcome(Interview originalInterview, Interview newInterview, Tier tier, String reason) {
        this.originalInterview = originalInterview;
        this.newInterview = newInterview;
        this.tier = tier;
        this.reason = reason;
    }

    public Interview getOriginalInterview() { return originalInterview; }
    public Interview getNewInterview() { return newInterview; }
    public Tier getTier() { return tier; }
    public String getReason() { return reason; }

    public boolean isRepaired() {
        return tier != Tier.TIER_3_UNSCHEDULED;
    }

    @Override
    public String toString() {
        if (tier == Tier.TIER_3_UNSCHEDULED) {
            return "UNSCHEDULED (was: " + originalInterview + ") -> " + reason;
        }
        return "[" + tier + "] " + originalInterview + "\n    -> " + newInterview;
    }
}
