package com.example.tradeLedger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PnlPlanRequestDto {

    private Long id;
    private String planName;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal annualTarget;
    private String currency;
    private Boolean active;
    private String planType;
    private BigDecimal startingCapital;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getAnnualTarget() {
        return annualTarget;
    }

    public void setAnnualTarget(BigDecimal annualTarget) {
        this.annualTarget = annualTarget;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getPlanType() {
        return planType;
    }

    public void setPlanType(String planType) {
        this.planType = planType;
    }

    public BigDecimal getStartingCapital() {
        return startingCapital;
    }

    public void setStartingCapital(BigDecimal startingCapital) {
        this.startingCapital = startingCapital;
    }
}
