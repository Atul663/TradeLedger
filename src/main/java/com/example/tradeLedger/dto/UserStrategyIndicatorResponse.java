package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;
import java.util.UUID;

/**
 * One indicator usage inside a saved strategy: what it is, what it runs on, and
 * what it is allowed to run on.
 *
 * {@link #params} is the effective set - the user's values with the schema's
 * defaults filled in - so a form never has to resolve a fallback. {@link #schema}
 * is the indicator's own {@code param_schema}, carried along so the client can
 * render an input with the right type and bounds for a knob nobody hardcoded.
 */
@Schema(name = "UserStrategyIndicatorResponse",
        description = "One indicator and its values, with the schema those values are "
                + "validated against - everything needed to render its inputs.")
public record UserStrategyIndicatorResponse(

        @Schema(description = "The row id. The most precise way to address this usage on update.",
                example = "7c9e6f10-3b2a-4d5c-8e1f-0a9b8c7d6e5f")
        UUID id,

        @Schema(example = "b2e4a1c8-1f3d-4e6a-9b2c-5d7e8f0a1b2c")
        UUID indicatorId,

        @Schema(example = "EMA Averaging")
        String indicatorName,

        @Schema(description = "Only set when a template uses one indicator twice.", example = "null")
        String slot,

        @Schema(description = "A disabled indicator keeps its tuning and contributes nothing "
                + "to the config hash.", example = "true")
        boolean enabled,

        @Schema(example = "0")
        int displayOrder,

        @Schema(description = "Effective values - the user's, with schema defaults filled in. "
                + "Returned in canonical (sorted) order, which is what the hash is computed over.",
                example = "{\"d\": 9, \"k\": 21}")
        Map<String, Object> params,

        @Schema(description = "The indicator's own declaration: type, bounds, default, and any "
                + "gt/lt rule between its keys.",
                example = "{\"k\": {\"type\":\"int\",\"min\":1,\"max\":300,\"default\":21}, "
                        + "\"d\": {\"type\":\"int\",\"min\":1,\"max\":300,\"default\":9,\"lt\":\"k\"}}")
        Map<String, Object> schema) {
}
