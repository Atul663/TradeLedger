package com.example.tradeLedger.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Table {@code user_strategy_parameters}: ONE ROW PER VALUE THE USER CHANGED.
 *
 * A knob left at its default has no row here. That is the whole point - the
 * global default is read through the catalog every time, so an admin retuning
 * {@code parameters.default_value} or an indicator's
 * {@code indicator_parameter_links.default_value} moves every user who never
 * overrode it, and moves nobody who did.
 *
 * The row sits at one of two levels, distinguished by whether
 * {@link #userStrategyIndicator} is set:
 * <ul>
 *   <li><b>indicator level</b> - {@code userStrategyIndicator} and
 *       {@link #indicatorParameterLink} both set; overrides the default that
 *       indicator declares for the knob ({@code k}, {@code d})</li>
 *   <li><b>strategy level</b> - both null; overrides the default the template
 *       declares for a strategy-wide knob ({@code sl}, {@code tp},
 *       {@code quantity}, the durations)</li>
 * </ul>
 *
 * {@link #parameter} is set either way: every override is an override OF a
 * catalog row, and keying on it is what lets the effective-value query resolve
 * custom &rarr; link default &rarr; catalog default in one COALESCE.
 *
 * {@link #customValue} is text for the same reason
 * {@code parameters.default_value} is: the catalog holds int, decimal, bool,
 * enum, timeframe and text knobs in one table, and the declared
 * {@code parameters.data_type} is what coerces it. Storing it typed would need a
 * column per type or an EAV sprawl, and the value is validated against that
 * data type and its validation rules before it is ever written.
 */
@Entity
@Table(name = "user_strategy_parameters")
public class UserStrategyParameter {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Always set, at both levels - "every override belonging to this user strategy". */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_strategy_id", nullable = false)
    private UserStrategy userStrategy;

    /** Null for a strategy-level knob; set for an indicator-level one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_strategy_indicator_id")
    private UserStrategyIndicator userStrategyIndicator;

    /** The catalog knob being overridden, by id. Never a code string. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "parameter_id", nullable = false)
    private Parameter parameter;

    /**
     * Which indicator usage's default this displaces. Null at strategy level.
     * Recorded even though it is reachable through the indicator, because it is
     * the row whose {@code default_value} this value is overriding and the
     * effective-value query reads it directly.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "indicator_parameter_link_id")
    private IndicatorParameterLink indicatorParameterLink;

    @Column(name = "custom_value", nullable = false, columnDefinition = "text")
    private String customValue;

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

    /** True when this overrides a knob belonging to one indicator rather than the strategy. */
    public boolean isIndicatorScoped() {
        return userStrategyIndicator != null;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UserStrategy getUserStrategy() { return userStrategy; }
    public void setUserStrategy(UserStrategy userStrategy) { this.userStrategy = userStrategy; }

    public UserStrategyIndicator getUserStrategyIndicator() { return userStrategyIndicator; }
    public void setUserStrategyIndicator(UserStrategyIndicator userStrategyIndicator) {
        this.userStrategyIndicator = userStrategyIndicator;
    }

    public Parameter getParameter() { return parameter; }
    public void setParameter(Parameter parameter) { this.parameter = parameter; }

    public IndicatorParameterLink getIndicatorParameterLink() { return indicatorParameterLink; }
    public void setIndicatorParameterLink(IndicatorParameterLink indicatorParameterLink) {
        this.indicatorParameterLink = indicatorParameterLink;
    }

    public String getCustomValue() { return customValue; }
    public void setCustomValue(String customValue) { this.customValue = customValue; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
