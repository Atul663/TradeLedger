package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;
import java.util.UUID;

/**
 * One fixed knob as a saved strategy has it: the descriptor AND the value.
 *
 * The descriptor half is {@code fixed_parameters} - label, type, bounds, what to
 * pre-fill - and the value half is the typed column on {@code user_strategies}
 * that the descriptor's {@link #name} names. Joining them here is what lets a
 * form render the whole strategy without hardcoding a single field: it walks the
 * groups, draws an input per entry from {@link #dataType} and {@link #validation},
 * and fills it from {@link #value}.
 *
 * {@link #value} is the SAME value the flat field of that name carries on
 * {@link UserStrategyResponse} - this is a second arrangement of it, not a second
 * source of truth. Writes still address the flat name.
 */
@Schema(name = "StrategyFixedParameterResponse",
        description = """
                One fixed knob of a saved strategy: its descriptor from fixed_parameters and \
                its current value from the user_strategies column of the same name. The value \
                is the same one the flat field carries - PUT still addresses the flat name.""")
public record StrategyFixedParameterResponse(

        @Schema(description = "The descriptor's id in fixed_parameters.",
                example = "fp000000-1111-4222-8333-444444444444")
        UUID id,

        @Schema(description = "The API field name of the column this describes - what a PUT "
                + "addresses.", example = "slPct")
        String name,

        @Schema(example = "SL %")
        String label,

        String description,

        @Schema(example = "decimal",
                allowableValues = {"int", "decimal", "bool", "enum", "timeframe", "text"})
        String dataType,

        @Schema(description = "signal knobs are part of the config hash; execution knobs are not.",
                example = "execution", allowableValues = {"signal", "execution"})
        String scope,

        @Schema(description = "This strategy's current value, already typed - a number for an "
                + "int or decimal, a boolean for a bool, a string otherwise. Null where the "
                + "column is unset.",
                example = "1.50")
        Object value,

        @Schema(description = "What the catalog suggests when nobody touches the knob. Text "
                + "whatever the type is - coerce it with dataType.", example = "2.5")
        String defaultValue,

        @Schema(description = "Empty when the knob is unbounded.",
                example = "{\"min\": 0, \"max\": 100}")
        Map<String, Object> validation,

        @Schema(example = "1")
        int displayOrder,

        boolean required) {
}
