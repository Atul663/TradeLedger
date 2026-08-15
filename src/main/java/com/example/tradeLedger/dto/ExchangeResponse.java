package com.example.tradeLedger.dto;

import java.util.UUID;

/** Reference data: a trading venue. */
public record ExchangeResponse(
        UUID id,
        String name,
        String code,
        String description,
        String status) {
}
