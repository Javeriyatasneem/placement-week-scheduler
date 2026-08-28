package com.mirailabs.placement_scheduler.dto;

public class RoomBlockRequest {
    private String roomId;
    private int day;
    private int unit;

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public int getDay() { return day; }
    public void setDay(int day) { this.day = day; }
    public int getUnit() { return unit; }
    public void setUnit(int unit) { this.unit = unit; }
}
