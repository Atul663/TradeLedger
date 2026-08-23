package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The dedup report - the design's acceptance gate.
 */
@Schema(name = "IndicatorPlanResponse",
        description = """
                What the platform actually has to compute, versus what it was asked for.

                This is the acceptance gate for the whole design: many deployments should \
                collapse into few distinct computations. Two strategies with the same indicator \
                values on the same symbol and candle MUST show one instance, however differently \
                they strike, size or exit - if they split, something instrument-shaped has leaked \
                into the config hash.""")
public record IndicatorPlanResponse(

        @Schema(description = "Active deployments across every broker and user.", example = "5")
        long activeStrategySubscriptions,

        @Schema(description = "Distinct shared computations behind them.", example = "2")
        int distinctInstances,

        @Schema(example = "2")
        int distinctIndicators,

        @Schema(description = "Every computation, resolved and deduplicated.",
                example = "[\"EMA AVERAGING(d=21,k=50)\",\"EMA AVERAGING(d=9,k=21)\"]")
        List<String> indicators) {

    public static IndicatorPlanResponse of(long activeStrategySubscriptions, int distinctInstances, List<String> indicators) {
        return new IndicatorPlanResponse(activeStrategySubscriptions, distinctInstances, indicators.size(), indicators);
    }
}
