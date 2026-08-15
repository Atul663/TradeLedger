package com.example.tradeLedger.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UserRiskLimitResponse(
        UUID userId,
        BigDecimal maxDailyLoss,
        Integer maxOpenPositions,
        BigDecimal maxTotalExposure,
        OffsetDateTime updatedAt) {
}
