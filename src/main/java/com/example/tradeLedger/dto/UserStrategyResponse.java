package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A saved strategy: what the caller set, under the names they set it with.
 *
 * The configuration arrives as the columns it is stored in - the same names the
 * request takes, so a round trip is edit-one-field-and-PUT-it-back. Each
 * indicator entry is the same: the values, under the name and slot that address
 * that usage on the way back in.
 *
 * <b>Values, not descriptions.</b> Earlier revisions also shipped the same
 * content re-grouped for a form ({@code legs[]} derived from the CE and PE
 * fields, {@code indicatorGroups[]} by indicator name, {@code fixedParameters[]}
 * by paramGroup), the ids behind the row ({@code userId}, {@code symbolId},
 * {@code sharedConfigId}, {@code configHash}) and, on every indicator usage, the
 * indicator's own {@code paramSchema}. All of it was either a second view of
 * something already here or a fact about the CATALOG rather than about this row -
 * identical for every strategy sharing that indicator - and a list response paid
 * for the lot once per row. A client reads the catalog once, from
 * {@code /api/v1/strategy-templates} and {@code /api/v1/fixed-parameters}, and
 * joins it to these values by name.
 */
@Schema(name = "UserStrategyResponse",
        description = """
                One saved strategy: the caller's settings, and nothing derived or duplicated. \
                The ce*/pe* fields are the EDITABLE form - the same names the request takes - \
                and indicators[] carries each usage's values under the name and slot that \
                address it on a write.

                Values only. What those values MEAN - an indicator's param schema, a fixed \
                knob's label and bounds - belongs to the catalog, is the same for every strategy \
                using it, and is read from /api/v1/strategy-templates and \
                /api/v1/fixed-parameters once per page.""")
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
