package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * One fixed-knob descriptor: what the field is called, what it takes, what it
 * starts at and where it sits on the form.
 *
 * {@code validation} comes back as a map rather than a JSON string, the same way
 * an indicator's {@code paramSchema} does.
 */
@Schema(name = "FixedParameterResponse",
        description = """
                The descriptor of one FIXED knob. Its VALUE is a typed column on \
                user_strategies or user_strategy_subscriptions, read and written through those \
                APIs; this is the metadata a form renders the field from.""")
public record FixedParameterResponse(
        UUID id,
        @Schema(example = "slPct") String name,
        @Schema(example = "SL %") String label,
        String description,
        @Schema(example = "decimal",
                allowableValues = {"int", "decimal", "bool", "enum", "timeframe", "text", "symbol", "exchange"})
        String dataType,
        @Schema(example = "execution", allowableValues = {"signal", "execution"}) String scope,
        @Schema(description = "Text whatever the type is - coerce it with dataType.",
                example = "2.5")
        String defaultValue,
        @Schema(description = "Empty when the knob is unbounded.",
                example = "{\"min\": 0, \"max\": 100}")
        Map<String, Object> validation,
        @Schema(example = "Exits") String paramGroup,
        @Schema(example = "1") int displayOrder,
        boolean required,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
