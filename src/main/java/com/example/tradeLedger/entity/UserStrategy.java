package com.example.tradeLedger.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Table {@code user_strategies}: one complete, runnable strategy configuration
 * owned by one user.
 *
 * <pre>
 *   user_strategies ──→ users               whose it is
 *                  ──→ strategy_templates   which logic it runs
 *                  ──→ symbols              which underlying it watches
 *                  ──→ shared_strategy_configs  the dedup unit it resolved to
 *        │
 *        └─ user_strategy_indicators ──→ indicators   the tuning of each one
 * </pre>
 *
 * <b>Everything the platform knows about is a column.</b> The instrument choice,
 * the strike selection, the averaging ladder, the exits and the durations are
 * fixed concepts on an Indian F&amp;O desk - they are not user-defined knobs - so
 * they are typed columns with real defaults and real CHECK constraints, not rows
 * in a key/value table. Wrong data is refused by the database, not only by Java,
 * and "every strategy trading OTM calls on a doubling ladder" is a WHERE clause.
 *
 * <b>Only indicator tuning is dynamic</b>, because only indicators are pluggable:
 * EMA takes k and d, RSI takes period, the next one takes whatever it takes. That
 * lives in {@code user_strategy_indicators.params} as jsonb, validated against
 * {@code indicators.param_schema}, and it is the ONLY part of a strategy that
 * enters the config hash - two users whose indicators resolve identically share
 * one computation however differently they size, exit or strike.
 *
 * <b>The CE and PE sides are columns, not rows.</b> There is one call side and
 * one put side; they are named parts of a strategy, not an open-ended list. A
 * child table would have bought nothing but a join and a uniqueness constraint to
 * enforce what the column layout makes structurally impossible.
 */
@Entity
@Table(name = "user_strategies",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_strategies_user_name",
                columnNames = {"user_id", "name"}),
        // Depth and moneyness have to agree: ATM is a single strike, ITM and OTM
        // offer 15 each. Enforced here as well as in the validator so a direct
        // INSERT cannot create a position the engine has no strike for.
        //
        // jakarta.persistence rather than org.hibernate.annotations.Check, which
        // Hibernate 7 deprecated in favour of this once JPA 3.2 standardized it.
        check = {
                @CheckConstraint(name = "ck_user_strategies_ce_strike", constraint =
                        "(ce_moneyness IS NULL) "
                                + "OR (ce_moneyness = 'ATM' AND ce_strike_offset = 0) "
                                + "OR (ce_moneyness IN ('ITM','OTM') AND ce_strike_offset BETWEEN 1 AND 15)"),
                @CheckConstraint(name = "ck_user_strategies_pe_strike", constraint =
                        "(pe_moneyness IS NULL) "
                                + "OR (pe_moneyness = 'ATM' AND pe_strike_offset = 0) "
                                + "OR (pe_moneyness IN ('ITM','OTM') AND pe_strike_offset BETWEEN 1 AND 15)"),
                @CheckConstraint(name = "ck_user_strategies_sizing", constraint =
                        "base_lot > 0 AND averaging_count >= 0 AND averaging_count <= 10")
        })
public class UserStrategy {

    /** ITM and OTM each offer 15 depths; ATM offers exactly one, itself. */
    public static final int MAX_STRIKE_OFFSET = 15;

    /** More than this many adds is a runaway, not a strategy. */
    public static final int MAX_AVERAGING_COUNT = 10;

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The logic this runs. Never modified from here. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_id", nullable = false)
    private StrategyTemplate strategy;

    /** The user's own label. Unique per user, not platform-wide. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    // ------------------------------------------------------------ the market

    /**
     * The UNDERLYING the indicators run on - NIFTY, BANKNIFTY, SENSEX, a stock.
     * Whether that is an index or a stock is {@code symbols.instrument_type}, so
     * the sheet's INDEX/STOCK cell needs no column of its own.
     *
     * Nullable: a strategy may be tuned before its market is picked.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol_id")
    private Symbol symbol;

    /**
     * The candle the strategy evaluates on - '5m', '15m', '1h'. The sheet's
     * "Time frame" row, and part of the shared config's identity, so two users on
     * 5m NIFTY with the same indicator tuning share one computation and a user on
     * 15m does not.
     */
    @Column(name = "candle_duration", length = 20)
    private String candleDuration;

    /**
     * How often the entry condition is re-evaluated inside a candle. Execution
     * scope: it changes when you look, not what you compute, so it is deliberately
     * NOT part of the shared config's identity.
     */
    @Column(name = "trigger_duration", length = 20)
    private String triggerDuration;

    // -------------------------------------------------------- the instrument

    /** FUTURES | OPTION. Decides whether the CE and PE sides apply at all. */
    @Enumerated(EnumType.STRING)
    @Column(name = "derivative", nullable = false, length = 10)
    private Derivative derivative = Derivative.OPTION;

    /** Trade the call side. Both sides may run at once. */
    @Column(name = "ce_enabled", nullable = false)
    private boolean ceEnabled = false;

    /** ATM | ITM | OTM for the call. Null while the side is off. */
    @Enumerated(EnumType.STRING)
    @Column(name = "ce_moneyness", length = 3)
    private Moneyness ceMoneyness;

    /** 0 for ATM, 1..15 for ITM and OTM - "OTM3" is 3. */
    @Column(name = "ce_strike_offset", nullable = false)
    private int ceStrikeOffset = 0;

    /** Trade the put side, independently of the call. */
    @Column(name = "pe_enabled", nullable = false)
    private boolean peEnabled = false;

    /** ATM | ITM | OTM for the put, chosen separately from the call's. */
    @Enumerated(EnumType.STRING)
    @Column(name = "pe_moneyness", length = 3)
    private Moneyness peMoneyness;

    @Column(name = "pe_strike_offset", nullable = false)
    private int peStrikeOffset = 0;

    // ------------------------------------------------------------- the sizing

    /** FIXED | DOUBLE | CUMULATIVE - how each averaging entry is sized. */
    @Enumerated(EnumType.STRING)
    @Column(name = "lot_rule", nullable = false, length = 12)
    private LotRule lotRule = LotRule.FIXED;

    /** The first entry's size, in contracts. The sheet's "Base Lot" cell. */
    @Column(name = "base_lot", nullable = false)
    private int baseLot = 1;

    /** How many times the strategy may add to a losing position. 0 = never. */
    @Column(name = "averaging_count", nullable = false)
    private int averagingCount = 0;

    // -------------------------------------------------------------- the exits

    /** Stop loss, percent. Null means the strategy carries no stop of its own. */
    @Column(name = "sl_pct", precision = 6, scale = 2)
    private BigDecimal slPct;

    /** Take profit, percent. */
    @Column(name = "tp_pct", precision = 6, scale = 2)
    private BigDecimal tpPct;

    // ------------------------------------------------- the deployment defaults

    /*
     * What a deployment of this strategy starts on when the deploy call does not
     * say otherwise.
     *
     * These are DEFAULTS, not settings: the live value of each one is the column
     * of the same name on user_strategy_subscriptions, decided per account, and
     * changing one here never reaches a deployment that already exists. That is
     * the same rule as everywhere else on this row read backwards - the config
     * fields above move every broker at once precisely because a deployment does
     * not copy them, while these four are copied at deploy time and then belong
     * to the account.
     *
     * The point is a strategy authored as "mine runs live at 2x" deploying
     * without repeating itself on every call.
     */

    /** FIXED_QTY | CAPITAL_PERCENT | RISK_PERCENT. */
    @Column(name = "execution_mode", nullable = false, length = 20)
    private String executionMode = StrategySubscription.EXEC_FIXED_QTY;

    /** Scales baseLot on the account. 1 runs the ladder as configured. */
    @Column(name = "multiplier", precision = 20, scale = 8, nullable = false)
    private BigDecimal multiplier = BigDecimal.ONE;

    /** Capital to earmark per account, for the percent-based execution modes. */
    @Column(name = "capital_allocated", precision = 20, scale = 8)
    private BigDecimal capitalAllocated;

    /**
     * Paper | Live.
     *
     * Defaults to paper, and stays paper unless the author says otherwise - a
     * strategy that silently deployed live because a field was left unset is the
     * one mistake here that costs money.
     */
    @Column(name = "trade_mode", nullable = false, length = 10)
    private String tradeMode = StrategySubscription.MODE_PAPER;

    // ---------------------------------------------------------- the wiring

    /**
     * The dedup unit this configuration resolves to: strategy + symbol +
     * candle duration + the indicator tuning, content-addressed.
     *
     * Resolved on every save, so subscriptions reach it through this row rather
     * than holding a second copy of the same pointer. Null until the market is
     * picked, since there is nothing to hash without one.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_config_id")
    private SharedStrategyConfig sharedConfig;

    /**
     * The tuning of each indicator the template uses. A child table rather than
     * more columns because the SET of indicators is open - one strategy uses EMA,
     * the next uses EMA and RSI - and unlike CE/PE they are not named slots.
     */
    @OneToMany(mappedBy = "userStrategy", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<UserStrategyIndicator> indicators = new ArrayList<>();

    /** A shelf flag, not an execution one: deployments decide what runs. */
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

    // --------------------------------------------------------------- derived

    /** True once the market is picked - i.e. once this can be hashed and deployed. */
    public boolean isDeployable() {
        return symbol != null && candleDuration != null && sharedConfig != null;
    }

    /** "CE OTM3", "PE ATM", "FUTURES" - built the same way wherever a leg is shown. */
    public static String legLabel(String side, Moneyness moneyness, int strikeOffset) {
        if (moneyness == null) {
            return side;
        }
        return strikeOffset == 0 ? side + " " + moneyness : side + " " + moneyness + strikeOffset;
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

    public String getCandleDuration() { return candleDuration; }
    public void setCandleDuration(String candleDuration) { this.candleDuration = candleDuration; }

    public String getTriggerDuration() { return triggerDuration; }
    public void setTriggerDuration(String triggerDuration) { this.triggerDuration = triggerDuration; }

    public Derivative getDerivative() { return derivative; }
    public void setDerivative(Derivative derivative) { this.derivative = derivative; }

    public boolean isCeEnabled() { return ceEnabled; }
    public void setCeEnabled(boolean ceEnabled) { this.ceEnabled = ceEnabled; }

    public Moneyness getCeMoneyness() { return ceMoneyness; }
    public void setCeMoneyness(Moneyness ceMoneyness) { this.ceMoneyness = ceMoneyness; }

    public int getCeStrikeOffset() { return ceStrikeOffset; }
    public void setCeStrikeOffset(int ceStrikeOffset) { this.ceStrikeOffset = ceStrikeOffset; }

    public boolean isPeEnabled() { return peEnabled; }
    public void setPeEnabled(boolean peEnabled) { this.peEnabled = peEnabled; }

    public Moneyness getPeMoneyness() { return peMoneyness; }
    public void setPeMoneyness(Moneyness peMoneyness) { this.peMoneyness = peMoneyness; }

    public int getPeStrikeOffset() { return peStrikeOffset; }
    public void setPeStrikeOffset(int peStrikeOffset) { this.peStrikeOffset = peStrikeOffset; }

    public LotRule getLotRule() { return lotRule; }
    public void setLotRule(LotRule lotRule) { this.lotRule = lotRule; }

    public int getBaseLot() { return baseLot; }
    public void setBaseLot(int baseLot) { this.baseLot = baseLot; }

    public int getAveragingCount() { return averagingCount; }
    public void setAveragingCount(int averagingCount) { this.averagingCount = averagingCount; }

    public BigDecimal getSlPct() { return slPct; }
    public void setSlPct(BigDecimal slPct) { this.slPct = slPct; }

    public BigDecimal getTpPct() { return tpPct; }
    public void setTpPct(BigDecimal tpPct) { this.tpPct = tpPct; }

    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }

    public BigDecimal getMultiplier() { return multiplier; }
    public void setMultiplier(BigDecimal multiplier) { this.multiplier = multiplier; }

    public BigDecimal getCapitalAllocated() { return capitalAllocated; }
    public void setCapitalAllocated(BigDecimal capitalAllocated) { this.capitalAllocated = capitalAllocated; }

    public String getTradeMode() { return tradeMode; }
    public void setTradeMode(String tradeMode) { this.tradeMode = tradeMode; }

    public SharedStrategyConfig getSharedConfig() { return sharedConfig; }
    public void setSharedConfig(SharedStrategyConfig sharedConfig) { this.sharedConfig = sharedConfig; }

    public List<UserStrategyIndicator> getIndicators() { return indicators; }
    public void setIndicators(List<UserStrategyIndicator> indicators) { this.indicators = indicators; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
