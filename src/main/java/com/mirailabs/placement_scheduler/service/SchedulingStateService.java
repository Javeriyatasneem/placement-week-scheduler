package com.mirailabs.placement_scheduler.service;

import com.mirailabs.placement_scheduler.generator.DataGenerator;
import com.mirailabs.placement_scheduler.generator.Dataset;
import com.mirailabs.placement_scheduler.model.Interview;
import com.mirailabs.placement_scheduler.replan.*;
import com.mirailabs.placement_scheduler.scheduler.SchedulingEngine;
import com.mirailabs.placement_scheduler.scheduler.SchedulingResult;
import com.mirailabs.placement_scheduler.scheduler.UnscheduledEntry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the LIVE scheduling/replan state across HTTP requests. Spring
 * creates exactly one instance of this (default singleton scope), shared
 * by every request - this is what lets /api/schedule/generate initialize
 * state once, and every subsequent /api/replan/* call keep operating on
 * that SAME dataset/engine/schedule, instead of each request accidentally
 * starting fresh (which would silently lose all prior replanning).
 *
 * IMPORTANT - one piece of NEW logic that doesn't exist in ReplanEngine and
 * was never needed by the demo classes: ReplanEngine.replanForXxx() returns
 * a DIFF (repaired/unscheduled interviews), it does not update any master
 * list itself - the demos never needed a persistent master list since each
 * one only ever ran ONE disruption per JVM run. A REST API needs that
 * master list to stay correct across MANY sequential disruptions, so this
 * service applies each diff onto currentSchedule after every replan call.
 * This is new orchestration glue, not a change to ReplanEngine's verified
 * behavior.
 *
 * Uses a fixed seed (42L) for the data generator, matching every demo class
 * built so far - keeps results reproducible and comparable to everything
 * already verified.
 */
@Service
public class SchedulingStateService {

    private Dataset dataset;
    private SchedulingEngine schedulingEngine;
    private ReplanEngine replanEngine;
    private List<Interview> currentSchedule;
    private List<UnscheduledEntry> unscheduledEntries;

    public synchronized SchedulingResult generate() {
        dataset = new DataGenerator(42L).generate();
        schedulingEngine = new SchedulingEngine();
        SchedulingResult result = schedulingEngine.schedule(dataset);
        currentSchedule = new ArrayList<>(result.getScheduledInterviews());
        unscheduledEntries = result.getUnscheduledEntries();
        // Fresh ReplanEngine bound to THIS scheduling run's live occupancy
        // trackers - required every time generate() is called, since a new
        // SchedulingEngine means brand new (empty) occupancy state.
        replanEngine = new ReplanEngine(schedulingEngine);
        return result;
    }

    public synchronized List<Interview> getCurrentSchedule() {
        ensureGenerated();
        return currentSchedule;
    }

    public synchronized List<UnscheduledEntry> getUnscheduledEntries() {
        ensureGenerated();
        return unscheduledEntries;
    }

    public synchronized ReplanResult panelDrop(String panelId, int day, int unit) {
        ensureGenerated();
        PanelDropDisruption disruption = new PanelDropDisruption(panelId, day, unit);
        ReplanResult result = replanEngine.replanForPanelDrop(disruption, dataset, currentSchedule);
        applyDiff(result);
        return result;
    }

    public synchronized ReplanResult roomBlock(String roomId, int day, int unit) {
        ensureGenerated();
        RoomBlockDisruption disruption = new RoomBlockDisruption(roomId, day, unit);
        ReplanResult result = replanEngine.replanForRoomBlock(disruption, dataset, currentSchedule);
        applyDiff(result);
        return result;
    }

    public synchronized ReplanResult studentWithdrawal(String studentId, int day, int unit) {
        ensureGenerated();
        StudentWithdrawalDisruption disruption = new StudentWithdrawalDisruption(studentId, day, unit);
        ReplanResult result = replanEngine.replanForStudentWithdrawal(disruption, dataset, currentSchedule);
        applyDiff(result);
        return result;
    }

    public synchronized ReplanResult companyDelay(String companyId, int delayUntilUnit) {
        ensureGenerated();
        CompanyDelayDisruption disruption = new CompanyDelayDisruption(companyId, delayUntilUnit);
        ReplanResult result = replanEngine.replanForCompanyDelay(disruption, dataset, currentSchedule);
        applyDiff(result);
        return result;
    }

    /**
     * Merges one replan's diff into the master list: repaired interviews
     * replace their original entry, cancelled/unscheduled ones are removed
     * entirely, and everything not mentioned in the diff (the LOCKED set)
     * is left untouched by construction - we only ever touch what's in
     * repairOutcomes.
     */
    private void applyDiff(ReplanResult result) {
        for (RepairOutcome outcome : result.getRepairOutcomes()) {
            currentSchedule.remove(outcome.getOriginalInterview());
            if (outcome.isRepaired()) {
                currentSchedule.add(outcome.getNewInterview());
            }
        }
    }

    private void ensureGenerated() {
        if (dataset == null) {
            throw new IllegalStateException("No schedule has been generated yet - call POST /api/schedule/generate first");
        }
    }
}
