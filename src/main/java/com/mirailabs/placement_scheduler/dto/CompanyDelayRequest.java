package com.mirailabs.placement_scheduler.dto;

public class CompanyDelayRequest {
    private String companyId;
    private int delayUntilUnit;

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }
    public int getDelayUntilUnit() { return delayUntilUnit; }
    public void setDelayUntilUnit(int delayUntilUnit) { this.delayUntilUnit = delayUntilUnit; }
}
