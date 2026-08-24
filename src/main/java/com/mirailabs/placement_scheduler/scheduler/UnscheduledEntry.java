package com.mirailabs.placement_scheduler.scheduler;

/**
 * Whenever the scheduler can't place a student-company interview, we record
 * WHY here - never a silent drop. The coordinator dashboard surfaces this
 * list directly so nobody just "disappears" from the schedule unexplained.
 */
public class UnscheduledEntry {

    private final String studentId;
    private final String companyId;
    private final String reason;

    public UnscheduledEntry(String studentId, String companyId, String reason) {
        this.studentId = studentId;
        this.companyId = companyId;
        this.reason = reason;
    }

    public String getStudentId() { return studentId; }
    public String getCompanyId() { return companyId; }
    public String getReason() { return reason; }

    @Override
    public String toString() {
        return "UNSCHEDULED: student=" + studentId + ", company=" + companyId + " -> " + reason;
    }
}
