package com.mirailabs.placement_scheduler.model;

import java.util.HashSet;
import java.util.Set;

public class Room {

    private final String id;
    // (day, unit) pairs where this room is blocked off (e.g. became unavailable mid-day)
    private final Set<String> blockedUnits = new HashSet<>();

    public Room(String id) {
        this.id = id;
    }

    public String getId() { return id; }

    /** Marks this room unavailable for the rest of a given day, from a given unit onward. */
    public void blockFrom(int day, int fromUnit) {
        for (int u = fromUnit; u < TimeGrid.UNITS_PER_DAY; u++) {
            blockedUnits.add(key(day, u));
        }
    }

    public boolean isBlocked(int day, int unit) {
        return blockedUnits.contains(key(day, unit));
    }

    private String key(int day, int unit) {
        return day + ":" + unit;
    }

    @Override
    public String toString() {
        return "Room[" + id + "]";
    }
}
