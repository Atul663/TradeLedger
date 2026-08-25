package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One section of a saved strategy's form: its fixed knobs, with values, that
 * share a {@code paramGroup}.
 *
 * The value-bearing counterpart of {@link FixedParameterGroupResponse}. Only the
 * knobs a {@code user_strategies} row actually carries appear - the 'deployment'
 * group describes subscription columns and belongs to the deployment shape, not
 * to a strategy.
 */
@Schema(name = "StrategyFixedParameterGroupResponse",
        description = """
                One form section of a saved strategy - the fixed knobs sharing a paramGroup, \
                each with the descriptor it renders from and the value it currently holds.""")
public record StrategyFixedParameterGroupResponse(

        @Schema(description = "The section these knobs belong to.",
                example = "instrument",
                allowableValues = {"market", "instrument", "sizing", "exits"})
        String paramGroup,

        @Schema(description = "How many knobs are in this group.", example = "7")
        int count,

        @Schema(description = "By displayOrder, then name - the order the section renders in.")
        List<StrategyFixedParameterResponse> parameters) {
}
