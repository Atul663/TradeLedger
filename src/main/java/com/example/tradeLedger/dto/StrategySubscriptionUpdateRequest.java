package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Partial update of one deployment. Every field is optional; only what is present
 * changes.
 *
 * The strategy is not editable from here. Retuning the indicators, moving the
 * strikes or changing the ladder is a change to the strategy - one PUT on
 * {@code /api/v1/my-strategies/{id}} that every broker running it picks up at
 * once.
 */
@Schema(name = "StrategySubscriptionUpdateRequest",
        description = """
                Change how THIS account runs the strategy: its size, its risk profile, paper or \
                live, paused or not. Every field is optional.

                The strategy itself is not editable here - retuning is one PUT on \
                /api/v1/my-strategies/{id}, which every broker running it picks up at once. \
                There is deliberately no way to fork one broker's configuration.""")
public class StrategySubscriptionUpdateRequest {

    @Schema(description = "Scales the strategy's baseLot on this account.", example = "2")
    private BigDecimal multiplier;

    @Schema(example = "500000")
    private BigDecimal capitalAllocated;

    @Schema(example = "FIXED_QTY",
            allowableValues = {"FIXED_QTY", "CAPITAL_PERCENT", "RISK_PERCENT"})
    private String executionMode;

    @Schema(description = "One broker can go live while the rest stay on paper.",
            example = "Live", allowableValues = {"Paper", "Live"})
    private String tradeMode;

    @Schema(example = "4f5e6d7c-8b9a-4c1d-9e2f-3a4b5c6d7e8f")
    private UUID riskProfileId;

    @Schema(description = "Pause this broker without touching the strategy or the other "
            + "deployments. The shared computation is retired once its last active "
            + "deployment pauses, and revived when one resumes.", example = "false")
    private Boolean active;

    public BigDecimal getMultiplier() { return multiplier; }
    public void setMultiplier(BigDecimal multiplier) { this.multiplier = multiplier; }

    public BigDecimal getCapitalAllocated() { return capitalAllocated; }
    public void setCapitalAllocated(BigDecimal capitalAllocated) { this.capitalAllocated = capitalAllocated; }

    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }

    public String getTradeMode() { return tradeMode; }
    public void setTradeMode(String tradeMode) { this.tradeMode = tradeMode; }

    public UUID getRiskProfileId() { return riskProfileId; }
    public void setRiskProfileId(UUID riskProfileId) { this.riskProfileId = riskProfileId; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
