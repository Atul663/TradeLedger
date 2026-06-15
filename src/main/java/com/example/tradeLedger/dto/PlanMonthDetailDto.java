package com.example.tradeLedger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PlanMonthDetailDto {

    private Long id;
    private String monthLabel;
    private Integer monthSequence;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal allocatedTarget;
    private BigDecimal manualTarget;
    private Integer tradingDaysPlanned;
    private BigDecimal achievedAmount;
    private BigDecimal netPnl;
    private Integer profitDays;
    private Integer lossDays;
    private Integer tradingDays;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMonthLabel() {
        return monthLabel;
    }

    public void setMonthLabel(String monthLabel) {
        this.monthLabel = monthLabel;
    }

    public Integer getMonthSequence() {
        return monthSequence;
    }

    public void setMonthSequence(Integer monthSequence) {
        this.monthSequence = monthSequence;
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

    public BigDecimal getAllocatedTarget() {
        return allocatedTarget;
    }

    public void setAllocatedTarget(BigDecimal allocatedTarget) {
        this.allocatedTarget = allocatedTarget;
    }

    public BigDecimal getManualTarget() {
        return manualTarget;
    }

    public void setManualTarget(BigDecimal manualTarget) {
        this.manualTarget = manualTarget;
    }

    public Integer getTradingDaysPlanned() {
        return tradingDaysPlanned;
    }

    public void setTradingDaysPlanned(Integer tradingDaysPlanned) {
        this.tradingDaysPlanned = tradingDaysPlanned;
    }

    public BigDecimal getAchievedAmount() {
        return achievedAmount;
    }

    public void setAchievedAmount(BigDecimal achievedAmount) {
        this.achievedAmount = achievedAmount;
    }

    public BigDecimal getNetPnl() {
        return netPnl;
    }

    public void setNetPnl(BigDecimal netPnl) {
        this.netPnl = netPnl;
    }

    public Integer getProfitDays() {
        return profitDays;
    }

    public void setProfitDays(Integer profitDays) {
        this.profitDays = profitDays;
    }

    public Integer getLossDays() {
        return lossDays;
    }

    public void setLossDays(Integer lossDays) {
        this.lossDays = lossDays;
    }

    public Integer getTradingDays() {
        return tradingDays;
    }

    public void setTradingDays(Integer tradingDays) {
        this.tradingDays = tradingDays;
    }
}
