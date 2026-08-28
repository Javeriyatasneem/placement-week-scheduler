package com.mirailabs.placement_scheduler.dto;

/**
 * Plain request body for POST /api/replan/panel-drop. Exists only because
 * PanelDropDisruption has no no-arg constructor for Jackson to deserialize
 * into - this DTO is the JSON-friendly shape; the controller builds the
 * real PanelDropDisruption from it manually.
 */
public class PanelDropRequest {
    private String panelId;
    private int day;
    private int unit;

    public String getPanelId() { return panelId; }
    public void setPanelId(String panelId) { this.panelId = panelId; }
    public int getDay() { return day; }
    public void setDay(int day) { this.day = day; }
    public int getUnit() { return unit; }
    public void setUnit(int unit) { this.unit = unit; }
}
