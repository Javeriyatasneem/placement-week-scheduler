package com.mirailabs.placement_scheduler.scheduler;

import com.mirailabs.placement_scheduler.model.TimeSlot;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks which (entityId, day, unit) combinations are already booked.
 * Used for students, panels, and rooms alike - all three need the same
 * "am I free during this range of units" check.
 *
 * This is the thing that actually enforces "no double booking" - every
 * placement decision the scheduler makes goes through here first.
 */
public class OccupancyTracker {

    private final Set<String> occupied = new HashSet<>();

    /** True if entityId is completely free for every unit in the slot. */
    public boolean isFree(String entityId, TimeSlot slot) {
        for (int unit = slot.getStartUnit(); unit < slot.getEndUnit(); unit++) {
            if (occupied.contains(key(entityId, slot.getDay(), unit))) {
                return false;
            }
        }
        return true;
    }

    /** Marks entityId as busy for every unit in the slot. Call only after confirming isFree(). */
    public void occupy(String entityId, TimeSlot slot) {
        for (int unit = slot.getStartUnit(); unit < slot.getEndUnit(); unit++) {
            occupied.add(key(entityId, slot.getDay(), unit));
        }
    }

    /** Frees up entityId for every unit in the slot - used when we cancel/undo an interview during replanning. */
    public void release(String entityId, TimeSlot slot) {
        for (int unit = slot.getStartUnit(); unit < slot.getEndUnit(); unit++) {
            occupied.remove(key(entityId, slot.getDay(), unit));
        }
    }

    private String key(String entityId, int day, int unit) {
        return entityId + "|" + day + "|" + unit;
    }
}
