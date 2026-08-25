package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One deployment: a saved strategy running on one broker account.
 *
 * The configuration fields here are READ THROUGH the strategy, not copied from
 * it - they are on the response so a dashboard row is one call, but they change
 * the moment the strategy is retuned, because there is only ever one copy.
 */
@Schema(name = "StrategySubscriptionResponse",
        description = """
                One deployment: a strategy running on one broker account.

                Everything from derivative down to indicators[] is READ THROUGH the strategy, not \
                copied onto this row - it is here so a dashboard row is one call, and it changes \
                the moment the strategy is retuned. Only multiplier, capitalAllocated, \
                executionMode, tradeMode and active belong to this deployment.""")
public record StrategySubscriptionResponse(

        @Schema(example = "sub00000-1111-4222-8333-444444444444")
        UUID id,

        @Schema(example = "u0000000-1111-4222-8333-444444444444")
        UUID userId,

        // ----------------------------------------------------- what it runs

        @Schema(example = "us000000-1111-4222-8333-444444444444")
        UUID userStrategyId,

        @Schema(example = "NIFTY 21/9 both sides")
        String userStrategyName,

        @Schema(description = "The template behind the strategy.",
                example = "3f1b0c7e-9a41-4c2e-9f11-2b7d5a6e8c01")
        UUID strategyId,

        @Schema(example = "EMA Averaging")
        String strategyName,

        @Schema(example = "1a2b3c4d-5e6f-4a8b-9c0d-1e2f3a4b5c6d")
        UUID symbolId,

        @Schema(example = "NIFTY")
        String symbol,

        @Schema(example = "5m")
        String candleDuration,

        @Schema(example = "OPTION", allowableValues = {"FUT", "OPTION"})
        String derivative,

        @Schema(description = "What this deployment actually trades, derived from the strategy.")
        List<StrategyLegView> legs,

        @Schema(example = "DOUBLE", allowableValues = {"FIXED", "DOUBLE", "CUMULATIVE"})
        String lotRule,

        @Schema(description = "The strategy's first-entry size. The size actually sent is "
                + "baseLot x multiplier.", example = "65")
        int baseLot,

        @Schema(example = "2")
        int averagingCount,

        @Schema(example = "1.50")
        BigDecimal slPct,

        @Schema(example = "3.00")
        BigDecimal tpPct,

        // -------------------------------------------------------- the dedup

        @Schema(description = "The shared computation this deployment feeds off. Two users "
                + "with the same hash cost the platform one computation, not two.",
                example = "sc000000-1111-4222-8333-444444444444")
        UUID sharedConfigId,

        @Schema(example = "6b1f0c9e2ad4471f9c3e5a70b8d21e4f6a9c0b3d5e7f1a2b3c4d5e6f70819a2b")
        String configHash,

        @Schema(description = "The indicator values that hash resolves from.",
                example = "{\"d\": 9, \"k\": 21}")
        Map<String, Object> signalParams,

        @Schema(description = "The concrete computations this configuration needs.",
                example = "[\"EMA Averaging(d=9,k=21)\"]")
        List<String> indicators,

        // ------------------------------------------------------ where it runs

        @Schema(example = "ta000000-1111-4222-8333-444444444444")
        UUID tradingAccountId,

        @Schema(example = "main")
        String tradingAccountName,

        @Schema(description = "The broker setup the account hangs off, for grouping.",
                example = "ub000000-1111-4222-8333-444444444444")
        UUID userBrokerId,

        @Schema(example = "My Dhan")
        String brokerLabel,

        @Schema(example = "4f5e6d7c-8b9a-4c1d-9e2f-3a4b5c6d7e8f")
        UUID riskProfileId,

        @Schema(example = "Conservative")
        String riskProfileName,

        @Schema(description = "Scales the strategy's baseLot on this account alone.",
                example = "1.00000000")
        BigDecimal multiplier,

        @Schema(example = "200000.00000000")
        BigDecimal capitalAllocated,

        @Schema(example = "FIXED_QTY",
                allowableValues = {"FIXED_QTY", "CAPITAL_PERCENT", "RISK_PERCENT"})
        String executionMode,

        @Schema(example = "paper", allowableValues = {"paper", "live"})
        String tradeMode,

        @Schema(example = "true")
        boolean active,

        @Schema(example = "2026-08-23T19:52:03.410+05:30")
        OffsetDateTime createdAt,

        @Schema(example = "2026-08-23T19:52:03.410+05:30")
        OffsetDateTime updatedAt) {
}
