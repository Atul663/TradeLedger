package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One compute primitive and the declaration of its knobs.
 */
@Schema(name = "IndicatorResponse",
        description = "A compute primitive. paramSchema is its ENTIRE parameter declaration - "
                + "there is no parameter table behind it. usedByStrategies is computed by "
                + "scanning rule trees, and a non-empty list blocks rename and delete.")
public record IndicatorResponse(

        @Schema(example = "b2e4a1c8-1f3d-4e6a-9b2c-5d7e8f0a1b2c")
        UUID id,

        @Schema(description = "Uppercased on save; matched by exact string against rule trees.",
                example = "EMA Averaging")
        String name,

        @Schema(example = "{\"k\": {\"type\":\"int\",\"min\":1,\"max\":300,\"default\":21}, "
                + "\"d\": {\"type\":\"int\",\"min\":1,\"max\":300,\"default\":9,\"lt\":\"k\"}}")
        Map<String, Object> paramSchema,

        @Schema(example = "true")
        boolean active,

        @Schema(description = "Templates whose rule tree names this indicator. Non-empty means "
                + "rename is 409 and delete is 409.",
                example = "[\"EMA Averaging\"]")
        List<String> usedByStrategies,

        @Schema(example = "2026-08-23T19:38:11.004+05:30")
        OffsetDateTime createdAt) {
}
