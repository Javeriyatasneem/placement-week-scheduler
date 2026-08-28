package com.mirailabs.placement_scheduler.dto;

public class StudentWithdrawalRequest {
    private String studentId;
    private int day;
    private int unit;

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public int getDay() { return day; }
    public void setDay(int day) { this.day = day; }
    public int getUnit() { return unit; }
    public void setUnit(int unit) { this.unit = unit; }
}
