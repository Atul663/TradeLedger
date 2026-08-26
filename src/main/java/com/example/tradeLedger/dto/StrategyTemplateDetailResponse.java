package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A template plus everything needed to start configuring it: the indicators its
 * rule tree names, with their schemas.
 *
 * <b>Neither the rule tree nor the fixed knobs are here.</b> The tree is the
 * platform's own logic and a form has nothing to do with it - what a form needs
 * is what the tree RESOLVES to, which is {@link #indicators} and
 * {@link #indicatorGroups}. The fixed knobs describe columns on
 * {@code user_strategies}, so they are identical on every template and come from
 * {@code GET /api/v1/fixed-parameters} once, rather than being repeated inside
 * every entry of a list.
 *
 * Both used to be carried here and both were the cost of the list endpoint: the
 * knobs alone re-read the whole symbols table once per template to fill the
 * instrument select.
 */
@Schema(name = "StrategyTemplateDetailResponse",
        description = """
                A template and the indicators its rule tree names, each with its parameter schema.

                indicators[].paramSchema is the only part of a builder form that varies per \
                template. The instrument, strike, ladder and exit fields are fixed columns, \
                identical everywhere - fetch them ONCE from /api/v1/fixed-parameters rather than \
                per template.

                The rule tree itself is not returned: it is platform logic, and what a form \
                needs from it is already resolved into indicators[] and indicatorGroups[].""")
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

        @Schema(description = "The indicators the rule tree names, each with its schema.")
        List<IndicatorSummaryResponse> indicators,

        @Schema(description = "The SAME indicators, arranged one group per indicator name, with "
                + "usageCount saying how many nodes of the tree name each - which is how many "
                + "tuning rows a strategy built from this template will carry. The arrangement "
                + "a builder form walks; it matches the shape my-strategies returns.")
        List<StrategyTemplateIndicatorGroupResponse> indicatorGroups,

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
