package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A user's broker setup. No secret appears here - {@code credentialsConfigured}
 * says whether it can authenticate, and the detail is behind
 * {@code GET /{id}/credentials}, which is itself masked.
 */
@Schema(name = "UserBrokerResponse",
        description = "One of the caller's broker setups. No secret appears here - "
                + "credentialsConfigured says whether it can authenticate at all, and the "
                + "detail is behind GET /{id}/credentials, which is itself masked.")
public record UserBrokerResponse(

        @Schema(description = "Use this as a deploy target to fan out to every account under it.",
                example = "ub000000-1111-4222-8333-444444444444")
        UUID id,

        @Schema(example = "b1000000-1111-4222-8333-444444444444")
        UUID brokerId,

        @Schema(example = "DHAN")
        String brokerCode,

        @Schema(example = "Dhan")
        String brokerName,

        @Schema(description = "Which credential fields this broker needs.",
                example = "api_key", allowableValues = {"api_key", "oauth_redirect", "totp"})
        String authType,

        @Schema(description = "The caller's own name for this setup.", example = "My Dhan")
        String label,

        @Schema(example = "true")
        boolean active,

        @Schema(description = "True when the setup holds enough to attempt a broker call.",
                example = "true")
        boolean credentialsConfigured,

        @Schema(description = "How many trading accounts hang off this setup - i.e. how many "
                + "deployments a userBrokerId target would create.", example = "2")
        long tradingAccountCount,

        @Schema(description = "How many of those override the setup's credentials with their own.",
                example = "0")
        long accountsWithOwnCredentials,

        @Schema(description = "When the credentials were last written.",
                example = "2026-08-23T19:41:02.114+05:30")
        OffsetDateTime rotatedAt,

        @Schema(example = "2026-08-23T19:41:02.100+05:30")
        OffsetDateTime createdAt,

        @Schema(example = "2026-08-23T19:41:02.100+05:30")
        OffsetDateTime updatedAt) {
}
