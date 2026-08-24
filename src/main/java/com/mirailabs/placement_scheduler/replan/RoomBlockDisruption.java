package com.mirailabs.placement_scheduler.replan;

/**
 * Describes a single "room becomes unavailable" disruption.
 *
 * Unlike a panel drop, a room block is DAY-SCOPED, matching Room.blockFrom():
 * the room is unavailable for the rest of THIS day only, from
 * disruptionUnit onward. It is not assumed to carry over to future days -
 * if that's needed later, blockFrom() would need to be called per affected
 * day, which is out of scope here.
 */
public class RoomBlockDisruption implements Disruption {

    private final String roomId;
    private final int disruptionDay;
    private final int disruptionUnit;

    public RoomBlockDisruption(String roomId, int disruptionDay, int disruptionUnit) {
        this.roomId = roomId;
        this.disruptionDay = disruptionDay;
        this.disruptionUnit = disruptionUnit;
    }

    public String getRoomId() { return roomId; }
    public int getDisruptionDay() { return disruptionDay; }
    public int getDisruptionUnit() { return disruptionUnit; }

    @Override
    public String toString() {
        return "RoomBlockDisruption[room=" + roomId + ", Day " + disruptionDay
                + " unit " + disruptionUnit + " onward (rest of that day only)]";
    }
}
