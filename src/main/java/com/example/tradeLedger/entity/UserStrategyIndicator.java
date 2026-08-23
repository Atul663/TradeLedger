package com.example.tradeLedger.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Table {@code user_strategy_indicators}: one indicator usage inside a
 * {@link UserStrategy}, and the values it runs with.
 *
 * <pre>
 *   user_strategy_indicators ──→ user_strategies   whose tuning this is
 *                            ──→ indicators        which indicator
 *        params  {"k":21,"d":9}
 * </pre>
 *
 * <b>This is the one place a strategy stores anything schemaless, and
 * deliberately so.</b> Indicators are the only pluggable part of the platform:
 * EMA takes k and d, RSI takes period, the next one takes whatever its author
 * says. Everything a strategy has that the platform itself defines - the
 * instrument, the strikes, the ladder, the exits, the durations - is a typed
 * column on {@link UserStrategy}.
 *
 * {@link #params} is validated on write against {@code indicators.param_schema},
 * so the jsonb is not a place unchecked data can hide: an unknown key, a value
 * out of range or a wrong type is a 400, exactly as a column constraint would be.
 * A key the user never set is simply absent, and the schema's own default
 * applies - which is how a platform retune reaches every user who left that value
 * alone and nobody who did not.
 *
 * These values, canonicalized, are the WHOLE of the config hash. Two users whose
 * indicators resolve identically on the same symbol and candle share one
 * computation however differently they strike, size or exit.
 *
 * {@link #slot} exists for the case where one template uses the same indicator
 * twice - two plain EMAs rather than one composite. It is null for every template
 * that uses each indicator once, and it is part of the unique key so the two
 * usages stay distinct rows without a schema change later.
 */
@Entity
@Table(name = "user_strategy_indicators",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_strategy_indicators",
                columnNames = {"user_strategy_id", "indicator_id", "slot"}))
public class UserStrategyIndicator {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_strategy_id", nullable = false)
    private UserStrategy userStrategy;

    /** The catalog indicator, by id. Never a name. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "indicator_id", nullable = false)
    private Indicator indicator;

    /** 'fast' / 'slow' when one template uses an indicator twice; null otherwise. */
    @Column(name = "slot", length = 50)
    private String slot;

    /**
     * {@code {"k":21,"d":9}} - only the values the user actually set.
     *
     * Validated against the indicator's {@code param_schema} before it is written,
     * and canonicalized before it is hashed, so {@code 9}, {@code 9.0} and
     * {@code "9"} cannot split the dedup.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false, columnDefinition = "jsonb")
    private String params = "{}";

    /**
     * Lets a user park an optional indicator without losing its tuning. A disabled
     * row contributes nothing to the config hash.
     */
    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

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

    public UserStrategy getUserStrategy() { return userStrategy; }
    public void setUserStrategy(UserStrategy userStrategy) { this.userStrategy = userStrategy; }

    public Indicator getIndicator() { return indicator; }
    public void setIndicator(Indicator indicator) { this.indicator = indicator; }

    public String getSlot() { return slot; }
    public void setSlot(String slot) { this.slot = slot; }

    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
