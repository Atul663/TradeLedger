package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;
import java.util.UUID;

/**
 * An indicator as a template uses it: what it is, and what it can be tuned with.
 *
 * {@link #paramSchema} is carried rather than a list of parameter rows, because
 * there are no parameter rows any more - the schema IS the declaration.
 */
@Schema(name = "IndicatorSummaryResponse",
        description = "An indicator a template's rule tree names, with the declaration its "
                + "values are validated against. This is the ONLY part of a builder form that "
                + "varies by template.")
public record IndicatorSummaryResponse(

        @Schema(example = "b2e4a1c8-1f3d-4e6a-9b2c-5d7e8f0a1b2c")
        UUID id,

        @Schema(example = "EMA Averaging")
        String name,

        @Schema(example = "true")
        boolean active,

        @Schema(description = "type is one of int/decimal/bool/enum/text; default is always "
                + "present; label is always present on read, falling back to the key; min, max, "
                + "options and a gt/lt sibling rule are optional.",
                example = "{\"k\": {\"type\":\"int\",\"min\":1,\"max\":300,\"default\":21,"
                        + "\"label\":\"Short (k)\"}, "
                        + "\"d\": {\"type\":\"int\",\"min\":1,\"max\":300,\"default\":9,\"lt\":\"k\","
                        + "\"label\":\"Long (d)\"}}")
        Map<String, Object> paramSchema) {
}
