package com.mirailabs.placement_scheduler.model;

/**
 * The time backbone for the whole scheduler.
 *
 * Instead of fixed-size slots, we divide each day into small 15-minute
 * "units". An interview occupies however many consecutive units its
 * duration needs. This lets companies with different interview lengths
 * (15 min vs 45 min) share the same timeline without special-casing.
 *
 * A "moment in time" anywhere in the system is just (day, unit) —
 * e.g. day=1, unit=4 means Day 1, 9:00 + 4*15min = 10:00 AM.
 */
public class TimeGrid {

    public static final int UNIT_MINUTES = 15;
    public static final int DAY_START_HOUR = 9;   // 9 AM
    public static final int DAY_END_HOUR = 17;    // 5 PM
    public static final int UNITS_PER_DAY = ((DAY_END_HOUR - DAY_START_HOUR) * 60) / UNIT_MINUTES; // 32
    public static final int TOTAL_DAYS = 4;

    private TimeGrid() {
        // utility class, not instantiated
    }

    /** How many time-units does an interview of this many minutes need? Rounds up. */
    public static int unitsFor(int durationMinutes) {
        return (int) Math.ceil((double) durationMinutes / UNIT_MINUTES);
    }

    /** Converts a (day, unit) into a human readable clock time string, e.g. "10:00 AM". */
    public static String toClockTime(int unit) {
        int totalMinutes = DAY_START_HOUR * 60 + unit * UNIT_MINUTES;
        int hour24 = totalMinutes / 60;
        int minute = totalMinutes % 60;
        String suffix = hour24 >= 12 ? "PM" : "AM";
        int hour12 = hour24 % 12 == 0 ? 12 : hour24 % 12;
        return String.format("%d:%02d %s", hour12, minute, suffix);
    }
}
