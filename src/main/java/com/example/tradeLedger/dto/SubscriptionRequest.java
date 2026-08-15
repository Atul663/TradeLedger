package com.example.tradeLedger.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Subscribe the authenticated user to a strategy with their own parameters.
 *
 * {@code params} carries BOTH scopes flat, exactly as an auto-rendered form
 * produces them; the server splits them using {@code strategy_param_defs.scope}
 * and sends signal params to the (shared, hashed) instance and execution params
 * to this (personal) subscription:
 *
 * <pre>
 * { "strategyId":"...", "symbolId":"...", "tradingAccountId":"...",
 *   "timeframe":"5m",
 *   "params":{"fast":9,"slow":21,"sl_pct":1.5,"tp_pct":3.0},
 *   "quantity":50, "tradeMode":"paper" }
 * </pre>
 *
 * {@code strategyName} may be sent instead of {@code strategyId}, and
 * {@code symbol} + {@code exchangeCode} instead of {@code symbolId}.
 */
public class SubscriptionRequest {

    private UUID strategyId;

    /** Alternative to strategyId - strategies.name is unique. */
    private String strategyName;

    private UUID symbolId;

    /** Alternative to symbolId, together with exchangeCode - symbols are unique per exchange. */
    private String symbol;

    private String exchangeCode;

    private UUID tradingAccountId;

    private UUID riskProfileId;

    private String timeframe;

    private Map<String, Object> params;

    private BigDecimal quantity;

    private BigDecimal multiplier;

    private BigDecimal lotSize;

    private BigDecimal capitalAllocated;

    /** FIXED_QTY | CAPITAL_PERCENT | RISK_PERCENT */
    private String executionMode;

    /** paper | live */
    private String tradeMode;

    public UUID getStrategyId() { return strategyId; }
    public void setStrategyId(UUID strategyId) { this.strategyId = strategyId; }

    public String getStrategyName() { return strategyName; }
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }

    public UUID getSymbolId() { return symbolId; }
    public void setSymbolId(UUID symbolId) { this.symbolId = symbolId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getExchangeCode() { return exchangeCode; }
    public void setExchangeCode(String exchangeCode) { this.exchangeCode = exchangeCode; }

    public UUID getTradingAccountId() { return tradingAccountId; }
    public void setTradingAccountId(UUID tradingAccountId) { this.tradingAccountId = tradingAccountId; }

    public UUID getRiskProfileId() { return riskProfileId; }
    public void setRiskProfileId(UUID riskProfileId) { this.riskProfileId = riskProfileId; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }

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

    public String getTradeMode() { return tradeMode; }
    public void setTradeMode(String tradeMode) { this.tradeMode = tradeMode; }
}
