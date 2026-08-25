package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A template plus everything needed to start configuring it: the rule tree, and
 * the indicators that tree names with their schemas.
 *
 * One GET is enough to draw the "new strategy" form. The instrument, strike,
 * ladder and exit fields are fixed platform concepts - typed columns on
 * {@code user_strategies} - so they are the same on every template and are not
 * described here.
 */
@Schema(name = "StrategyTemplateDetailResponse",
        description = """
                A template and the indicators its rule tree names, each with its parameter schema.

                One GET is enough to draw the "new strategy" form: indicators[].paramSchema is \
                the only part that varies per template, because the instrument, strike, ladder \
                and exit fields are fixed columns and identical everywhere - and those come \
                back too, as fixedParameters[], grouped the way the form lays them out.""")
public record StrategyTemplateDetailResponse(

        @Schema(example = "3f1b0c7e-9a41-4c2e-9f11-2b7d5a6e8c01")
        UUID id,

        @Schema(example = "EMA Averaging")
        String name,

        @Schema(example = "1")
        int version,

        @Schema(example = "EMA of the highs against a shorter signal leg, traded through options "
                + "or the future, with a configurable averaging ladder.")
        String description,

        @Schema(description = "Seeded templates are locked: PUT and DELETE return 409.",
                example = "true")
        boolean system,

        @Schema(example = "true")
        boolean active,

        @Schema(example = "{\"entry\": {\"ind\": \"EMA Averaging\", "
                + "\"params\": {\"k\": \"$k\", \"d\": \"$d\"}}}")
        Map<String, Object> ruleTree,

        @Schema(description = "The indicators the rule tree names, each with its schema.")
        List<IndicatorSummaryResponse> indicators,

        @Schema(description = "The SAME indicators, arranged one group per indicator name, with "
                + "usageCount saying how many nodes of the tree name each - which is how many "
                + "tuning rows a strategy built from this template will carry. The arrangement "
                + "a builder form walks; it matches the shape my-strategies returns.")
        List<StrategyTemplateIndicatorGroupResponse> indicatorGroups,

        @Schema(description = "The platform's fixed knobs, grouped by paramGroup - market, "
                + "instrument, sizing, exits. Descriptors only, because a template holds no "
                + "values: the same sections the saved strategy returns, in the same order, so "
                + "one form draws a blank template and a saved strategy alike.")
        List<FixedParameterGroupResponse> fixedParameters,

        @Schema(description = "Names the rule tree references that resolve to no ACTIVE "
                + "indicator. Non-empty means the tree is broken - block building on it, "
                + "creation will 400.",
                example = "[]")
        List<String> unknownIndicators,

        @Schema(description = "How many shared computations exist for this template.", example = "4")
        long instanceCount,

        @Schema(description = "How many users have built a strategy from it. Non-zero freezes "
                + "the rule tree.", example = "12")
        long strategyCount,

        @Schema(example = "2026-08-23T19:38:11.004+05:30")
        OffsetDateTime createdAt,

        @Schema(example = "2026-08-23T19:38:11.004+05:30")
        OffsetDateTime updatedAt) {
}
