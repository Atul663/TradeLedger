package com.example.tradeLedger.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Reference data: a reusable per-subscription limit set. */
public record RiskProfileResponse(
        UUID id,
        String name,
        String description,
        BigDecimal maxDailyLoss,
        BigDecimal maxDrawdown,
        BigDecimal maxPositionSize,
        BigDecimal maxTotalExposure,
        Integer maxTradesPerDay,
        boolean killSwitchEnabled) {
}
