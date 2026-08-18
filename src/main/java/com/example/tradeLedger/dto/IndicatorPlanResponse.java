package com.example.tradeLedger.dto;

import java.util.List;

/**
 * Proof that dedup is working: how many subscriptions the platform serves versus
 * how many indicator computations that actually costs.
 *
 * The design document's acceptance gate - three users on 9x21, 9x50 and 13x21 -
 * must report 3 subscriptions, 3 instances and 4 distinct indicators, not 6.
 */
public record IndicatorPlanResponse(
        long activeStrategySubscriptions,
        int distinctInstances,
        int distinctIndicators,
        List<String> indicators) {

    public static IndicatorPlanResponse of(long activeStrategySubscriptions, int distinctInstances, List<String> indicators) {
        return new IndicatorPlanResponse(activeStrategySubscriptions, distinctInstances, indicators.size(), indicators);
    }
}
