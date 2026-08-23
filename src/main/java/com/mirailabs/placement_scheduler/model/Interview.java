package com.mirailabs.placement_scheduler.model;

import java.util.UUID;

/**
 * The actual thing we are scheduling: one student, interviewed by one panel
 * (belonging to one company), in one room, during one time slot.
 *
 * This is the "puzzle piece" - every hard constraint in the system
 * (no double-booked student/room/panel) is really just "no two Interviews
 * share the same student/room/panel with overlapping TimeSlots".
 */
public class Interview {

    private final String id;
    private final String studentId;
    private final String companyId;
    private final String panelId;
    private final String roomId;
    private final TimeSlot timeSlot;

    public Interview(String studentId, String companyId, String panelId, String roomId, TimeSlot timeSlot) {
        this.id = UUID.randomUUID().toString();
        this.studentId = studentId;
        this.companyId = companyId;
        this.panelId = panelId;
        this.roomId = roomId;
        this.timeSlot = timeSlot;
    }

    public String getId() { return id; }
    public String getStudentId() { return studentId; }
    public String getCompanyId() { return companyId; }
    public String getPanelId() { return panelId; }
    public String getRoomId() { return roomId; }
    public TimeSlot getTimeSlot() { return timeSlot; }

    @Override
    public String toString() {
        return "Interview[student=" + studentId + ", company=" + companyId +
                ", panel=" + panelId + ", room=" + roomId + ", " + timeSlot + "]";
    }
}
