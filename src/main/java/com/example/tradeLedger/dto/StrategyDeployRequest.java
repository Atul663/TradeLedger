package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Deploy one saved strategy onto several brokers at once.
 *
 * A target names either ONE trading account or a whole broker setup. A
 * {@code userBrokerId} fans out to every account under that setup, which is what
 * "deploy it on my Dhan" means when the login carries three sub-accounts.
 *
 * Everything outside {@code targets} is a default: a target that omits
 * {@code multiplier} takes the request one, and a target that sets it wins.
 */
@Schema(name = "StrategyDeployRequest",
        description = """
                Deploy one strategy to many brokers in one call.

                The strategy is NOT in this body - the symbol, candle, strikes, ladder and \
                indicator values all come from the saved strategy, which is what makes every \
                broker run identical maths off ONE shared computation.

                Fields outside targets[] are defaults; a target that sets the same field wins. \
                That makes "same size everywhere" one field, and per-broker sizing possible, \
                without two shapes of request.""")
public class StrategyDeployRequest {

    @Schema(description = "Required, non-empty. Each entry becomes one or more deployments.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Target> targets;

    // ------------------------------------------- defaults for every target

    @Schema(description = "Default risk profile for every target.",
            example = "4f5e6d7c-8b9a-4c1d-9e2f-3a4b5c6d7e8f")
    private UUID riskProfileId;

    @Schema(description = "Default scaler on the strategy's baseLot. 1 runs the ladder as "
            + "configured.", example = "1", defaultValue = "1")
    private BigDecimal multiplier;

    @Schema(description = "Default capital earmarked per account, for the percent-based "
            + "execution modes.", example = "200000")
    private BigDecimal capitalAllocated;

    @Schema(description = "Default execution mode.",
            example = "FIXED_QTY",
            allowableValues = {"FIXED_QTY", "CAPITAL_PERCENT", "RISK_PERCENT"},
            defaultValue = "FIXED_QTY")
    private String executionMode;

    @Schema(description = "Default trade mode. Deploy everything on paper first, then flip "
            + "one broker live with a per-target override.",
            example = "Paper", allowableValues = {"Paper", "Live"}, defaultValue = "Paper")
    private String tradeMode;

    /**
     * One destination: an account, or a broker setup and therefore all of its
     * accounts.
     */
    @Schema(name = "DeployTarget",
            description = "One destination. Send tradingAccountId for a single account, or "
                    + "userBrokerId to fan out to every account under that broker setup.")
    public static class Target {

        @Schema(description = "One account. Mutually exclusive with userBrokerId.",
                example = "ta000000-1111-4222-8333-444444444444")
        private UUID tradingAccountId;

        @Schema(description = "Every account under this broker setup.",
                example = "ub000000-1111-4222-8333-444444444444")
        private UUID userBrokerId;

        @Schema(description = "Overrides the request-level risk profile for this target.",
                example = "4f5e6d7c-8b9a-4c1d-9e2f-3a4b5c6d7e8f")
        private UUID riskProfileId;

        @Schema(description = "Overrides the request-level multiplier - run this broker at "
                + "double size.", example = "2")
        private BigDecimal multiplier;

        @Schema(example = "200000")
        private BigDecimal capitalAllocated;

        @Schema(example = "FIXED_QTY",
                allowableValues = {"FIXED_QTY", "CAPITAL_PERCENT", "RISK_PERCENT"})
        private String executionMode;

        @Schema(description = "Overrides the request-level trade mode - take one broker live "
                + "while the rest stay on paper.",
                example = "Live", allowableValues = {"Paper", "Live"})
        private String tradeMode;

        public UUID getTradingAccountId() { return tradingAccountId; }
        public void setTradingAccountId(UUID tradingAccountId) { this.tradingAccountId = tradingAccountId; }

        public UUID getUserBrokerId() { return userBrokerId; }
        public void setUserBrokerId(UUID userBrokerId) { this.userBrokerId = userBrokerId; }

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

    public List<Target> getTargets() { return targets; }
    public void setTargets(List<Target> targets) { this.targets = targets; }

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
