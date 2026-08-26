package com.mirailabs.placement_scheduler.generator;

import com.mirailabs.placement_scheduler.model.*;

import java.util.*;

/**
 * Generates a realistic placement-week dataset:
 *  - 35 companies, front-loaded with mass recruiters on Day 1
 *  - 800 students with a realistic (bell-curve) CGPA spread
 *  - 20 rooms
 *  - Shortlists: a few "topper" students get 8-10 shortlists,
 *    most students get 2-4 - this creates the overlap problem
 *    the scheduler has to deal with.
 */
public class DataGenerator {

    private static final String[] BRANCHES = {
            "CSE", "ISE", "ECE", "EEE", "MECH", "CIVIL", "AIML"
    };

    // Fictional company name pool, grouped loosely by tier flavor.
    private static final String[] MASS_RECRUITER_NAMES = {
            "NimbusTech Solutions", "Vertex InfoSystems", "BrightWave Software",
            "Optimus Consulting", "Skyline Digital", "Quantum Edge Technologies",
            "Prime Logic Systems", "Nova Consultancy", "Ironclad Software Services",
            "Horizon Business Solutions", "Zenith IT Services", "Pinnacle Softworks"
    };
    private static final String[] MID_TIER_NAMES = {
            "Cobalt Analytics", "Redwood Systems", "Aurora Data Labs",
            "Fusion Cloudworks", "Lumen Platforms", "Catalyst Software Group",
            "Meridian Tech", "Trailhead Systems", "Solstice Digital",
            "Argon Networks", "Vantage Point Software"
    };
    private static final String[] DREAM_COMPANY_NAMES = {
            "Stratos AI", "Orbital Systems", "Apex Machine Intelligence",
            "Halcyon Robotics", "Nebula Research Labs", "Ferrous Dynamics",
            "Crestline Innovations", "Obsidian Computing", "Polaris Deep Tech",
            "Nightfall Security", "Everest Cloud", "Titanium Analytics"
    };

    private final Random random;

    public DataGenerator(long seed) {
         // Fixed seed keeps the generated data consistent while testing.
        this.random = new Random(seed);
    }

    public Dataset generate() {
        List<Company> companies = generateCompanies();
        List<Student> students = generateStudents(800, companies);
        List<Room> rooms = generateRooms(20);
        return new Dataset(companies, students, rooms);
    }

    // ---------- COMPANIES ----------

    private List<Company> generateCompanies() {
        List<Company> companies = new ArrayList<>();
        int companyCounter = 1;

     // Day 1 has the highest-volume recruiters.
        companies.addAll(buildTier(companyCounter, MASS_RECRUITER_NAMES, PriorityTier.MASS_RECRUITER, 1));
        companyCounter += MASS_RECRUITER_NAMES.length;

      // Mid-tier companies are distributed between Days 2 and 3.
        List<Company> midTier = buildTier(companyCounter, MID_TIER_NAMES, PriorityTier.MID_TIER, 2);
        companyCounter += MID_TIER_NAMES.length;
        companies.addAll(midTier);

     // Dream companies are scheduled on Day 4.
        companies.addAll(buildTier(companyCounter, DREAM_COMPANY_NAMES, PriorityTier.DREAM_COMPANY, 4));

        return companies;
    }

    private List<Company> buildTier(int startIndex, String[] names, PriorityTier tier, int primaryDay) {
        List<Company> result = new ArrayList<>();
        int[] durations = {15, 30, 45};

        for (int i = 0; i < names.length; i++) {
            String id = "C" + (startIndex + i);

            // Day assignment: mid-tier companies get spread across day 2 and 3
            // instead of all piling onto one day.
            int day = primaryDay;
            if (tier == PriorityTier.MID_TIER) {
                day = (i % 2 == 0) ? 2 : 3;
            }

            double cgpaCutoff = cutoffForTier(tier);
            Set<String> allowedBranches = branchesForTier(tier);
            int duration = durations[random.nextInt(durations.length)];
            int panelCount = panelCountForTier(tier);
            int openPositions = positionsForTier(tier);

            Company company = new Company(id, names[i], day, tier, cgpaCutoff,
                    allowedBranches, duration, openPositions);

            for (int p = 1; p <= panelCount; p++) {
                company.addPanel(new Panel(id + "-P" + p, id));
            }

            result.add(company);
        }
        return result;
    }

    private double cutoffForTier(PriorityTier tier) {
        switch (tier) {
            case MASS_RECRUITER: return round1(5.5 + random.nextDouble() * 1.0);  // 5.5 - 6.5
            case MID_TIER:       return round1(6.8 + random.nextDouble() * 1.0);  // 6.8 - 7.8
            case DREAM_COMPANY:  return round1(8.0 + random.nextDouble() * 1.0);  // 8.0 - 9.0
            default:             return 6.0;
        }
    }

    private Set<String> branchesForTier(PriorityTier tier) {
        // Mass recruiters: open to everyone (null = no restriction).
        if (tier == PriorityTier.MASS_RECRUITER) {
            return null;
        }
        // Mid-tier: usually restricted to a handful of related branches.
        if (tier == PriorityTier.MID_TIER) {
            if (random.nextDouble() < 0.4) {
                return null; // some mid-tier companies are still open to all
            }
            return pickRandomBranchSubset(3);
        }
        // Dream companies: almost always CS-adjacent only.
        Set<String> pool = new HashSet<>(Arrays.asList("CSE", "ISE", "AIML", "ECE"));
        return pickRandomSubsetFrom(pool, 2);
    }

    private Set<String> pickRandomBranchSubset(int count) {
        return pickRandomSubsetFrom(new HashSet<>(Arrays.asList(BRANCHES)), count);
    }

    private Set<String> pickRandomSubsetFrom(Set<String> pool, int count) {
        List<String> list = new ArrayList<>(pool);
        Collections.shuffle(list, random);
        return new HashSet<>(list.subList(0, Math.min(count, list.size())));
    }

    private int panelCountForTier(PriorityTier tier) {
        switch (tier) {
            case MASS_RECRUITER: return 3 + random.nextInt(2); // 3-4 panels
            case MID_TIER:       return 2 + random.nextInt(2); // 2-3 panels
            case DREAM_COMPANY:  return 1 + random.nextInt(2); // 1-2 panels
            default:             return 1;
        }
    }

    private int positionsForTier(PriorityTier tier) {
        switch (tier) {
            case MASS_RECRUITER: return 40 + random.nextInt(40); // 40-80 openings
            case MID_TIER:       return 10 + random.nextInt(20); // 10-30 openings
            case DREAM_COMPANY:  return 2 + random.nextInt(6);   // 2-8 openings
            default:             return 5;
        }
    }

    // ---------- STUDENTS ----------

    private List<Student> generateStudents(int count, List<Company> companies) {
        List<Student> students = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            String id = "S" + i;
            String name = "Student" + i;
            double cgpa = generateRealisticCgpa();
            String branch = BRANCHES[random.nextInt(BRANCHES.length)];

            Student student = new Student(id, name, cgpa, branch);
            students.add(student);
        }

        // Shortlisting pass: done separately AFTER all students exist, since
        // shortlist count is influenced by cgpa (higher cgpa -> more
        // shortlists - toppers get picked by more companies in real life).
        for (Student student : students) {
            assignShortlists(student, companies);
        }

        return students;
    }

    /**
     * Bell-curve CGPA using Gaussian distribution, mean 7.2, std-dev 0.9,
     * clamped to a realistic [5.0, 9.9] range and rounded to 2 decimals.
     * This avoids the unrealistic "flat" look of pure uniform random CGPAs.
     */
    private double generateRealisticCgpa() {
        double raw = 7.2 + random.nextGaussian() * 0.9;
        double clamped = Math.max(5.0, Math.min(9.9, raw));
        return Math.round(clamped * 100.0) / 100.0;
    }
    
    /**
     * Generates the data used by the placement scheduler.
     *
     * The generated data is intentionally varied so that the scheduler
     * has to handle different company tiers, student eligibility and
     * competition for rooms and panels.
     */
    
    private void assignShortlists(Student student, List<Company> companies) {
        double scaled = (student.getCgpa() - 5.0) / (9.9 - 5.0); // 0.0 to 1.0
        int baseTarget = 2 + (int) Math.round(scaled * 7);       // 2 to 9
        int noise = random.nextInt(3) - 1;                       // -1, 0, or +1
        int target = Math.max(1, Math.min(10, baseTarget + noise));

        List<Company> eligibleMass = new ArrayList<>();
        List<Company> eligibleOther = new ArrayList<>(); // mid-tier + dream, pooled together
        for (Company c : companies) {
            if (!c.isEligible(student)) {
                continue;
            }
            if (c.getTier() == PriorityTier.MASS_RECRUITER) {
                eligibleMass.add(c);
            } else {
                eligibleOther.add(c);
            }
        }
        Collections.shuffle(eligibleMass, random);
        Collections.shuffle(eligibleOther, random);

        // Cap mass-recruiter picks at 3 max - a realistic "safety net" count,
        // not "shortlisted everywhere just because eligible."
        int massPick = Math.min(Math.min(3, target), eligibleMass.size());
        for (int i = 0; i < massPick; i++) {
            student.addShortlist(eligibleMass.get(i).getId());
        }

        // Fill the remaining quota from mid-tier/dream companies.
        int remaining = target - massPick;
        int otherPick = Math.min(remaining, eligibleOther.size());
        for (int i = 0; i < otherPick; i++) {
            student.addShortlist(eligibleOther.get(i).getId());
        }

        // Edge case: if a student wasn't eligible for enough mid/dream
        // companies to fill their quota, top up with any remaining
        // mass-recruiter slots instead of leaving them under-shortlisted.
        int stillRemaining = target - massPick - otherPick;
        if (stillRemaining > 0) {
            for (int i = massPick; i < eligibleMass.size() && stillRemaining > 0; i++) {
                student.addShortlist(eligibleMass.get(i).getId());
                stillRemaining--;
            }
        }
    }

    // ---------- ROOMS ----------

    private List<Room> generateRooms(int count) {
        List<Room> rooms = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rooms.add(new Room("R" + i));
        }
        return rooms;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
