package com.example.tradeLedger.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The UI's view of a user strategy: the template it came from, its indicators,
 * and every knob with the global default and the user's value side by side.
 *
 * One call renders the whole form. {@code indicators} carries the knobs that
 * belong to each indicator; {@code strategyParameters} carries the ones that
 * belong to the strategy itself - SL, TP, quantity, the durations. Each
 * {@link EffectiveParameterResponse} already holds {@code defaultValue},
 * {@code customValue}, {@code effectiveValue} and {@code overridden}, so nothing
 * has to be resolved client-side.
 */
public record UserStrategyResponse(
        UUID id,
        UUID userId,

        /** The global template this customizes. */
        UUID strategyId,
        String strategyName,
        String strategyDescription,

        /** The caller's own label and note. */
        String name,
        String description,

        UUID symbolId,
        String symbol,
        String timeframe,

        List<UserStrategyIndicatorResponse> indicators,

        /** Knobs belonging to the strategy rather than to any indicator. */
        List<EffectiveParameterResponse> strategyParameters,

        /** How many knobs the user actually changed, across both levels. */
        int overrideCount,

        /** True once symbol and timeframe are both set - i.e. subscribe would work. */
        boolean readyToSubscribe,

        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
