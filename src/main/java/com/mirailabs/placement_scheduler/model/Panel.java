package com.mirailabs.placement_scheduler.model;

public class Panel {

    private final String id;
    private final String companyId;
    private boolean active = true; // set false when a panel "drops out" mid-day (disruption type)

    public Panel(String id, String companyId) {
        this.id = id;
        this.companyId = companyId;
    }

    public String getId() { return id; }
    public String getCompanyId() { return companyId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String toString() {
        return "Panel[" + id + " @ " + companyId + (active ? "" : " - DROPPED") + "]";
    }
}
