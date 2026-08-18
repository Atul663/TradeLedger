package com.example.tradeLedger.dto;

import java.util.List;
import java.util.UUID;

/**
 * Customize a global strategy template and save it under the caller's own name.
 *
 * <pre>
 * { "strategyId":"...",
 *   "name":"My fast EMA",
 *   "timeframe":"5m",
 *   "overrides":[ {"indicatorId":"...", "parameterId":1, "value":"13"},
 *                 {"parameterId":4, "value":"2.5"} ] }
 * </pre>
 *
 * The indicator rows are created automatically from the template's own
 * indicators, so a body with no {@code overrides} at all saves a faithful copy
 * sitting entirely on global defaults - and only the knobs listed in
 * {@code overrides} get a row of their own.
 *
 * Nothing in the global catalog is written. {@code overrides} addresses knobs by
 * id, never by name.
 */
public class UserStrategyRequest {

    /** The global template being customized. */
    private UUID strategyId;

    /** Alternative to strategyId - strategy_templates.name is unique. */
    private String strategyName;

    /** The caller's own label. Unique per user; defaults to the template name. */
    private String name;

    private String description;

    private UUID symbolId;

    /** Alternative to symbolId, together with exchangeCode. */
    private String symbol;

    private String exchangeCode;

    private String timeframe;

    /** Only the values that differ from the global defaults. */
    private List<ParameterOverrideRequest> overrides;

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

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    public List<ParameterOverrideRequest> getOverrides() { return overrides; }
    public void setOverrides(List<ParameterOverrideRequest> overrides) { this.overrides = overrides; }
}
