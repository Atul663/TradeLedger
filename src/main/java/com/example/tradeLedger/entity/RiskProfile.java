package com.example.tradeLedger.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Table {@code risk_profiles}: reusable per-subscription limit sets.
 *
 * Optional on a {@link Subscription} ({@code risk_profile_id} is nullable);
 * user-level aggregate caps live separately in {@link UserRiskLimit}.
 */
@Entity
@Table(name = "risk_profiles")
public class RiskProfile {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "max_daily_loss", precision = 20, scale = 8)
    private BigDecimal maxDailyLoss;

    @Column(name = "max_drawdown", precision = 20, scale = 8)
    private BigDecimal maxDrawdown;

    @Column(name = "max_position_size", precision = 20, scale = 8)
    private BigDecimal maxPositionSize;

    @Column(name = "max_total_exposure", precision = 20, scale = 8)
    private BigDecimal maxTotalExposure;

    @Column(name = "max_trades_per_day")
    private Integer maxTradesPerDay;

    @Column(name = "kill_switch_enabled", nullable = false)
    private boolean killSwitchEnabled = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getMaxDailyLoss() { return maxDailyLoss; }
    public void setMaxDailyLoss(BigDecimal maxDailyLoss) { this.maxDailyLoss = maxDailyLoss; }

    public BigDecimal getMaxDrawdown() { return maxDrawdown; }
    public void setMaxDrawdown(BigDecimal maxDrawdown) { this.maxDrawdown = maxDrawdown; }

    public BigDecimal getMaxPositionSize() { return maxPositionSize; }
    public void setMaxPositionSize(BigDecimal maxPositionSize) { this.maxPositionSize = maxPositionSize; }

    public BigDecimal getMaxTotalExposure() { return maxTotalExposure; }
    public void setMaxTotalExposure(BigDecimal maxTotalExposure) { this.maxTotalExposure = maxTotalExposure; }

    public Integer getMaxTradesPerDay() { return maxTradesPerDay; }
    public void setMaxTradesPerDay(Integer maxTradesPerDay) { this.maxTradesPerDay = maxTradesPerDay; }

    public boolean isKillSwitchEnabled() { return killSwitchEnabled; }
    public void setKillSwitchEnabled(boolean killSwitchEnabled) { this.killSwitchEnabled = killSwitchEnabled; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
