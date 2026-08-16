package com.example.tradeLedger.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Table {@code strategy_indicators}: which indicators a strategy uses, as real
 * foreign keys.
 *
 * This is a DERIVED index, not a second source of truth. {@link Strategy#getRuleTree()}
 * remains authoritative - the rows here are rebuilt from
 * {@code IndicatorResolver.indicatorNames(ruleTree)} every time a strategy is
 * saved, and at startup for rows written outside the API.
 *
 * It records WHICH indicators a strategy depends on, never at which
 * parameterization: EMA(9) and EMA(21) inside one rule tree are two computations
 * but one dependency, so this table holds a single EMA row for that strategy.
 * The concrete computations stay where they belong - resolved per instance from
 * the tree's {@code $bindings} - which is why a fixed link here costs the design
 * nothing.
 */
@Entity
@Table(name = "strategy_indicators",
        uniqueConstraints = @UniqueConstraint(name = "uq_strategy_indicators_strategy_indicator",
                columnNames = {"strategy_id", "indicator_id"}))
public class StrategyIndicator {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_id", nullable = false)
    private Strategy strategy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "indicator_id", nullable = false)
    private IndicatorDef indicator;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Strategy getStrategy() { return strategy; }
    public void setStrategy(Strategy strategy) { this.strategy = strategy; }

    public IndicatorDef getIndicator() { return indicator; }
    public void setIndicator(IndicatorDef indicator) { this.indicator = indicator; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
