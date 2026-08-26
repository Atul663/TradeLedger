package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Deploy one saved strategy onto one trading account.
 *
 * No parameters, no symbol, no timeframe, no strikes. All of that is the
 * strategy, and the strategy is one foreign key away - so a deployment cannot
 * drift from the configuration it claims to be running, and retuning the strategy
 * moves every broker at once.
 */
@Schema(name = "StrategySubscriptionRequest",
        description = """
                Deploy one strategy onto ONE account.

                No parameters, symbol, candle or strikes here - all of that is the strategy, one \
                foreign key away, so a deployment can never drift from what it claims to run. \
                What IS here is the only thing that differs per account: how much, under whose \
                risk profile, and paper or live.

                To reach several accounts in one call use POST /api/v1/my-strategies/{id}/deploy.""")
public class StrategySubscriptionRequest {

    @Schema(description = "The saved strategy to deploy.",
            example = "us000000-1111-4222-8333-444444444444",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID userStrategyId;

    @Schema(description = "The account to deploy it on. Must belong to the caller.",
            example = "ta000000-1111-4222-8333-444444444444",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID tradingAccountId;

    @Schema(example = "4f5e6d7c-8b9a-4c1d-9e2f-3a4b5c6d7e8f")
    private UUID riskProfileId;

    @Schema(description = "Scales the strategy's baseLot on this account alone.",
            example = "1", defaultValue = "1")
    private BigDecimal multiplier;

    @Schema(example = "200000")
    private BigDecimal capitalAllocated;

    @Schema(example = "FIXED_QTY",
            allowableValues = {"FIXED_QTY", "CAPITAL_PERCENT", "RISK_PERCENT"},
            defaultValue = "FIXED_QTY")
    private String executionMode;

    @Schema(example = "Paper", allowableValues = {"Paper", "Live"}, defaultValue = "Paper")
    private String tradeMode;

    public UUID getUserStrategyId() { return userStrategyId; }
    public void setUserStrategyId(UUID userStrategyId) { this.userStrategyId = userStrategyId; }

    public UUID getTradingAccountId() { return tradingAccountId; }
    public void setTradingAccountId(UUID tradingAccountId) { this.tradingAccountId = tradingAccountId; }

    public UUID getRiskProfileId() { return riskProfileId; }
    public void setRiskProfileId(UUID riskProfileId) { this.riskProfileId = riskProfileId; }

    public BigDecimal getMultiplier() { return multiplier; }
    public void setMultiplier(BigDecimal multiplier) { this.multiplier = multiplier; }

    public BigDecimal getCapitalAllocated() { return capitalAllocated; }
    public void setCapitalAllocated(BigDecimal capitalAllocated) { this.capitalAllocated = capitalAllocated; }

    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }

    public String getTradeMode() { return tradeMode; }
    public void setTradeMode(String tradeMode) { this.tradeMode = tradeMode; }
}
