package com.example.tradeLedger.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A user's own strategy configuration: the shared instance it points at, plus the
 * personal knobs that never enter the config hash.
 */
public record StrategySubscriptionResponse(
        UUID id,
        UUID userId,
        UUID strategyId,
        String strategyName,
        UUID sharedConfigId,
        String configHash,
        UUID symbolId,
        String symbol,
        String timeframe,

        /** Shared, hashed, dedup-eligible. */
        Map<String, Object> signalParams,

        /** Personal, never hashed. */
        Map<String, Object> execParams,

        /** Resolved computations this config needs, e.g. ["EMA(period=9)","EMA(period=21)"]. */
        List<String> indicators,

        UUID tradingAccountId,
        String tradingAccountName,
        UUID riskProfileId,
        String riskProfileName,
        BigDecimal quantity,
        BigDecimal multiplier,
        BigDecimal lotSize,
        BigDecimal capitalAllocated,
        String executionMode,
        String tradeMode,
        boolean active,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
