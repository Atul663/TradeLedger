package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A saved strategy, in full: one call renders the whole editor.
 *
 * The configuration arrives as the columns it is stored in - the same names the
 * request takes, so a round trip is edit-one-field-and-PUT-it-back. {@link #legs}
 * is the same instrument choice DERIVED for display, so a summary line does not
 * have to reimplement "CE OTM1" from three fields.
 */
@Schema(name = "UserStrategyResponse",
        description = """
                One saved strategy, complete. The ce*/pe* fields are the EDITABLE form - the same \
                names the request takes - while legs[] is the same choice derived for display. \
                Each indicator carries both its current values and its schema, so the form can \
                render an input for a knob nobody hardcoded.""")
public record UserStrategyResponse(

        @Schema(example = "us000000-1111-4222-8333-444444444444")
        UUID id,

        @Schema(example = "u0000000-1111-4222-8333-444444444444")
        UUID userId,

        @Schema(description = "The template whose logic this runs.",
                example = "3f1b0c7e-9a41-4c2e-9f11-2b7d5a6e8c01")
        UUID strategyId,

        @Schema(example = "EMA Averaging")
        String strategyName,

        @Schema(example = "EMA of the highs against a shorter signal leg, traded through options "
                + "or the future, with a configurable averaging ladder.")
        String strategyDescription,

        @Schema(description = "The caller's own label.", example = "NIFTY 21/9 both sides")
        String name,

        @Schema(example = "Sheet block 1")
        String description,

        // ------------------------------------------------------------- market

        @Schema(example = "1a2b3c4d-5e6f-4a8b-9c0d-1e2f3a4b5c6d")
        UUID symbolId,

        @Schema(example = "NIFTY")
        String symbol,

        @Schema(description = "The sheet's INDEX-vs-STOCK cell, read off the symbol.",
                example = "index", allowableValues = {"spot", "future", "option", "index"})
        String instrumentType,

        @Schema(example = "NSE")
        String exchangeCode,

        @Schema(description = "The candle the signal is evaluated on. Hashed.", example = "5m")
        String candleDuration,

        @Schema(description = "How often to re-check inside that candle. Never hashed.", example = "5m")
        String triggerDuration,

        // --------------------------------------------------------- instrument

        @Schema(example = "OPTION", allowableValues = {"FUT", "OPTION"})
        String derivative,

        @Schema(example = "true")
        boolean ceEnabled,

        @Schema(example = "OTM", allowableValues = {"ATM", "ITM", "OTM"})
        String ceMoneyness,

        @Schema(example = "1")
        int ceStrikeOffset,

        @Schema(example = "true")
        boolean peEnabled,

        @Schema(example = "OTM", allowableValues = {"ATM", "ITM", "OTM"})
        String peMoneyness,

        @Schema(example = "1")
        int peStrikeOffset,

        @Schema(description = "The same instrument choice, derived: what this strategy actually "
                + "trades. Read-only - writes address the ce*/pe* fields by name. A FUT strategy "
                + "returns a single FUT leg.")
        List<StrategyLegView> legs,

        // ------------------------------------------------------------- sizing

        @Schema(example = "DOUBLE", allowableValues = {"FIXED", "DOUBLE", "CUMULATIVE"})
        String lotRule,

        @Schema(example = "65")
        int baseLot,

        @Schema(example = "2")
        int averagingCount,

        @Schema(example = "1.50")
        BigDecimal slPct,

        @Schema(example = "3.00")
        BigDecimal tpPct,

        // --------------------------------------------------------- indicators

        List<UserStrategyIndicatorResponse> indicators,

        // ------------------------------------------------------------ runtime

        @Schema(description = "The shared computation this resolved to; null until the market is set.",
                example = "sc000000-1111-4222-8333-444444444444")
        UUID sharedConfigId,

        @Schema(description = "Changes whenever an indicator value, the symbol or the candle "
                + "changes. Two strategies with the same hash share one computation.",
                example = "6b1f0c9e2ad4471f9c3e5a70b8d21e4f6a9c0b3d5e7f1a2b3c4d5e6f70819a2b")
        String configHash,

        @Schema(description = "True once symbol and candleDuration are set - gate the deploy "
                + "button on this.", example = "true")
        boolean deployable,

        @Schema(description = "How many brokers this is deployed on. An edit moves all of them.",
                example = "3")
        long deploymentCount,

        @Schema(example = "true")
        boolean active,

        @Schema(example = "2026-08-23T19:45:10.221+05:30")
        OffsetDateTime createdAt,

        @Schema(example = "2026-08-23T19:45:10.221+05:30")
        OffsetDateTime updatedAt) {
}
