package com.example.tradeLedger.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The bot's view of a user strategy: one call, everything resolved, no fallback
 * logic left for the caller.
 *
 * {@code indicators} carries each usage with its knobs already collapsed to
 * {@code code -> effective value}. {@code signalParams} and {@code execParams} are
 * the same values split the way the execution path consumes them - signal scope
 * is what a shared strategy config is hashed from, execution scope is personal to
 * the subscription - so the bot can hand them straight on without re-deriving the
 * split.
 */
public record UserStrategyRuntimeResponse(
        UUID userStrategyId,
        UUID userId,
        UUID strategyId,
        String strategyName,

        /** The template's rule tree, so the bot knows how the knobs wire together. */
        String ruleTree,

        UUID symbolId,
        String symbol,
        String timeframe,
        boolean active,

        List<Indicator> indicators,

        /** Shared scope, resolved. */
        Map<String, Object> signalParams,

        /** Personal scope, resolved. */
        Map<String, Object> execParams) {

    /** One indicator usage and the values it runs with. */
    public record Indicator(
            UUID indicatorId,
            String name,
            String slot,
            Map<String, String> params) {
    }
}
