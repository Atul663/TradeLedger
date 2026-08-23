package com.example.tradeLedger.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Error body for the strategy / indicator endpoints.
 *
 * Shaped to stay compatible with what the rest of the application already emits:
 * {@code {"error": "..."}} matches {@code JwtFilter}, and {@code errors} carries
 * the multi-message list that validation produces.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiError",
        description = """
                error is always present and is a displayable sentence.

                errors[] appears ONLY on 400 and holds EVERY problem found, not just the first - \
                render the whole list. Field names in the messages match the request field names, \
                so matching on them to place an inline error works.""")
public record ApiError(

        @Schema(description = "Always present.",
                example = "ceStrikeOffset must be 1..15 for OTM, got 16")
        String error,

        @Schema(description = "Validation failures only (400). Every problem found.",
                example = "[\"ceStrikeOffset must be 1..15 for OTM, got 16\","
                        + "\"averagingCount must be 0..10, got 25\"]")
        List<String> errors) {

    public static ApiError of(String message) {
        return new ApiError(message, null);
    }

    public static ApiError of(List<String> messages) {
        return new ApiError(messages.isEmpty() ? "Validation failed" : messages.get(0), messages);
    }
}
