package com.mirailabs.placement_scheduler.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Company {

    private final String id;
    private final String name;
    private final int day;                 // which day (1-4) this company is on campus
    private final PriorityTier tier;
    private final double cgpaCutoff;        // hard filter - students below this aren't eligible
    private final Set<String> allowedBranches; // hard filter - null/empty means "open to all branches"
    private final int interviewDurationMinutes;
    private final int openPositions;
    private final List<Panel> panels = new ArrayList<>();

    // Disruption state - set to true if this company is affected by a live disruption
    private int delayInUnits = 0;           // e.g. arrived 2 hours late -> shifts earliest possible start

    public Company(String id, String name, int day, PriorityTier tier,
                   double cgpaCutoff, Set<String> allowedBranches,
                   int interviewDurationMinutes, int openPositions) {
        this.id = id;
        this.name = name;
        this.day = day;
        this.tier = tier;
        this.cgpaCutoff = cgpaCutoff;
        this.allowedBranches = allowedBranches;
        this.interviewDurationMinutes = interviewDurationMinutes;
        this.openPositions = openPositions;
    }

    public void addPanel(Panel panel) {
        panels.add(panel);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getDay() { return day; }
    public PriorityTier getTier() { return tier; }
    public double getCgpaCutoff() { return cgpaCutoff; }
    public int getInterviewDurationMinutes() { return interviewDurationMinutes; }
    public int getInterviewDurationUnits() { return TimeGrid.unitsFor(interviewDurationMinutes); }
    public int getOpenPositions() { return openPositions; }
    public List<Panel> getPanels() { return panels; }
    public int getDelayInUnits() { return delayInUnits; }
    public void setDelayInUnits(int delayInUnits) { this.delayInUnits = delayInUnits; }

    /** A student is eligible only if BOTH the CGPA cutoff and branch restriction pass. */
    public boolean isEligible(Student student) {
        boolean cgpaOk = student.getCgpa() >= cgpaCutoff;
        boolean branchOk = allowedBranches == null || allowedBranches.isEmpty()
                || allowedBranches.contains(student.getBranch());
        return cgpaOk && branchOk;
    }

    public Set<String> getAllowedBranches() { return allowedBranches; }

    @Override
    public String toString() {
        return name + " (Day " + day + ", " + tier + ", cutoff " + cgpaCutoff + ")";
    }
}
