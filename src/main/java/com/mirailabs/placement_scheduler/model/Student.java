package com.mirailabs.placement_scheduler.model;

import java.util.ArrayList;
import java.util.List;

public class Student {

    private final String id;
    private final String name;
    private final double cgpa;
    private final String branch;
    private final List<String> shortlistedCompanyIds = new ArrayList<>();
    private boolean withdrawn = false; // set true if student withdraws mid-day (disruption type)

    public Student(String id, String name, double cgpa, String branch) {
        this.id = id;
        this.name = name;
        this.cgpa = cgpa;
        this.branch = branch;
    }

    public void addShortlist(String companyId) {
        shortlistedCompanyIds.add(companyId);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getCgpa() { return cgpa; }
    public String getBranch() { return branch; }
    public List<String> getShortlistedCompanyIds() { return shortlistedCompanyIds; }
    public boolean isWithdrawn() { return withdrawn; }
    public void setWithdrawn(boolean withdrawn) { this.withdrawn = withdrawn; }

    @Override
    public String toString() {
        return name + " (CGPA " + cgpa + ", " + branch + ", " + shortlistedCompanyIds.size() + " shortlists)";
    }
}
