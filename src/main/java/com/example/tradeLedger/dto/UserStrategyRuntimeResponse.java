package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The bot view of a saved strategy: everything resolved, nothing left to look up.
 *
 * The same rows as {@link UserStrategyResponse}, shaped for the thing that has to
 * place an order rather than the thing that has to draw a form.
 */
@Schema(name = "UserStrategyRuntimeResponse",
        description = """
                The BOT view of a strategy: legs resolved to what they trade, indicator values \
                coerced to their declared types, and signalParams exactly as they were hashed.

                Same rows as GET /{id}, shaped for something that has to place an order rather \
                than draw a form. Note ruleTree is a JSON STRING here (passed straight through), \
                unlike the template endpoint where it is a parsed object.""")
public record UserStrategyRuntimeResponse(

        @Schema(example = "us000000-1111-4222-8333-444444444444")
        UUID userStrategyId,

        @Schema(example = "u0000000-1111-4222-8333-444444444444")
        UUID userId,

        @Schema(example = "3f1b0c7e-9a41-4c2e-9f11-2b7d5a6e8c01")
        UUID strategyId,

        @Schema(example = "EMA Averaging")
        String strategyName,

        @Schema(description = "The template's rule tree, as a JSON string, so the bot knows how "
                + "the values wire together.",
                example = "{\"entry\":{\"ind\":\"EMA Averaging\",\"params\":{\"k\":\"$k\",\"d\":\"$d\"}}}")
        String ruleTree,

        @Schema(example = "1a2b3c4d-5e6f-4a8b-9c0d-1e2f3a4b5c6d")
        UUID symbolId,

        @Schema(description = "The UNDERLYING the indicators run on.", example = "NIFTY")
        String symbol,

        @Schema(description = "The candle the signal is evaluated on.", example = "5m")
        String candleDuration,

        @Schema(description = "How often to re-check inside that candle.", example = "5m")
        String triggerDuration,

        @Schema(example = "true")
        boolean active,

        List<Indicator> indicators,

        @Schema(example = "OPTION", allowableValues = {"FUT", "OPTION"})
        String derivative,

        @Schema(description = "What to trade when the signal fires, in the order to place it.")
        List<StrategyLegView> legs,

        @Schema(description = "How to size each entry in the averaging ladder. The engine "
                + "applies it; the platform only records the choice.",
                example = "DOUBLE", allowableValues = {"FIXED", "DOUBLE", "CUMULATIVE"})
        String lotRule,

        @Schema(example = "65")
        int baseLot,

        @Schema(example = "2")
        int averagingCount,

        @Schema(example = "1.50")
        BigDecimal slPct,

        @Schema(example = "3.00")
        BigDecimal tpPct,

        @Schema(description = "The union of every ENABLED indicator's values, canonically "
                + "ordered - the exact input the config hash is computed over.",
                example = "{\"d\": 9, \"k\": 21}")
        Map<String, Object> signalParams,

        @Schema(example = "sc000000-1111-4222-8333-444444444444")
        UUID sharedConfigId,

        @Schema(example = "6b1f0c9e2ad4471f9c3e5a70b8d21e4f6a9c0b3d5e7f1a2b3c4d5e6f70819a2b")
        String configHash) {

    /** One indicator usage and the values it runs with. */
    @Schema(name = "RuntimeIndicator", description = "One indicator and the values it runs with.")
    public record Indicator(

            @Schema(example = "b2e4a1c8-1f3d-4e6a-9b2c-5d7e8f0a1b2c")
            UUID indicatorId,

            @Schema(example = "EMA Averaging")
            String name,

            @Schema(description = "Only set when a template uses one indicator twice.", example = "null")
            String slot,

            @Schema(description = "Coerced to the declared types.", example = "{\"d\": 9, \"k\": 21}")
            Map<String, Object> params) {
    }
}
