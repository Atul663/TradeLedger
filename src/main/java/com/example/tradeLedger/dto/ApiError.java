package com.example.tradeLedger.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Error body for the strategy / indicator endpoints.
 *
 * Shaped to stay compatible with what the rest of the application already emits:
 * {@code {"error": "..."}} matches {@code JwtFilter}, and {@code errors} carries
 * the multi-message list that parameter validation produces.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String error, List<String> errors) {

    public static ApiError of(String message) {
        return new ApiError(message, null);
    }

    public static ApiError of(List<String> messages) {
        return new ApiError(messages.isEmpty() ? "Validation failed" : messages.get(0), messages);
    }
}
