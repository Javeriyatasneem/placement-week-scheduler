package com.mirailabs.placement_scheduler.scheduler;

import com.mirailabs.placement_scheduler.generator.DataGenerator;
import com.mirailabs.placement_scheduler.generator.Dataset;
import com.mirailabs.placement_scheduler.model.Interview;

import java.util.*;

/**
 * Run this directly (Run As -> Java Application) to test the scheduler
 * against generated data before wiring it into Spring/REST.
 */
public class SchedulingEngineDemo {

    public static void main(String[] args) {
        Dataset dataset = new DataGenerator(42L).generate();

        long startTime = System.currentTimeMillis();
        SchedulingResult result = new SchedulingEngine().schedule(dataset);
        long elapsed = System.currentTimeMillis() - startTime;

        result.printSummary();
        System.out.println("Time taken: " + elapsed + "ms");

        int totalShortlists = dataset.getStudents().stream()
                .mapToInt(s -> s.getShortlistedCompanyIds().size())
                .sum();
        double placementRate = 100.0 * result.getScheduledInterviews().size() / totalShortlists;
        System.out.printf("Placement rate: %.1f%% of all shortlisted interviews scheduled%n", placementRate);

        // How many students ended up with ZERO scheduled interviews at all?
        Set<String> studentsWithInterviews = new HashSet<>();
        for (Interview i : result.getScheduledInterviews()) {
            studentsWithInterviews.add(i.getStudentId());
        }
        long studentsWithShortlists = dataset.getStudents().stream()
                .filter(s -> !s.getShortlistedCompanyIds().isEmpty())
                .count();
        long studentsWithZeroInterviews = dataset.getStudents().stream()
                .filter(s -> !s.getShortlistedCompanyIds().isEmpty())
                .filter(s -> !studentsWithInterviews.contains(s.getId()))
                .count();
        System.out.println("Students with >=1 shortlist but ZERO interviews scheduled: "
                + studentsWithZeroInterviews + " / " + studentsWithShortlists);

        System.out.println();
        System.out.println("Sample of unscheduled entries (first 5):");
        result.getUnscheduledEntries().stream().limit(5).forEach(System.out::println);

        // ---- Diagnostic: demand vs capacity, per day ----
        System.out.println();
        System.out.println("=== PER-DAY BREAKDOWN ===");
        Map<String, Integer> companyIdToDay = new HashMap<>();
        for (var c : dataset.getCompanies()) {
            companyIdToDay.put(c.getId(), c.getDay());
        }

        Map<Integer, Integer> demandByDay = new TreeMap<>();
        Map<Integer, Integer> scheduledByDay = new TreeMap<>();
        for (int d = 1; d <= 4; d++) {
            demandByDay.put(d, 0);
            scheduledByDay.put(d, 0);
        }
        for (var student : dataset.getStudents()) {
            for (String companyId : student.getShortlistedCompanyIds()) {
                Integer day = companyIdToDay.get(companyId);
                if (day != null) {
                    demandByDay.merge(day, 1, Integer::sum);
                }
            }
        }
        for (Interview i : result.getScheduledInterviews()) {
            Integer day = companyIdToDay.get(i.getCompanyId());
            if (day != null) {
                scheduledByDay.merge(day, 1, Integer::sum);
            }
        }
        int roomCapacityPerDay = 20 * com.mirailabs.placement_scheduler.model.TimeGrid.UNITS_PER_DAY;
        for (int d = 1; d <= 4; d++) {
            System.out.println("Day " + d + " -> demand: " + demandByDay.get(d)
                    + ", scheduled: " + scheduledByDay.get(d)
                    + ", room-unit capacity: " + roomCapacityPerDay);
        }
    }
}
