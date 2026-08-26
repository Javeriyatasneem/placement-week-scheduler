package com.mirailabs.placement_scheduler.replan;

/**
 * Describes a company arriving late. No interview for this company may
 * start before delayUntilUnit, on the company's (single, fixed) day.
 */
public class CompanyDelayDisruption implements Disruption {

    private final String companyId;
    private final int delayUntilUnit;

    public CompanyDelayDisruption(String companyId, int delayUntilUnit) {
        this.companyId = companyId;
        this.delayUntilUnit = delayUntilUnit;
    }

    public String getCompanyId() { return companyId; }
    public int getDelayUntilUnit() { return delayUntilUnit; }

    @Override
    public String toString() {
        return "CompanyDelayDisruption[company=" + companyId + ", no interviews before unit " + delayUntilUnit
                + " on its scheduled day]";
    }
}
