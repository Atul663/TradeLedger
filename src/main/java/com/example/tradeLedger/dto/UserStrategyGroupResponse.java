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
                tagged with the template's name, description, system flag and instance count.

                The rows inside are the same UserStrategyResponse the flat list returns - the \
                SAME objects, from the same mapper - so everything a flat row carries is here \
                too: legs[], indicators[], indicatorGroups[], fixedParameters[], configHash, \
                deployable. Grouping rearranges rows; it never edits one.""")
public record UserStrategyGroupResponse(

        @Schema(description = "The template every strategy in this group runs.",
                example = "3f1b0c7e-9a41-4c2e-9f11-2b7d5a6e8c01")
        UUID strategyId,

        @Schema(description = "The group's tag - the template's name.", example = "EMA Averaging")
        String strategyName,

        @Schema(example = "EMA of the highs against a shorter signal leg, traded through options "
                + "or the future, with a configurable averaging ladder.")
        String strategyDescription,

        @Schema(description = "Whether the template this group is tagged with is a seeded "
                + "platform one - the same flag every row inside carries as strategySystem.",
                example = "true")
        boolean strategySystem,

        @Schema(description = "How many of the caller's strategies are in this group, AFTER the "
                + "active filter has been applied.", example = "2")
        int count,

        @Schema(description = "How many shared computations exist for this template, across ALL "
                + "users - the same number the template catalog reports. A platform-wide fact "
                + "about the template, not a fact about the caller's rows.", example = "4")
        long instanceCount,

        @Schema(description = "Oldest first, the same order the flat list uses. Each is a "
                + "COMPLETE UserStrategyResponse - identical, field for field, to what "
                + "GET /api/v1/my-strategies returns for the same strategy.")
        List<UserStrategyResponse> strategies) {
}
