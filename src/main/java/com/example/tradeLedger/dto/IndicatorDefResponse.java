package com.example.tradeLedger.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An indicator primitive and its parameter schema.
 *
 * {@code usedByStrategies} closes the loop on the indicator-to-strategy
 * relationship: the names of the strategies whose rule tree references this
 * indicator. It is also what blocks a destructive delete.
 */
public record IndicatorDefResponse(
        UUID id,
        String name,
        Map<String, Object> paramSchema,
        boolean active,
        List<String> usedByStrategies,
        OffsetDateTime createdAt) {
}
