package com.example.tradeLedger.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A user's broker account. {@code vaultRef} is a pointer, never a secret. */
public record TradingAccountResponse(
        UUID id,
        UUID exchangeId,
        String exchangeCode,
        String exchangeName,
        String accountName,
        boolean active,
        String vaultRef,
        OffsetDateTime rotatedAt,
        long activeStrategySubscriptions,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
