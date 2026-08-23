package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One account under a broker setup. No secret appears here.
 *
 * {@code credentialsConfigured} answers "can this account authenticate?" - which
 * is true when the setup has credentials, whether or not this account overrides
 * them. {@code credentialsOverridden} says which of the two it is using.
 */
@Schema(name = "TradingAccountResponse",
        description = "One account under a broker setup - what a strategy is actually deployed "
                + "onto. credentialsConfigured answers 'can this authenticate?', which is true "
                + "when the SETUP has a key even if this account has none of its own; "
                + "credentialsOverridden says which of the two it is using.")
public record TradingAccountResponse(

        @Schema(description = "Use this as a deploy target for a single account.",
                example = "ta000000-1111-4222-8333-444444444444")
        UUID id,

        @Schema(example = "ub000000-1111-4222-8333-444444444444")
        UUID userBrokerId,

        @Schema(example = "My Dhan")
        String userBrokerLabel,

        @Schema(example = "b1000000-1111-4222-8333-444444444444")
        UUID brokerId,

        @Schema(example = "DHAN")
        String brokerCode,

        @Schema(example = "Dhan")
        String brokerName,

        @Schema(example = "api_key", allowableValues = {"api_key", "oauth_redirect", "totp"})
        String brokerAuthType,

        @Schema(description = "The caller's own label, unique within the setup.", example = "main")
        String accountName,

        @Schema(description = "The broker's own identifier for the account.", example = "1100112233")
        String brokerAccountId,

        @Schema(description = "An inactive account is refused at deploy time.", example = "true")
        boolean active,

        @Schema(description = "Can this account authenticate - through the setup's key or its own.",
                example = "true")
        boolean credentialsConfigured,

        @Schema(description = "True when this account has its own key rather than inheriting.",
                example = "false")
        boolean credentialsOverridden,

        @Schema(description = "How many strategies are currently deployed here.", example = "3")
        long activeStrategySubscriptions,

        @Schema(example = "2026-08-23T19:41:02.100+05:30")
        OffsetDateTime createdAt,

        @Schema(example = "2026-08-23T19:41:02.100+05:30")
        OffsetDateTime updatedAt) {
}
