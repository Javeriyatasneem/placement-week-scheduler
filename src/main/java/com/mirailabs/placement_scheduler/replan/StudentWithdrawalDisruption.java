package com.mirailabs.placement_scheduler.replan;

/**
 * Describes a student withdrawing, permanently, from the rest of the event.
 * Every one of their scheduled interviews at/after this point, across ALL
 * remaining days, is cancelled - not repaired, since there's nothing to
 * repair (the student isn't looking for an alternate slot).
 */
public class StudentWithdrawalDisruption implements Disruption {

    private final String studentId;
    private final int disruptionDay;
    private final int disruptionUnit;

    public StudentWithdrawalDisruption(String studentId, int disruptionDay, int disruptionUnit) {
        this.studentId = studentId;
        this.disruptionDay = disruptionDay;
        this.disruptionUnit = disruptionUnit;
    }

    public String getStudentId() { return studentId; }
    public int getDisruptionDay() { return disruptionDay; }
    public int getDisruptionUnit() { return disruptionUnit; }

    @Override
    public String toString() {
        return "StudentWithdrawalDisruption[student=" + studentId + ", from Day " + disruptionDay
                + " unit " + disruptionUnit + " onward - permanent for rest of event]";
    }
}
