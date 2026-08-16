package com.example.tradeLedger.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A strategy plus everything a dashboard needs to render its configuration form:
 * the rule tree, the knob definitions, and the indicators the rule tree refers to.
 *
 * The complete configuration hierarchy, everything in it addressable by id:
 *
 * <pre>
 *   strategy
 *     ├── indicators[]            (strategy_indicators)
 *     │     └── parameters[]      (indicator_parameters -> parameters)
 *     └── parameters[]            (strategy_parameters  -> parameters)
 * </pre>
 *
 * One GET gives a form everything it needs to render: the two parameter lists to
 * draw, and {@code params} - their flat derivation - to post back against.
 */
public record StrategyDetailResponse(
        UUID id,
        String name,
        int version,
        String description,
        boolean system,
        boolean active,
        Map<String, Object> ruleTree,

        /**
         * The strategy's indicators, each with its own parameters nested - the
         * Strategy to Indicator to Parameter hierarchy, all of it addressable by
         * id. Read from {@code strategy_indicators} joined to
         * {@code indicator_parameters}, not from the rule tree.
         */
        List<IndicatorSummaryResponse> indicators,

        /**
         * Parameters belonging to the strategy directly rather than to one of its
         * indicators - SL, TP, quantity, the durations. From
         * {@code strategy_parameters}.
         */
        List<ParameterResponse> parameters,

        /**
         * Names the rule tree references that resolve to no active indicator, and
         * therefore have no id. A non-empty list means the tree is broken.
         */
        List<String> unknownIndicators,

        /**
         * The flat knob set derived from the two lists above, in the shape the
         * subscribe endpoint validates against. Kept because a subscribe request
         * still sends one flat map; nothing here is authored directly.
         */
        List<StrategyParamDefResponse> params,
        long instanceCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
