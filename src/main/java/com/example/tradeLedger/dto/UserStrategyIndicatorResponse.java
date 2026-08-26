package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * One indicator usage inside a saved strategy: which indicator, which usage of
 * it, and the values it is tuned to.
 *
 * {@link #params} is the effective set - the user's values with the schema's
 * defaults filled in - so a form never has to resolve a fallback.
 *
 * <b>Values only.</b> The declaration those values are validated against - type,
 * bounds, defaults, gt/lt rules - is the INDICATOR's, identical for every
 * strategy using it, and comes from {@code GET /api/v1/strategy-templates} (or
 * {@code /api/v1/indicators}) as {@code paramSchema}. It used to be repeated on
 * every usage of every strategy in a list; it is fetched once per page instead.
 *
 * The three fields here are also exactly what a write takes: send
 * {@code indicatorName} and {@code slot} back with the params you changed.
 */
@Schema(name = "UserStrategyIndicatorResponse",
        description = """
                One indicator usage and the values it is tuned to.

                Values only - the schema they are validated against belongs to the indicator and \
                comes from the template. These are the same three fields a write takes, so a \
                round trip is change-a-value-and-PUT-it-back.""")
public record UserStrategyIndicatorResponse(

        @Schema(description = "Names the indicator, and addresses this usage on update.",
                example = "EMA Averaging")
        String indicatorName,

        @Schema(description = "Only set when a template uses one indicator twice - then it is "
                + "what tells the two usages apart, on read and on write.", example = "null")
        String slot,

        @Schema(description = "Effective values - the user's, with schema defaults filled in. "
                + "Returned in canonical (sorted) order, which is what the hash is computed over.",
                example = "{\"d\": 9, \"k\": 21}")
        Map<String, Object> params) {
}
