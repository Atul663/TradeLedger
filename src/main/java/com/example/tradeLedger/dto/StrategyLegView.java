package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One side of a strategy, DERIVED from the typed columns for display.
 *
 * The storage is {@code ce_enabled / ce_moneyness / ce_strike_offset} and the
 * matching {@code pe_} triple - two named sides, not a list. This record exists
 * only so a client can iterate "the legs this strategy trades" without knowing
 * that layout, and so the "CE OTM1" label is built the same way everywhere.
 *
 * Read-only: writes address the columns by name.
 */
@Schema(name = "StrategyLegView",
        description = "What one side of the strategy trades, derived from the columns. "
                + "Read-only; writes address ceMoneyness / ceStrikeOffset and their pe twins.")
public record StrategyLegView(

        @Schema(description = "Which side. FUTURES appears alone, on a futures strategy.",
                example = "CE", allowableValues = {"CE", "PE", "FUTURES"})
        String side,

        @Schema(description = "Null on a FUTURES leg - a future has no strike.",
                example = "OTM", allowableValues = {"ATM", "ITM", "OTM"})
        String moneyness,

        @Schema(description = "0 for ATM and FUTURES, 1..15 for ITM and OTM.", example = "1")
        int strikeOffset,

        @Schema(description = "Ready to print.", example = "CE OTM1")
        String label) {
}
