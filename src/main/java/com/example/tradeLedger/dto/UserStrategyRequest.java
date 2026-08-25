package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Create or update a strategy. One shape for both, because the semantics are the
 * same either way: a field that is present is applied, a field that is absent is
 * left alone - which on create means the column default.
 *
 * Everything except {@code indicators} is a typed column with a database
 * constraint behind it. {@code indicators[].params} is the one free-form part,
 * and it is validated against that indicator's {@code param_schema} on write, so
 * it is not a hiding place for unchecked data either.
 */
@Schema(name = "UserStrategyRequest",
        description = """
                One complete strategy configuration. On create, every absent field takes its \
                column default; on update, an absent field is left alone.

                The example is the spreadsheet verbatim: EMA High (K) 21 against EMA (D) 9 on \
                NIFTY 5-minute candles, traded as options one strike out of the money on BOTH \
                sides, sized 65 with a doubling ladder and two averaging entries.""")
public class UserStrategyRequest {

    @Schema(description = "The template being configured. Send this or strategyName. "
            + "Required on create, ignored on update.",
            example = "3f1b0c7e-9a41-4c2e-9f11-2b7d5a6e8c01")
    private UUID strategyId;

    @Schema(description = "Alternative to strategyId - strategy_templates.name is unique.",
            example = "EMA Averaging")
    private String strategyName;

    @Schema(description = "The caller's own label. Unique per user; defaults to the template name.",
            example = "NIFTY 21/9 both sides", maxLength = 100)
    private String name;

    @Schema(description = "Free text note.", example = "Sheet block 1")
    private String description;

    // ------------------------------------------------------------ the market

    @Schema(description = "The UNDERLYING the indicators run on. Send this, or symbol + "
            + "exchangeCode. INDEX vs STOCK is the symbol's own instrumentType.",
            example = "1a2b3c4d-5e6f-4a8b-9c0d-1e2f3a4b5c6d")
    private UUID symbolId;

    @Schema(description = "Alternative to symbolId, together with exchangeCode.", example = "NIFTY")
    private String symbol;

    @Schema(description = "Required when identifying the symbol by name - symbols are unique "
            + "per exchange, not globally.", example = "NSE")
    private String exchangeCode;

    @Schema(description = "The candle the strategy evaluates on. Part of the shared config's "
            + "identity, so two users on different candles never share a computation.",
            example = "5m", allowableValues = {"30s", "1m", "3m", "5m", "15m", "30m", "1h", "2h", "4h", "1d", "1w"})
    private String candleDuration;

    @Schema(description = "How often the entry condition is re-checked inside a candle. "
            + "Execution scope - never hashed.", example = "5m")
    private String triggerDuration;

    // -------------------------------------------------------- the instrument

    @Schema(description = "What the signal is traded through. FUTURES means no strike to choose "
            + "and both option sides must stay off.",
            example = "OPTION", allowableValues = {"FUTURES", "OPTION"}, defaultValue = "OPTION")
    private String derivative;

    @Schema(description = "Trade the call side. Setting ceMoneyness turns this on by itself.",
            example = "true", defaultValue = "false")
    private Boolean ceEnabled;

    @Schema(description = "Where the call's strike sits. Required while the call side is on.",
            example = "OTM", allowableValues = {"ATM", "ITM", "OTM"})
    private String ceMoneyness;

    @Schema(description = "How many strikes away for the call. Must be 0 for ATM and 1..15 "
            + "for ITM/OTM - there is no OTM0, that is what ATM is called.",
            example = "1", minimum = "0", maximum = "15", defaultValue = "0")
    private Integer ceStrikeOffset;

    @Schema(description = "Trade the put side, independently of the call.",
            example = "true", defaultValue = "false")
    private Boolean peEnabled;

    @Schema(description = "Where the put's strike sits, chosen separately from the call's.",
            example = "OTM", allowableValues = {"ATM", "ITM", "OTM"})
    private String peMoneyness;

    @Schema(description = "How many strikes away for the put.",
            example = "1", minimum = "0", maximum = "15", defaultValue = "0")
    private Integer peStrikeOffset;

    // ------------------------------------------------------------- the sizing

    @Schema(description = """
            How each averaging entry is sized, starting from baseLot. With baseLot 65 and \
            averagingCount 2: FIXED gives 65/65/65, DOUBLE gives 65/130/260, CUMULATIVE gives \
            65/130/195. The ladder is applied by the execution engine; this only records the choice.""",
            example = "DOUBLE", allowableValues = {"FIXED", "DOUBLE", "CUMULATIVE"}, defaultValue = "FIXED")
    private String lotRule;

    @Schema(description = "The first entry's size, in contracts.",
            example = "65", minimum = "1", defaultValue = "1")
    private Integer baseLot;

    @Schema(description = "How many times the strategy may add to a losing position. "
            + "A non-FIXED lotRule needs at least 1, or the ladder has nothing to climb.",
            example = "2", minimum = "0", maximum = "10", defaultValue = "0")
    private Integer averagingCount;

    // -------------------------------------------------------------- the exits

    @Schema(description = "Stop loss, percent. Null means the strategy carries no stop of its own.",
            example = "1.5", minimum = "0", exclusiveMinimum = true, maximum = "100")
    private BigDecimal slPct;

    @Schema(description = "Take profit, percent.",
            example = "3.0", minimum = "0", exclusiveMinimum = true, maximum = "100")
    private BigDecimal tpPct;

    // --------------------------------------------------------- the indicators

    @Schema(description = "Tuning for the indicators the template uses. Omit it entirely and "
            + "every indicator runs on its schema defaults.")
    private List<IndicatorTuning> indicators;

    @Schema(description = "Archive without deleting. An archived strategy cannot be deployed.",
            example = "true", defaultValue = "true")
    private Boolean active;

    /**
     * One indicator usage and the values it runs with.
     *
     * Addressed by id, or by name - {@code indicators.name} is unique.
     * {@code slot} is only needed when a template uses one indicator twice.
     */
    @Schema(name = "IndicatorTuning",
            description = "Values for one indicator, validated against its own param_schema.")
    public static class IndicatorTuning {

        @Schema(description = "The row id from a strategy response. The most precise way to "
                + "address an existing usage.",
                example = "7c9e6f10-3b2a-4d5c-8e1f-0a9b8c7d6e5f")
        private UUID userStrategyIndicatorId;

        @Schema(description = "The catalog indicator by id.",
                example = "b2e4a1c8-1f3d-4e6a-9b2c-5d7e8f0a1b2c")
        private UUID indicatorId;

        @Schema(description = "The catalog indicator by name - indicators.name is unique and "
                + "uppercased on save.", example = "EMA Averaging")
        private String indicatorName;

        @Schema(description = "Only needed when one template uses the same indicator twice.",
                example = "fast")
        private String slot;

        @Schema(description = "MERGED over what is stored, so sending one key changes one key. "
                + "Keys and bounds come from the indicator's paramSchema.",
                example = "{\"k\": 21, \"d\": 9}")
        private Map<String, Object> params;

        @Schema(description = "Park an optional indicator without losing its tuning. "
                + "A disabled indicator contributes nothing to the config hash.",
                example = "true")
        private Boolean enabled;

        public UUID getUserStrategyIndicatorId() { return userStrategyIndicatorId; }
        public void setUserStrategyIndicatorId(UUID id) { this.userStrategyIndicatorId = id; }

        public UUID getIndicatorId() { return indicatorId; }
        public void setIndicatorId(UUID indicatorId) { this.indicatorId = indicatorId; }

        public String getIndicatorName() { return indicatorName; }
        public void setIndicatorName(String indicatorName) { this.indicatorName = indicatorName; }

        public String getSlot() { return slot; }
        public void setSlot(String slot) { this.slot = slot; }

        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }

    public UUID getStrategyId() { return strategyId; }
    public void setStrategyId(UUID strategyId) { this.strategyId = strategyId; }

    public String getStrategyName() { return strategyName; }
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getSymbolId() { return symbolId; }
    public void setSymbolId(UUID symbolId) { this.symbolId = symbolId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getExchangeCode() { return exchangeCode; }
    public void setExchangeCode(String exchangeCode) { this.exchangeCode = exchangeCode; }

    public String getCandleDuration() { return candleDuration; }
    public void setCandleDuration(String candleDuration) { this.candleDuration = candleDuration; }

    public String getTriggerDuration() { return triggerDuration; }
    public void setTriggerDuration(String triggerDuration) { this.triggerDuration = triggerDuration; }

    public String getDerivative() { return derivative; }
    public void setDerivative(String derivative) { this.derivative = derivative; }

    public Boolean getCeEnabled() { return ceEnabled; }
    public void setCeEnabled(Boolean ceEnabled) { this.ceEnabled = ceEnabled; }

    public String getCeMoneyness() { return ceMoneyness; }
    public void setCeMoneyness(String ceMoneyness) { this.ceMoneyness = ceMoneyness; }

    public Integer getCeStrikeOffset() { return ceStrikeOffset; }
    public void setCeStrikeOffset(Integer ceStrikeOffset) { this.ceStrikeOffset = ceStrikeOffset; }

    public Boolean getPeEnabled() { return peEnabled; }
    public void setPeEnabled(Boolean peEnabled) { this.peEnabled = peEnabled; }

    public String getPeMoneyness() { return peMoneyness; }
    public void setPeMoneyness(String peMoneyness) { this.peMoneyness = peMoneyness; }

    public Integer getPeStrikeOffset() { return peStrikeOffset; }
    public void setPeStrikeOffset(Integer peStrikeOffset) { this.peStrikeOffset = peStrikeOffset; }

    public String getLotRule() { return lotRule; }
    public void setLotRule(String lotRule) { this.lotRule = lotRule; }

    public Integer getBaseLot() { return baseLot; }
    public void setBaseLot(Integer baseLot) { this.baseLot = baseLot; }

    public Integer getAveragingCount() { return averagingCount; }
    public void setAveragingCount(Integer averagingCount) { this.averagingCount = averagingCount; }

    public BigDecimal getSlPct() { return slPct; }
    public void setSlPct(BigDecimal slPct) { this.slPct = slPct; }

    public BigDecimal getTpPct() { return tpPct; }
    public void setTpPct(BigDecimal tpPct) { this.tpPct = tpPct; }

    public List<IndicatorTuning> getIndicators() { return indicators; }
    public void setIndicators(List<IndicatorTuning> indicators) { this.indicators = indicators; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
