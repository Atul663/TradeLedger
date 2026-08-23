package com.example.tradeLedger.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Table {@code user_strategy_subscriptions}: one deployment of one saved strategy
 * onto one trading account.
 *
 * <pre>
 *   user_strategy_subscriptions ──→ users             whose it is
 *                               ──→ user_strategies   WHAT it runs
 *                               ──→ trading_accounts  WHERE it runs
 *                               ──→ risk_profiles     under which caps
 * </pre>
 *
 * <b>The configuration is not copied here.</b> A subscription reaches its
 * instrument, strikes, ladder, exits and indicator tuning through
 * {@code user_strategy_id}, so editing the saved strategy moves every broker it
 * is deployed on at once - which is what "deploy it with multiple brokers" means.
 * A frozen copy per account would drift the moment the user retuned anything and
 * leave no way to tell the copies from the original.
 *
 * What IS here is the only thing that genuinely differs per account: how much,
 * under whose risk profile, and paper or live. Everything else is one row away
 * through a foreign key.
 *
 * {@code UNIQUE (user_strategy_id, trading_account_id)}: a saved strategy is
 * deployed on an account once. Deploying it twice is an edit, not a second row.
 */
@Entity
@Table(name = "user_strategy_subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_strategy_subs_strategy_account",
                columnNames = {"user_strategy_id", "trading_account_id"}))
public class StrategySubscription {

    public static final String MODE_PAPER = "paper";
    public static final String MODE_LIVE = "live";

    public static final String EXEC_FIXED_QTY = "FIXED_QTY";
    public static final String EXEC_CAPITAL_PERCENT = "CAPITAL_PERCENT";
    public static final String EXEC_RISK_PERCENT = "RISK_PERCENT";

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Kept alongside {@code userStrategy} even though that row knows its owner.
     * Ownership filtering happens on every read ({@code findByIdAndUser_Id}) and
     * joining through the parent to do it would turn the cheapest, most frequent
     * check in the module into a two-table query.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The saved configuration this deployment runs. The whole of "what". */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_strategy_id", nullable = false)
    private UserStrategy userStrategy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trading_account_id", nullable = false)
    private TradingAccount tradingAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "risk_profile_id")
    private RiskProfile riskProfile;

    /**
     * Scales the strategy's {@code baseLot} on this account: 1 runs the ladder as
     * configured, 2 runs it at double size on this broker alone.
     *
     * A multiplier rather than a second quantity, so there is exactly one place a
     * size is authored and this is a knob on top of it.
     */
    @Column(name = "multiplier", precision = 20, scale = 8, nullable = false)
    private BigDecimal multiplier = BigDecimal.ONE;

    /** Capital earmarked on this account, for the percent-based execution modes. */
    @Column(name = "capital_allocated", precision = 20, scale = 8)
    private BigDecimal capitalAllocated;

    /** FIXED_QTY | CAPITAL_PERCENT | RISK_PERCENT */
    @Column(name = "execution_mode", nullable = false, length = 20)
    private String executionMode = EXEC_FIXED_QTY;

    /** paper | live - decided per account, so one broker can go live before the rest. */
    @Column(name = "trade_mode", nullable = false, length = 10)
    private String tradeMode = MODE_PAPER;

    /** Pause this one broker without touching the strategy or the others. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

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

    public UserStrategy getUserStrategy() { return userStrategy; }
    public void setUserStrategy(UserStrategy userStrategy) { this.userStrategy = userStrategy; }

    public TradingAccount getTradingAccount() { return tradingAccount; }
    public void setTradingAccount(TradingAccount tradingAccount) { this.tradingAccount = tradingAccount; }

    public RiskProfile getRiskProfile() { return riskProfile; }
    public void setRiskProfile(RiskProfile riskProfile) { this.riskProfile = riskProfile; }

    public BigDecimal getMultiplier() { return multiplier; }
    public void setMultiplier(BigDecimal multiplier) { this.multiplier = multiplier; }

    public BigDecimal getCapitalAllocated() { return capitalAllocated; }
    public void setCapitalAllocated(BigDecimal capitalAllocated) { this.capitalAllocated = capitalAllocated; }

    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }

    public String getTradeMode() { return tradeMode; }
    public void setTradeMode(String tradeMode) { this.tradeMode = tradeMode; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
