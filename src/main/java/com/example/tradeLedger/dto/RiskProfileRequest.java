package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Create / update a reusable risk profile - the caps a deployment runs under.
 *
 * PUT is partial: an absent field keeps its stored value.
 */
@Schema(name = "RiskProfileRequest",
        description = """
                Reusable per-deployment caps. SHARED platform master data - no owner column, so \
                a profile written here is selectable by every user.

                Distinct from /api/v1/me/risk-limits, which is one row per user holding AGGREGATE \
                caps across all their deployments. This is a named set a single deployment points \
                at. Every cap is optional; an absent one means uncapped.""")
public class RiskProfileRequest {

    @Schema(description = "Display name. Not unique - two profiles may share one.",
            example = "Conservative", maxLength = 100)
    private String name;

    @Schema(example = "Tight daily stop, small size")
    private String description;

    @Schema(description = "Stop trading this deployment after losing this much in a day.",
            example = "5000", minimum = "0")
    private BigDecimal maxDailyLoss;

    @Schema(example = "10000", minimum = "0")
    private BigDecimal maxDrawdown;

    @Schema(example = "100000", minimum = "0")
    private BigDecimal maxPositionSize;

    @Schema(example = "500000", minimum = "0")
    private BigDecimal maxTotalExposure;

    @Schema(example = "10", minimum = "1")
    private Integer maxTradesPerDay;

    @Schema(description = "Whether breaching a cap halts the deployment outright.",
            example = "true", defaultValue = "true")
    private Boolean killSwitchEnabled;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getMaxDailyLoss() { return maxDailyLoss; }
    public void setMaxDailyLoss(BigDecimal maxDailyLoss) { this.maxDailyLoss = maxDailyLoss; }

    public BigDecimal getMaxDrawdown() { return maxDrawdown; }
    public void setMaxDrawdown(BigDecimal maxDrawdown) { this.maxDrawdown = maxDrawdown; }

    public BigDecimal getMaxPositionSize() { return maxPositionSize; }
    public void setMaxPositionSize(BigDecimal maxPositionSize) { this.maxPositionSize = maxPositionSize; }

    public BigDecimal getMaxTotalExposure() { return maxTotalExposure; }
    public void setMaxTotalExposure(BigDecimal maxTotalExposure) { this.maxTotalExposure = maxTotalExposure; }

    public Integer getMaxTradesPerDay() { return maxTradesPerDay; }
    public void setMaxTradesPerDay(Integer maxTradesPerDay) { this.maxTradesPerDay = maxTradesPerDay; }

    public Boolean getKillSwitchEnabled() { return killSwitchEnabled; }
    public void setKillSwitchEnabled(Boolean killSwitchEnabled) { this.killSwitchEnabled = killSwitchEnabled; }
}
