package com.example.tradeLedger.dto;

import java.math.BigDecimal;

/**
 * The caller's own aggregate caps ({@code user_risk_limits}).
 *
 * Distinct from a {@link RiskProfileResponse}, which limits ONE subscription:
 * these cap the user across every subscription they own.
 */
public class UserRiskLimitRequest {

    private BigDecimal maxDailyLoss;

    private Integer maxOpenPositions;

    private BigDecimal maxTotalExposure;

    public BigDecimal getMaxDailyLoss() { return maxDailyLoss; }
    public void setMaxDailyLoss(BigDecimal maxDailyLoss) { this.maxDailyLoss = maxDailyLoss; }

    public Integer getMaxOpenPositions() { return maxOpenPositions; }
    public void setMaxOpenPositions(Integer maxOpenPositions) { this.maxOpenPositions = maxOpenPositions; }

    public BigDecimal getMaxTotalExposure() { return maxTotalExposure; }
    public void setMaxTotalExposure(BigDecimal maxTotalExposure) { this.maxTotalExposure = maxTotalExposure; }
}
