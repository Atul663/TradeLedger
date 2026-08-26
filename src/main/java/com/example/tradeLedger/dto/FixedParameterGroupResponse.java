package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One section of a form: the fixed-knob descriptors that share a
 * {@code paramGroup}.
 *
 * The grouping key is {@code fixed_parameters.param_group} - 'Market',
 * 'Instrument', 'Sizing', 'Exits', 'Deployment' - which is exactly the section a
 * form lays those fields out in. Carrying it as a FIELD rather than as a JSON key
 * means renaming a group changes a value and never the structure a client parses,
 * the same stance {@link UserStrategyGroupResponse} takes.
 *
 * Descriptors only: nothing here says what any strategy is set to. The
 * value-bearing counterpart is the strategy response itself, whose flat fields
 * carry the values these describe.
 */
@Schema(name = "FixedParameterGroupResponse",
        description = """
                One form section's worth of fixed-knob descriptors, grouped by paramGroup. The \
                rows inside are the same FixedParameterResponse the flat list returns, in the \
                same displayOrder-then-name order.""")
public record FixedParameterGroupResponse(

        @Schema(description = "The section these knobs belong to. Null for a descriptor that "
                + "was never assigned one - those collect in a single trailing group.",
                example = "Exits")
        String paramGroup,

        @Schema(description = "How many descriptors are in this group, AFTER the filters have "
                + "been applied.", example = "2")
        int count,

        @Schema(description = "By displayOrder, then name - the order the section renders in.")
        List<FixedParameterResponse> parameters) {
}
