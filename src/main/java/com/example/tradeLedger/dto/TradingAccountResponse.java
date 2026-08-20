package com.example.tradeLedger.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One account under a broker setup. No secret appears here.
 *
 * {@code credentialsConfigured} answers "can this account authenticate?" - which
 * is true when the setup has credentials, whether or not this account overrides
 * them. {@code credentialsOverridden} says which of the two it is using.
 */
public record TradingAccountResponse(
        UUID id,
        UUID userBrokerId,
        String userBrokerLabel,
        UUID brokerId,
        String brokerCode,
        String brokerName,
        String brokerAuthType,
        String accountName,
        String brokerAccountId,
        boolean active,
        boolean credentialsConfigured,
        /** True when this account has its own credentials rather than the setup's. */
        boolean credentialsOverridden,
        long activeStrategySubscriptions,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
