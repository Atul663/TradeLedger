package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The dedup unit: one content-addressed computation, shared by everyone whose
 * strategy resolves to it.
 */
@Schema(name = "SharedStrategyConfigResponse",
        description = """
                One shared computation - the dedup unit, and the whole point of the design.

                Its identity is template + symbol + timeframe + the indicator values, hashed. \
                Instrument, strikes, ladder and exits are deliberately EXCLUDED, which is what \
                lets two users trading opposite sides of the same signal share one computation.

                Read-only: instances appear when a strategy resolves to them and are retired \
                (never deleted) when their last active deployment leaves.""")
public record SharedStrategyConfigResponse(

        @Schema(example = "sc000000-1111-4222-8333-444444444444")
        UUID id,

        @Schema(example = "3f1b0c7e-9a41-4c2e-9f11-2b7d5a6e8c01")
        UUID strategyId,

        @Schema(example = "EMA Averaging")
        String strategyName,

        @Schema(example = "1a2b3c4d-5e6f-4a8b-9c0d-1e2f3a4b5c6d")
        UUID symbolId,

        @Schema(example = "NIFTY")
        String symbol,

        @Schema(description = "The candle duration of the strategies that point here.", example = "5m")
        String timeframe,

        @Schema(description = "The union of every enabled indicator's values, canonicalized.",
                example = "{\"d\": 9, \"k\": 21}")
        Map<String, Object> signalParams,

        @Schema(example = "6b1f0c9e2ad4471f9c3e5a70b8d21e4f6a9c0b3d5e7f1a2b3c4d5e6f70819a2b")
        String configHash,

        @Schema(description = "The instance this one replaced when a strategy was retuned.",
                example = "null")
        UUID supersedesId,

        @Schema(example = "active", allowableValues = {"active", "retired"})
        String status,

        @Schema(description = "The concrete computations this instance needs.",
                example = "[\"EMA AVERAGING(d=9,k=21)\"]")
        List<String> indicators,

        @Schema(description = "How many active deployments feed off it. At zero it is retired.",
                example = "3")
        long activeSubscribers,

        @Schema(example = "2026-08-23T19:45:10.221+05:30")
        OffsetDateTime createdAt) {
}
