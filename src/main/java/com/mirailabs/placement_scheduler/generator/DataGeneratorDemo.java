package com.mirailabs.placement_scheduler.generator;

import com.mirailabs.placement_scheduler.model.Company;
import com.mirailabs.placement_scheduler.model.PriorityTier;
import com.mirailabs.placement_scheduler.model.Student;

import java.util.*;

/**
 * Run this directly (right-click -> Run As -> Java Application in Eclipse)
 * to sanity-check the generator's output BEFORE wiring it into Spring.
 * No Spring context needed - it's a plain main() method.
 */
public class DataGeneratorDemo {

    public static void main(String[] args) {
        DataGenerator generator = new DataGenerator(42L); // fixed seed = reproducible
        Dataset dataset = generator.generate();

        System.out.println("=== COMPANIES: " + dataset.getCompanies().size() + " ===");
        Map<PriorityTier, Long> tierCounts = new EnumMap<>(PriorityTier.class);
        Map<Integer, Long> dayCounts = new TreeMap<>();
        for (Company c : dataset.getCompanies()) {
            tierCounts.merge(c.getTier(), 1L, Long::sum);
            dayCounts.merge(c.getDay(), 1L, Long::sum);
        }
        System.out.println("By tier: " + tierCounts);
        System.out.println("By day: " + dayCounts);

        System.out.println();
        System.out.println("=== STUDENTS: " + dataset.getStudents().size() + " ===");

        DoubleSummaryStatistics cgpaStats = dataset.getStudents().stream()
                .mapToDouble(Student::getCgpa)
                .summaryStatistics();
        System.out.printf("CGPA -> min: %.2f, max: %.2f, avg: %.2f%n",
                cgpaStats.getMin(), cgpaStats.getMax(), cgpaStats.getAverage());

        IntSummaryStatistics shortlistStats = dataset.getStudents().stream()
                .mapToInt(s -> s.getShortlistedCompanyIds().size())
                .summaryStatistics();
        System.out.printf("Shortlists per student -> min: %d, max: %d, avg: %.2f%n",
                shortlistStats.getMin(), shortlistStats.getMax(), shortlistStats.getAverage());

        // Show the "topper effect" - students with the most shortlists.
        System.out.println();
        System.out.println("Top 5 most-shortlisted students (the overlap problem in action):");
        dataset.getStudents().stream()
                .sorted((a, b) -> b.getShortlistedCompanyIds().size() - a.getShortlistedCompanyIds().size())
                .limit(5)
                .forEach(s -> System.out.println("  " + s.getName() + " - CGPA " + s.getCgpa()
                        + " - " + s.getShortlistedCompanyIds().size() + " shortlists"));

        System.out.println();
        System.out.println("=== ROOMS: " + dataset.getRooms().size() + " ===");
    }
}
