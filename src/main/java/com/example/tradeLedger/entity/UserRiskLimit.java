package com.example.tradeLedger.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Table {@code user_risk_limits}: user-level AGGREGATE caps, one row per user.
 *
 * Exists because per-subscription {@link RiskProfile} limits alone would give a
 * user with ten subscriptions ten independent daily-loss limits and no total.
 */
@Entity
@Table(name = "user_risk_limits")
public class UserRiskLimit {

    /** PK and FK in one: user_risk_limits.user_id references users(id). */
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "max_daily_loss", precision = 20, scale = 8)
    private BigDecimal maxDailyLoss;

    @Column(name = "max_open_positions")
    private Integer maxOpenPositions;

    @Column(name = "max_total_exposure", precision = 20, scale = 8)
    private BigDecimal maxTotalExposure;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public BigDecimal getMaxDailyLoss() { return maxDailyLoss; }
    public void setMaxDailyLoss(BigDecimal maxDailyLoss) { this.maxDailyLoss = maxDailyLoss; }

    public Integer getMaxOpenPositions() { return maxOpenPositions; }
    public void setMaxOpenPositions(Integer maxOpenPositions) { this.maxOpenPositions = maxOpenPositions; }

    public BigDecimal getMaxTotalExposure() { return maxTotalExposure; }
    public void setMaxTotalExposure(BigDecimal maxTotalExposure) { this.maxTotalExposure = maxTotalExposure; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
