package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * The caller's strategies for ONE template, tagged with that template's name.
 *
 * The grouping key is {@code user_strategies.strategy_id} - the template whose
 * logic every strategy in the group runs. A user typically builds several
 * customizations of the same template (one per market, one per tuning), so this
 * is the shape a list screen wants: a heading per template and its rows under it.
 *
 * The name is a FIELD rather than a JSON key, so rewording a template changes a
 * value and never the structure a client parses.
 *
 * Each entry in {@link #strategies} is the same complete
 * {@link UserStrategyResponse} the flat list returns - grouping changes how the
 * rows are arranged, never what a row carries.
 */
@Schema(name = "UserStrategyGroupResponse",
        description = """
                One template's worth of the caller's strategies. Grouped by strategyId and \
                tagged with strategyName; the rows inside are the same UserStrategyResponse the \
                flat list returns.""")
public record UserStrategyGroupResponse(

        @Schema(description = "The template every strategy in this group runs.",
                example = "3f1b0c7e-9a41-4c2e-9f11-2b7d5a6e8c01")
        UUID strategyId,

        @Schema(description = "The group's tag - the template's name.", example = "EMA Averaging")
        String strategyName,

        @Schema(example = "EMA of the highs against a shorter signal leg, traded through options "
                + "or the future, with a configurable averaging ladder.")
        String strategyDescription,

        @Schema(description = "How many of the caller's strategies are in this group, AFTER the "
                + "active filter has been applied.", example = "2")
        int count,

        @Schema(description = "Oldest first, the same order the flat list uses.")
        List<UserStrategyResponse> strategies) {
}
