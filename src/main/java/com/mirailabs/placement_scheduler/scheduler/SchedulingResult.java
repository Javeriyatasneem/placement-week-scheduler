package com.mirailabs.placement_scheduler.scheduler;

import com.mirailabs.placement_scheduler.model.Interview;

import java.util.List;

/**
 * Output of a scheduling run: what got booked, and what didn't (with reasons).
 * The dashboard reads both lists - scheduled interviews for the timetable
 * view, unscheduled entries for the "needs attention" panel.
 */
public class SchedulingResult {

    private final List<Interview> scheduledInterviews;
    private final List<UnscheduledEntry> unscheduledEntries;

    public SchedulingResult(List<Interview> scheduledInterviews, List<UnscheduledEntry> unscheduledEntries) {
        this.scheduledInterviews = scheduledInterviews;
        this.unscheduledEntries = unscheduledEntries;
    }

    public List<Interview> getScheduledInterviews() { return scheduledInterviews; }
    public List<UnscheduledEntry> getUnscheduledEntries() { return unscheduledEntries; }

    public void printSummary() {
        System.out.println("Scheduled interviews: " + scheduledInterviews.size());
        System.out.println("Unscheduled: " + unscheduledEntries.size());
    }
}
