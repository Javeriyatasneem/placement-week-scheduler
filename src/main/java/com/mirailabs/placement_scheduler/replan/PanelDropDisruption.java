package com.mirailabs.placement_scheduler.replan;

/**
 * Describes a single "panel becomes unavailable" disruption.
 *
 * disruptionDay/disruptionUnit mark the exact point in time the panel went
 * down. Interviews on this panel BEFORE that point are historical fact -
 * they're in the LOCKED set, untouched. Interviews AT or AFTER it are in
 * the AFFECTED set and need a repair attempt.
 */
public class PanelDropDisruption {

    private final String panelId;
    private final int disruptionDay;
    private final int disruptionUnit;

    public PanelDropDisruption(String panelId, int disruptionDay, int disruptionUnit) {
        this.panelId = panelId;
        this.disruptionDay = disruptionDay;
        this.disruptionUnit = disruptionUnit;
    }

    public String getPanelId() { return panelId; }
    public int getDisruptionDay() { return disruptionDay; }
    public int getDisruptionUnit() { return disruptionUnit; }

    @Override
    public String toString() {
        return "PanelDropDisruption[panel=" + panelId + ", from Day " + disruptionDay
                + " unit " + disruptionUnit + " onward]";
    }
}
