package com.mirailabs.placement_scheduler.model;

import java.util.Objects;

/**
 * A contiguous block of time-units on a given day.
 * e.g. day=1, startUnit=4, endUnit=7 -> Day 1, 10:00 AM to 10:45 AM (3 units of 15 min).
 *
 * endUnit is EXCLUSIVE (like a normal array range) - this makes overlap
 * checks simple: two slots overlap if they're on the same day AND
 * startA < endB AND startB < endA.
 */
public class TimeSlot {

    private final int day;
    private final int startUnit;
    private final int endUnit; // exclusive

    public TimeSlot(int day, int startUnit, int endUnit) {
        if (endUnit <= startUnit) {
            throw new IllegalArgumentException("endUnit must be after startUnit");
        }
        this.day = day;
        this.startUnit = startUnit;
        this.endUnit = endUnit;
    }

    public int getDay() {
        return day;
    }

    public int getStartUnit() {
        return startUnit;
    }

    public int getEndUnit() {
        return endUnit;
    }

    public int durationInUnits() {
        return endUnit - startUnit;
    }

    /** Does this slot overlap another? Only meaningful on the same day. */
    public boolean overlaps(TimeSlot other) {
        if (this.day != other.day) {
            return false;
        }
        return this.startUnit < other.endUnit && other.startUnit < this.endUnit;
    }

    public String toDisplayString() {
        return "Day " + day + ", " + TimeGrid.toClockTime(startUnit) + " - " + TimeGrid.toClockTime(endUnit);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeSlot)) return false;
        TimeSlot timeSlot = (TimeSlot) o;
        return day == timeSlot.day && startUnit == timeSlot.startUnit && endUnit == timeSlot.endUnit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(day, startUnit, endUnit);
    }

    @Override
    public String toString() {
        return toDisplayString();
    }
}
