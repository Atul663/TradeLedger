package com.example.tradeLedger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PlanDayDetailDto {

    private LocalDate date;
    private BigDecimal targetAmount;
    private BigDecimal achievedAmount;
    private BigDecimal netPnl;
    private boolean isTradingDay;
    private String status;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = targetAmount;
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

    public boolean isTradingDay() {
        return isTradingDay;
    }

    public void setTradingDay(boolean tradingDay) {
        isTradingDay = tradingDay;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
