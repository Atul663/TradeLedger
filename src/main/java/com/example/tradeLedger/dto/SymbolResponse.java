package com.example.tradeLedger.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Reference data: a tradable contract.
 *
 * A strategy instance's symbol is the SIGNAL symbol - indicators run on it.
 * Conventionally that is the underlying (spot/index) rather than an option
 * contract.
 */
public record SymbolResponse(
        UUID id,
        UUID exchangeId,
        String exchangeCode,
        String symbol,
        String baseAsset,
        String quoteAsset,
        String instrumentType,
        String optionType,
        BigDecimal strikePrice,
        OffsetDateTime expiryAt,
        BigDecimal contractSize,
        BigDecimal tickSize,
        BigDecimal minQty,
        boolean active) {
}
