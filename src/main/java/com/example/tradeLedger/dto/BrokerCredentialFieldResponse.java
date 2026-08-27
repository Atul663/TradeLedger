package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * One credential-form field descriptor: what the input is called, what type it
 * takes, what it starts at and where it sits on the form.
 *
 * The broker's code and name ride along so a UI rendering one form does not have
 * to resolve the id it filtered on. {@code validation} comes back as a map rather
 * than a JSON string, the same way a fixed parameter's does.
 */
@Schema(name = "BrokerCredentialFieldResponse",
        description = """
                The descriptor of one field on a broker's credential form. Its VALUE lives on \
                broker_credentials as ciphertext and is read and written through \
                /api/v1/my-brokers and /api/v1/trading-accounts; this is the metadata a form \
                renders the input from.""")
public record BrokerCredentialFieldResponse(
        UUID id,
        UUID brokerId,
        @Schema(example = "ZERODHA") String brokerCode,
        @Schema(example = "Zerodha") String brokerName,
        @Schema(description = "The broker_credentials column this input binds to.",
                example = "api_secret")
        String fieldKey,
        @Schema(example = "API Secret") String label,
        String description,
        @Schema(example = "abcd1234efgh5678") String placeholder,
        @Schema(example = "secret", allowableValues = {"text", "secret", "url"}) String dataType,
        @Schema(description = "Null on a secret field, always.", example = "null")
        String defaultValue,
        @Schema(description = "Empty when the field is unbounded.",
                example = "{\"maxLength\": 100}")
        Map<String, Object> validation,
        @Schema(example = "credentials", allowableValues = {"credentials", "session"})
        String fieldGroup,
        @Schema(example = "2") int displayOrder,
        boolean required,
        @Schema(description = "False when the auth flow fills this, not the user - render it "
                + "as status rather than as an input.",
                example = "true")
        boolean userSupplied,
        @Schema(example = "https://developers.kite.trade") String helpUrl,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
