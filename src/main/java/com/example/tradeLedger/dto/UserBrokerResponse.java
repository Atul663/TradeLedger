package com.example.tradeLedger.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A user's broker setup. No secret appears here - {@code credentialsConfigured}
 * says whether it can authenticate, and the detail is behind
 * {@code GET /{id}/credentials}, which is itself masked.
 */
public record UserBrokerResponse(
        UUID id,
        UUID brokerId,
        String brokerCode,
        String brokerName,
        /** Which credential fields this broker needs: api_key, oauth_redirect, totp. */
        String authType,
        String label,
        boolean active,
        /** True when the setup holds enough to attempt a broker call. */
        boolean credentialsConfigured,
        /** How many trading accounts hang off this setup. */
        long tradingAccountCount,
        /** How many of those override the setup's credentials with their own. */
        long accountsWithOwnCredentials,
        OffsetDateTime rotatedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
