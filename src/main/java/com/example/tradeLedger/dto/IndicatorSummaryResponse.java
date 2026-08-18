package com.example.tradeLedger.dto;

import java.util.List;
import java.util.UUID;

/**
 * An indicator as it hangs off a strategy, with its own parameters nested.
 *
 * This is the middle level of StrategyTemplate to Indicator to Parameter. {@link #id} is
 * the indicator itself, shared by every strategy that uses it; the attachment is
 * addressed by the {@code (strategyId, id)} pair rather than by the
 * {@code strategy_indicator_links} row id.
 */
public record IndicatorSummaryResponse(
        UUID id,
        String name,
        boolean active,
        List<ParameterResponse> parameters) {
}
