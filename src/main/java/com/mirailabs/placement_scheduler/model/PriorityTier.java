package com.mirailabs.placement_scheduler.model;

/**
 * Companies aren't all equal. Mass recruiters (usually Day 1) hire in bulk
 * and get priority when we have to bend constraints. Dream/niche companies
 * hire fewer people but students care a lot about them.
 *
 * This tier is what we fall back on when the schedule can't fit everyone —
 * see SchedulingEngine's constraint-bending order.
 */
public enum PriorityTier {
    MASS_RECRUITER,   // e.g. TCS, Infosys — high volume, usually Day 1
    MID_TIER,         // decent brand, moderate hiring
    DREAM_COMPANY      // Google/Amazon-style — low volume, high demand, usually later days
}
