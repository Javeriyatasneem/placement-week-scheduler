package com.mirailabs.placement_scheduler.replan;

import com.mirailabs.placement_scheduler.generator.DataGenerator;
import com.mirailabs.placement_scheduler.generator.Dataset;
import com.mirailabs.placement_scheduler.model.Company;
import com.mirailabs.placement_scheduler.model.Interview;
import com.mirailabs.placement_scheduler.model.Panel;
import com.mirailabs.placement_scheduler.scheduler.SchedulingEngine;
import com.mirailabs.placement_scheduler.scheduler.SchedulingResult;

import java.util.List;

/**
 * Run this directly (Run As -> Java Application) to test panel-drop
 * replanning against a freshly generated + scheduled dataset.
 */
public class ReplanEngineDemo {

    public static void main(String[] args) {
        Dataset dataset = new DataGenerator(42L).generate();

        SchedulingEngine schedulingEngine = new SchedulingEngine();
        SchedulingResult initialResult = schedulingEngine.schedule(dataset);
        List<Interview> currentSchedule = initialResult.getScheduledInterviews();

        System.out.println("=== INITIAL SCHEDULE ===");
        System.out.println("Total scheduled interviews: " + currentSchedule.size());

        // Pick a panel to drop: the first panel belonging to a Day-1 mass
        // recruiter, so we know it'll actually have interviews on it.
        Company targetCompany = dataset.getCompanies().stream()
                .filter(c -> c.getDay() == 1)
                .findFirst()
                .orElseThrow();
        Panel targetPanel = targetCompany.getPanels().get(0);

        // Disruption takes effect partway through the day (unit 10 = 2.5
        // hours in) so we get a realistic mix of LOCKED (already-happened)
        // and AFFECTED (still-pending) interviews on this panel.
        PanelDropDisruption disruption = new PanelDropDisruption(targetPanel.getId(), 1, 10);

        System.out.println();
        System.out.println("=== DISRUPTION ===");
        System.out.println(disruption + " (company: " + targetCompany.getName() + ")");

        ReplanEngine replanEngine = new ReplanEngine(schedulingEngine);
        ReplanResult result = replanEngine.replanForPanelDrop(disruption, dataset, currentSchedule);

        long tier1Count = result.countByTier(RepairOutcome.Tier.TIER_1_SAME_SLOT_ALT_PANEL);
        long tier2Count = result.countByTier(RepairOutcome.Tier.TIER_2_NEAREST_ALTERNATIVE);
        long tier3Count = result.countByTier(RepairOutcome.Tier.TIER_3_UNSCHEDULED);

        System.out.println();
        System.out.println("=== REPLAN SUMMARY ===");
        System.out.println("Affected interviews: " + result.getRepairOutcomes().size());
        System.out.println("  Tier 1 (same slot, alt panel): " + tier1Count);
        System.out.println("  Tier 2 (nearest alternative slot): " + tier2Count);
        System.out.println("  Tier 3 (unscheduled): " + tier3Count);
        System.out.println("Locked interviews (untouched): " + result.getLockedInterviewCount());
        System.out.println("Students requiring notification: " + result.getStudentsToNotify().size());

        System.out.println();
        System.out.println("=== OLD -> NEW ASSIGNMENTS ===");
        for (RepairOutcome outcome : result.getRepairOutcomes()) {
            System.out.println(outcome);
            System.out.println();
        }

        System.out.println("=== STUDENTS TO NOTIFY ===");
        System.out.println(result.getStudentsToNotify());
    }
}
