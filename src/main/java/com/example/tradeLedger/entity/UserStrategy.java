package com.example.tradeLedger.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Table {@code user_strategies}: a user's own customization of a global
 * {@link StrategyTemplate}.
 *
 * <pre>
 *   user_strategies  ──→ strategy_templates          (which global strategy)
 *        │
 *        ├─ user_strategy_indicators ──→ indicators  (which indicator usages)
 *        │        │
 *        │        └─ user_strategy_parameters ──→ indicator_parameter_links
 *        │                                        (the changed values)
 *        └─ user_strategy_parameters ──→ parameters  (strategy-level: sl, tp, ...)
 * </pre>
 *
 * <b>No global row is ever written.</b> The template, its indicators and the
 * parameter catalog are shared across every user; this row and its children hold
 * foreign keys and the values the user actually changed, nothing else. No
 * default, label, data type or validation rule is copied down.
 *
 * A user may customize one template any number of times - {@code UNIQUE
 * (user_id, name)} is per user, so "My fast EMA" and "My slow EMA" can both point
 * at EMA Crossover.
 *
 * This row runs nothing. It becomes live only when it is subscribed, at which
 * point its effective values are projected into the flat map the execution path
 * already consumes - signal scope into a shared, hashed
 * {@link SharedStrategyConfig}, execution scope onto the
 * {@link StrategySubscription}.
 */
@Entity
@Table(name = "user_strategies",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_strategies_user_name",
                columnNames = {"user_id", "name"}))
public class UserStrategy {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The global template this customizes. Never modified from here. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_id", nullable = false)
    private StrategyTemplate strategy;

    /** The user's own label. Unique per user, not platform-wide. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** Nullable: a user strategy may be params-only and pick its market at subscribe time. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol_id")
    private Symbol symbol;

    @Column(name = "timeframe", length = 20)
    private String timeframe;

    /** A shelf flag, not an execution one: nothing here runs either way. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "userStrategy", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<UserStrategyIndicator> indicators = new ArrayList<>();

    /**
     * Every override this strategy carries, at BOTH levels - the indicator-scoped
     * ones and the strategy-scoped ones. Mapped here as well as on
     * {@link UserStrategyIndicator} so "all the values this user changed" is one
     * collection rather than a walk over the indicator list.
     */
    @OneToMany(mappedBy = "userStrategy", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserStrategyParameter> parameters = new ArrayList<>();

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

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public StrategyTemplate getStrategy() { return strategy; }
    public void setStrategy(StrategyTemplate strategy) { this.strategy = strategy; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Symbol getSymbol() { return symbol; }
    public void setSymbol(Symbol symbol) { this.symbol = symbol; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public List<UserStrategyIndicator> getIndicators() { return indicators; }
    public void setIndicators(List<UserStrategyIndicator> indicators) { this.indicators = indicators; }

    public List<UserStrategyParameter> getParameters() { return parameters; }
    public void setParameters(List<UserStrategyParameter> parameters) { this.parameters = parameters; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
