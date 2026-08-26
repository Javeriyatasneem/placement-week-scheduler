package com.mirailabs.placement_scheduler.replan;

import com.mirailabs.placement_scheduler.generator.DataGenerator;
import com.mirailabs.placement_scheduler.generator.Dataset;
import com.mirailabs.placement_scheduler.model.Interview;
import com.mirailabs.placement_scheduler.model.Student;
import com.mirailabs.placement_scheduler.scheduler.SchedulingEngine;
import com.mirailabs.placement_scheduler.scheduler.SchedulingResult;

import java.util.List;

/**
 * Run this directly (Run As -> Java Application) to test student-withdrawal
 * replanning against a freshly generated + scheduled dataset.
 */
public class StudentWithdrawalReplanDemo {

    public static void main(String[] args) {
        Dataset dataset = new DataGenerator(42L).generate();

        SchedulingEngine schedulingEngine = new SchedulingEngine();
        SchedulingResult initialResult = schedulingEngine.schedule(dataset);
        List<Interview> currentSchedule = initialResult.getScheduledInterviews();

        System.out.println("=== INITIAL SCHEDULE ===");
        System.out.println("Total scheduled interviews: " + currentSchedule.size());

        // Find a student with more than one scheduled interview, so the
        // withdrawal actually demonstrates cancelling multiple bookings
        // (potentially spanning different days).
        Student targetStudent = null;
        for (Student s : dataset.getStudents()) {
            long count = currentSchedule.stream().filter(i -> i.getStudentId().equals(s.getId())).count();
            if (count >= 2) {
                targetStudent = s;
                break;
            }
        }
        if (targetStudent == null) {
            System.out.println("No student with 2+ scheduled interviews found - try a different seed.");
            return;
        }

        // Withdraw partway through Day 1 - unit 10 (2:30 PM), so anything
        // already completed before that stays locked.
        StudentWithdrawalDisruption disruption = new StudentWithdrawalDisruption(targetStudent.getId(), 1, 10);

        System.out.println();
        System.out.println("=== DISRUPTION ===");
        System.out.println(disruption + " (" + targetStudent.getName() + ")");

        ReplanEngine replanEngine = new ReplanEngine(schedulingEngine);
        ReplanResult result = replanEngine.replanForStudentWithdrawal(disruption, dataset, currentSchedule);

        long cancelledCount = result.countByTier(RepairOutcome.Tier.CANCELLED_WITHDRAWN);

        System.out.println();
        System.out.println("=== REPLAN SUMMARY ===");
        System.out.println("Affected interviews: " + result.getRepairOutcomes().size());
        System.out.println("  Cancelled (withdrawn): " + cancelledCount);
        System.out.println("Locked interviews (untouched): " + result.getLockedInterviewCount());
        System.out.println("Students requiring notification: " + result.getStudentsToNotify().size());

        System.out.println();
        System.out.println("=== CANCELLED INTERVIEWS ===");
        for (RepairOutcome outcome : result.getRepairOutcomes()) {
            System.out.println(outcome);
            System.out.println();
        }

        System.out.println("=== STUDENTS TO NOTIFY ===");
        System.out.println(result.getStudentsToNotify());
    }
}
