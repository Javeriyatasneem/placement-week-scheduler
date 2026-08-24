package com.mirailabs.placement_scheduler.replan;

/**
 * Marker interface only - lets ReplanResult hold either a PanelDropDisruption
 * or a RoomBlockDisruption without changing ReplanResult's shape or
 * behavior. Both disruption classes already have a descriptive toString();
 * no new methods needed here.
 */
public interface Disruption {
}
