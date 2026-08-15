package com.example.tradeLedger.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Table {@code subscriptions}: the fan-out edge, and the user-to-strategy
 * relationship in this design.
 *
 * A user never owns a {@link Strategy} or a {@link StrategyInstance} - those are
 * shared, content-addressed rows. What a user owns is a subscription: their
 * account, their sizing, their execution-scope knobs. Every read and write in
 * the API layer is filtered by {@code user_id} for exactly this reason.
 *
 * {@code UNIQUE (strategy_instance_id, trading_account_id)}: one config on two
 * accounts is two subscriptions and therefore two independently tracked legs,
 * with no special columns needed.
 *
 * {@link #execParams} holds SL/TP/trailing - personal, and NEVER part of any
 * hash, which is what lets two users share one indicator computation while
 * exiting at different stop losses.
 */
@Entity
@Table(name = "subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uq_subs_instance_account",
                columnNames = {"strategy_instance_id", "trading_account_id"}))
public class Subscription {

    public static final String MODE_PAPER = "paper";
    public static final String MODE_LIVE = "live";

    public static final String EXEC_FIXED_QTY = "FIXED_QTY";
    public static final String EXEC_CAPITAL_PERCENT = "CAPITAL_PERCENT";
    public static final String EXEC_RISK_PERCENT = "RISK_PERCENT";

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_instance_id", nullable = false)
    private StrategyInstance strategyInstance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trading_account_id", nullable = false)
    private TradingAccount tradingAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "risk_profile_id")
    private RiskProfile riskProfile;

    @Column(name = "quantity", nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "multiplier", nullable = false, precision = 20, scale = 8)
    private BigDecimal multiplier = BigDecimal.ONE;

    @Column(name = "lot_size", precision = 20, scale = 8)
    private BigDecimal lotSize;

    @Column(name = "capital_allocated", precision = 20, scale = 8)
    private BigDecimal capitalAllocated;

    /** FIXED_QTY | CAPITAL_PERCENT | RISK_PERCENT */
    @Column(name = "execution_mode", nullable = false, length = 20)
    private String executionMode = EXEC_FIXED_QTY;

    /** {@code {"sl_pct":1.5,"tp_pct":3.0}} - execution scope only, never hashed. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exec_params", nullable = false, columnDefinition = "jsonb")
    private String execParams = "{}";

    /** paper | live */
    @Column(name = "trade_mode", nullable = false, length = 10)
    private String tradeMode = MODE_PAPER;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * Bumped every time the subscription is repointed at a new instance.
     * Plain data, not a JPA optimistic-lock column - the schema defines it as an
     * ordinary int and the design uses it as configuration lineage.
     */
    @Column(name = "version", nullable = false)
    private int version = 1;

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

    public StrategyInstance getStrategyInstance() { return strategyInstance; }
    public void setStrategyInstance(StrategyInstance strategyInstance) { this.strategyInstance = strategyInstance; }

    public TradingAccount getTradingAccount() { return tradingAccount; }
    public void setTradingAccount(TradingAccount tradingAccount) { this.tradingAccount = tradingAccount; }

    public RiskProfile getRiskProfile() { return riskProfile; }
    public void setRiskProfile(RiskProfile riskProfile) { this.riskProfile = riskProfile; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getMultiplier() { return multiplier; }
    public void setMultiplier(BigDecimal multiplier) { this.multiplier = multiplier; }

    public BigDecimal getLotSize() { return lotSize; }
    public void setLotSize(BigDecimal lotSize) { this.lotSize = lotSize; }

    public BigDecimal getCapitalAllocated() { return capitalAllocated; }
    public void setCapitalAllocated(BigDecimal capitalAllocated) { this.capitalAllocated = capitalAllocated; }

    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }

    public String getExecParams() { return execParams; }
    public void setExecParams(String execParams) { this.execParams = execParams; }

    public String getTradeMode() { return tradeMode; }
    public void setTradeMode(String tradeMode) { this.tradeMode = tradeMode; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
