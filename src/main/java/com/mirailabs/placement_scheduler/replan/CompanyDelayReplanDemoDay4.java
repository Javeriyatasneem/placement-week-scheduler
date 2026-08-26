package com.mirailabs.placement_scheduler.replan;

import com.mirailabs.placement_scheduler.generator.DataGenerator;
import com.mirailabs.placement_scheduler.generator.Dataset;
import com.mirailabs.placement_scheduler.model.Company;
import com.mirailabs.placement_scheduler.model.Interview;
import com.mirailabs.placement_scheduler.scheduler.SchedulingEngine;
import com.mirailabs.placement_scheduler.scheduler.SchedulingResult;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Second company-delay scenario, using a Day-4 company instead of Day-1.
 * Day 4 (dream companies) runs at a small fraction of Day 1's demand (see
 * our earlier per-day audit: ~78 demand vs ~2,500+ on Day 1), so this
 * exercises the SAME replanForCompanyDelay logic - unchanged - against a
 * scenario with real spare capacity, to confirm Tier 1/2 can actually
 * succeed when the room/panel bottleneck isn't present.
 *
 * No changes to ReplanEngine - only the scenario/input selection differs
 * from CompanyDelayReplanDemo.
 */
public class CompanyDelayReplanDemoDay4 {

    public static void main(String[] args) {
        Dataset dataset = new DataGenerator(42L).generate();

        SchedulingEngine schedulingEngine = new SchedulingEngine();
        SchedulingResult initialResult = schedulingEngine.schedule(dataset);
        List<Interview> currentSchedule = initialResult.getScheduledInterviews();

        System.out.println("=== INITIAL SCHEDULE ===");
        System.out.println("Total scheduled interviews: " + currentSchedule.size());

        // Pick the Day-4 company with the MOST scheduled interviews, so the
        // demo actually has something to affect (Day 4's overall demand is
        // low, so some dream companies may have very few or zero bookings).
        Map<String, Long> day4CompanyCounts = currentSchedule.stream()
                .filter(i -> {
                    Company c = findCompany(dataset, i.getCompanyId());
                    return c != null && c.getDay() == 4;
                })
                .collect(Collectors.groupingBy(Interview::getCompanyId, Collectors.counting()));

        String targetCompanyId = day4CompanyCounts.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new IllegalStateException("No Day-4 company has any scheduled interviews"));

        Company targetCompany = findCompany(dataset, targetCompanyId);

        int earliestStart = currentSchedule.stream()
                .filter(i -> i.getCompanyId().equals(targetCompanyId))
                .mapToInt(i -> i.getTimeSlot().getStartUnit())
                .min()
                .orElseThrow();

        // Delay threshold set just past the earliest scheduled interview,
        // so at least that one is affected while later ones stay locked -
        // giving a mix, same shape as the Day-1 demo.
        int delayUntilUnit = earliestStart + 2;

        CompanyDelayDisruption disruption = new CompanyDelayDisruption(targetCompanyId, delayUntilUnit);

        System.out.println();
        System.out.println("=== DISRUPTION ===");
        System.out.println(disruption + " (company: " + targetCompany.getName()
                + ", " + day4CompanyCounts.get(targetCompanyId) + " total scheduled interviews)");

        ReplanEngine replanEngine = new ReplanEngine(schedulingEngine);
        ReplanResult result = replanEngine.replanForCompanyDelay(disruption, dataset, currentSchedule);

        long tier1Count = result.countByTier(RepairOutcome.Tier.TIER_1_SAME_SLOT_ALT_PANEL);
        long tier2Count = result.countByTier(RepairOutcome.Tier.TIER_2_NEAREST_ALTERNATIVE);
        long tier3Count = result.countByTier(RepairOutcome.Tier.TIER_3_UNSCHEDULED);

        System.out.println();
        System.out.println("=== REPLAN SUMMARY ===");
        System.out.println("Affected interviews: " + result.getRepairOutcomes().size());
        System.out.println("  Tier 1 (same panel/room, earliest feasible after delay): " + tier1Count);
        System.out.println("  Tier 2 (nearest alternative slot, at/after delay): " + tier2Count);
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

    private static Company findCompany(Dataset dataset, String companyId) {
        return dataset.getCompanies().stream()
                .filter(c -> c.getId().equals(companyId))
                .findFirst()
                .orElse(null);
    }
}
