package com.example.tradeLedger.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A strategy plus everything a dashboard needs to render its configuration form:
 * the rule tree, the knob definitions, and the indicators the rule tree refers to.
 *
 * {@code indicators} is the strategy-to-indicator relationship made explicit -
 * the distinct {@code indicator_defs.name} values reachable from the rule tree.
 * {@code indicatorIds} is the same relationship keyed by id, read from the
 * {@code strategy_indicators} rows written when the strategy was saved; the two
 * lists are in the same order, so index {@code i} of one names index {@code i}
 * of the other. {@code unknownIndicators} lists any name the rule tree
 * references that has no matching (active) row - those have no id because there
 * is no row to point at, which is how a broken rule tree surfaces before a user
 * ever subscribes to it.
 */
public record StrategyDetailResponse(
        UUID id,
        String name,
        int version,
        String description,
        boolean system,
        boolean active,
        Map<String, Object> ruleTree,
        List<String> indicators,
        List<UUID> indicatorIds,
        List<String> unknownIndicators,
        List<StrategyParamDefResponse> params,
        long instanceCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
