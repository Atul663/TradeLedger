package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every usage of ONE indicator inside a saved strategy, under that indicator's
 * name.
 *
 * A template's rule tree may name the same indicator more than once - two EMAs on
 * different periods, an RSI filter on each side - and each of those becomes its
 * own {@code user_strategy_indicators} row, told apart by
 * {@link UserStrategyIndicatorResponse#slot()}. Flat, that reads as a list with
 * repeating names; grouped, it reads as one section per indicator with its
 * usages under it, which is the shape an editor draws.
 *
 * The schema is hoisted to the group because it belongs to the INDICATOR, not to
 * a usage: every row in {@link #indicators} validates against the same
 * declaration. It is still repeated on each row, so a client that ignores the
 * grouping loses nothing.
 *
 * Each entry is the same complete {@link UserStrategyIndicatorResponse} the flat
 * {@code indicators} list returns - grouping changes how the rows are arranged,
 * never what a row carries.
 */
@Schema(name = "UserStrategyIndicatorGroupResponse",
        description = """
                One indicator's worth of a strategy's tuning. Grouped by indicatorName, so a \
                template that uses the same indicator twice renders as one section with two \
                usages rather than two look-alike rows.""")
public record UserStrategyIndicatorGroupResponse(

        @Schema(description = "The indicator every usage in this group runs.",
                example = "b2e4a1c8-1f3d-4e6a-9b2c-5d7e8f0a1b2c")
        UUID indicatorId,

        @Schema(description = "The group's tag - the indicator's name.", example = "EMA AVERAGING")
        String indicatorName,

        @Schema(description = "How many usages are in this group. Greater than one only when "
                + "the rule tree names the indicator more than once.", example = "1")
        int count,

        @Schema(description = "True if ANY usage in the group is enabled - what a section header "
                + "renders.", example = "true")
        boolean enabled,

        @Schema(description = "The indicator's own declaration, hoisted because every usage in "
                + "the group shares it.",
                example = "{\"k\": {\"type\":\"int\",\"min\":1,\"max\":300,\"default\":21}, "
                        + "\"d\": {\"type\":\"int\",\"min\":1,\"max\":300,\"default\":9,\"lt\":\"k\"}}")
        Map<String, Object> schema,

        @Schema(description = "By displayOrder, the same order the flat list uses.")
        List<UserStrategyIndicatorResponse> indicators) {
}
