package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The indicators a template's rule tree names, under one name.
 *
 * The same arrangement {@link UserStrategyIndicatorGroupResponse} gives a saved
 * strategy, so a builder form walks one shape whether it is drawing a blank
 * template or an existing strategy. A template's tree resolves to DISTINCT names,
 * so a group here normally holds exactly one entry; {@link #usageCount} is the
 * number that varies - how many nodes of the tree name this indicator, which is
 * how many rows a strategy built from it will carry.
 */
@Schema(name = "StrategyTemplateIndicatorGroupResponse",
        description = """
                One indicator a template's rule tree names, with its schema. usageCount is how \
                many nodes of the tree name it - that is how many tuning rows a strategy built \
                from this template gets.""")
public record StrategyTemplateIndicatorGroupResponse(

        @Schema(description = "The group's tag - the indicator's name, exactly as the rule tree "
                + "spells it.", example = "EMA Averaging")
        String indicatorName,

        @Schema(description = "How many nodes of the rule tree name this indicator.", example = "1")
        int usageCount,

        @Schema(description = "How many resolved indicators are in this group.", example = "1")
        int count,

        @Schema(description = "The resolved indicator(s) with their parameter schemas.")
        List<IndicatorSummaryResponse> indicators) {
}
