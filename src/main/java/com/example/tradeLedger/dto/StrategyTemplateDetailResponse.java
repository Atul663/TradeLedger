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
 *     ├── indicators[]            (strategy_indicator_links)
 *     │     └── parameters[]      (indicator_parameter_links -> parameters)
 *     └── parameters[]            (strategy_parameter_links  -> parameters)
 * </pre>
 *
 * One GET gives a form everything it needs: the two parameter lists are the
 * fields to draw, and their {@code code} values are the keys a subscribe request
 * posts back. A subscribe body is one flat map, so a client concatenates the two
 * lists - the server does the same thing internally and calls the result
 * {@code strategy_param_definitions}.
 *
 * That derived set is deliberately NOT repeated here: it is the union of these
 * two lists and nothing more. {@code GET /api/v1/strategy-templates/{id}/params} exposes
 * it for anyone who wants to inspect what the engine actually validates against.
 */
public record StrategyTemplateDetailResponse(
        UUID id,
        String name,
        int version,
        String description,
        boolean system,
        boolean active,
        Map<String, Object> ruleTree,

        /**
         * The strategy's indicators, each with its own parameters nested - the
         * StrategyTemplate to Indicator to Parameter hierarchy, all of it addressable by
         * id. Read from {@code strategy_indicator_links} joined to
         * {@code indicator_parameter_links}, not from the rule tree.
         */
        List<IndicatorSummaryResponse> indicators,

        /**
         * Parameters belonging to the strategy directly rather than to one of its
         * indicators - SL, TP, quantity, the durations. From
         * {@code strategy_parameter_links}.
         */
        List<ParameterResponse> parameters,

        /**
         * Names the rule tree references that resolve to no active indicator, and
         * therefore have no id. A non-empty list means the tree is broken.
         */
        List<String> unknownIndicators,
        long instanceCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
