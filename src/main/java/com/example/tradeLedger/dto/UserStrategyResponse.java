package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A saved strategy: one call renders the whole editor.
 *
 * The configuration arrives as the columns it is stored in - the same names the
 * request takes, so a round trip is edit-one-field-and-PUT-it-back. Each
 * indicator carries its current values and the schema those values are validated
 * against, so the form can render an input for a knob nobody hardcoded.
 *
 * <b>One arrangement, not several.</b> Earlier revisions also shipped the same
 * content re-grouped for a form - {@code legs[]} derived from the CE and PE
 * fields, {@code indicatorGroups[]} by indicator name, {@code fixedParameters[]}
 * by paramGroup - along with the ids behind the row ({@code userId},
 * {@code symbolId}, {@code sharedConfigId}, {@code configHash}). Every one of
 * them was a second view of something already here, and nothing read them; a
 * list response paid for all of them on every row. The flat fields below are the
 * whole shape now.
 */
@Schema(name = "UserStrategyResponse",
        description = """
                One saved strategy. The ce*/pe* fields are the EDITABLE form - the same names the \
                request takes - and each indicator carries both its current values and its \
                schema, so the form can render an input for a knob nobody hardcoded.

                Flat only: there is one arrangement of the configuration, and a PUT addresses it \
                by name.""")
public record UserStrategyResponse(

        @Schema(example = "us000000-1111-4222-8333-444444444444")
        UUID id,

        @Schema(description = "The template whose logic this runs.",
                example = "3f1b0c7e-9a41-4c2e-9f11-2b7d5a6e8c01")
        UUID strategyId,

        @Schema(example = "EMA Averaging")
        String strategyName,

        @Schema(description = "The caller's own label.", example = "NIFTY 21/9 both sides")
        String name,

        @Schema(example = "Sheet block 1")
        String description,

        // ------------------------------------------------------------- market

        @Schema(example = "NIFTY")
        String symbol,

        @Schema(example = "NSE")
        String exchangeCode,

        @Schema(description = "The candle the signal is evaluated on. Hashed.", example = "5m")
        String candleDuration,

        @Schema(description = "How often to re-check inside that candle. Never hashed.", example = "5m")
        String triggerDuration,

        // --------------------------------------------------------- instrument

        @Schema(example = "OPTION", allowableValues = {"FUTURES", "OPTION"})
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

        @Schema(description = "Every indicator usage, flat and in display order.")
        List<UserStrategyIndicatorResponse> indicators,

        // ------------------------------------------------------------ runtime

        @Schema(description = "True once symbol and candleDuration are set - gate the deploy "
                + "button on this.", example = "true")
        boolean deployable,

        @Schema(example = "true")
        boolean active,

        @Schema(example = "2026-08-23T19:45:10.221+05:30")
        OffsetDateTime createdAt,

        @Schema(example = "2026-08-23T19:45:10.221+05:30")
        OffsetDateTime updatedAt) {
}
