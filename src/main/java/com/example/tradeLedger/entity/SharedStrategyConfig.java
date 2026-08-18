package com.example.tradeLedger.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Table {@code shared_strategy_configs}: immutable, content-addressed configuration -
 * the dedup unit.
 *
 * Two users asking for EMA 9x21 on the same symbol and timeframe share ONE row;
 * {@code UNIQUE (strategy_id, symbol_id, timeframe, config_hash)} enforces it.
 *
 * A parameter change NEVER updates this row: a new instance is inserted, the
 * subscription is repointed, {@link #supersedes} records the lineage, and the
 * orphan is retired once its last active subscriber leaves.
 *
 * {@link #configHash} is computed server-side by the {@code trg_instances_hash}
 * trigger; the application computes the identical value first (see
 * {@code ConfigHashUtil}) so the row can be looked up before it is inserted.
 * The database is the referee - if the two ever disagree, dedup splits silently,
 * which is what {@code StrategyDedupTest} guards.
 */
@Entity
@Table(name = "shared_strategy_configs",
        uniqueConstraints = @UniqueConstraint(name = "uq_shared_configs_dedup",
                columnNames = {"strategy_id", "symbol_id", "timeframe", "config_hash"}))
public class SharedStrategyConfig {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_RETIRED = "retired";

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_id", nullable = false)
    private StrategyTemplate strategy;

    /** The SIGNAL symbol - indicators run on this, not on the traded contract. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "symbol_id", nullable = false)
    private Symbol symbol;

    /** '5m', '15m', '1h', ... */
    @Column(name = "timeframe", nullable = false, length = 20)
    private String timeframe;

    /** Canonicalized signal-scope params only, e.g. {@code {"fast": 9, "slow": 21}} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "signal_params", nullable = false, columnDefinition = "jsonb")
    private String signalParams;

    @Column(name = "config_hash", nullable = false, length = 64)
    private String configHash;

    /** Version lineage: the instance this one replaced. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_id")
    private SharedStrategyConfig supersedes;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public StrategyTemplate getStrategy() { return strategy; }
    public void setStrategy(StrategyTemplate strategy) { this.strategy = strategy; }

    public Symbol getSymbol() { return symbol; }
    public void setSymbol(Symbol symbol) { this.symbol = symbol; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    public String getSignalParams() { return signalParams; }
    public void setSignalParams(String signalParams) { this.signalParams = signalParams; }

    public String getConfigHash() { return configHash; }
    public void setConfigHash(String configHash) { this.configHash = configHash; }

    public SharedStrategyConfig getSupersedes() { return supersedes; }
    public void setSupersedes(SharedStrategyConfig supersedes) { this.supersedes = supersedes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
