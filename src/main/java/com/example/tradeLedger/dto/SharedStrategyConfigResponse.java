package com.example.tradeLedger.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An immutable, content-addressed configuration.
 *
 * {@code indicators} holds the RESOLVED indicator computations this config needs
 * - {@code ["EMA(period=9)", "EMA(period=21)"]} - i.e. the rule tree's $bindings
 * substituted with this instance's signal params. Two instances that resolve to
 * the same fingerprint share the computation, which is the whole point of the
 * dedup design.
 */
public record SharedStrategyConfigResponse(
        UUID id,
        UUID strategyId,
        String strategyName,
        UUID symbolId,
        String symbol,
        String timeframe,
        Map<String, Object> signalParams,
        String configHash,
        UUID supersedesId,
        String status,
        List<String> indicators,
        long activeSubscribers,
        OffsetDateTime createdAt) {
}
