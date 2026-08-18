package com.example.tradeLedger.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Put a saved strategy to work: everything a subscription needs that a saved
 * strategy deliberately does not hold.
 *
 * The parameters are NOT repeated here - they come from the saved row. What is
 * supplied is the execution context: which account, how big, paper or live.
 *
 * {@code symbolId} / {@code symbol} + {@code exchangeCode} and {@code timeframe}
 * override the saved market, and are REQUIRED when the saved strategy is
 * params-only. Overriding them does not change the saved row.
 */
public class UserStrategySubscribeRequest {

    private UUID tradingAccountId;

    private UUID riskProfileId;

    /** Overrides the saved symbol; required if the saved strategy has none. */
    private UUID symbolId;

    private String symbol;

    private String exchangeCode;

    /** Overrides the saved timeframe; required if the saved strategy has none. */
    private String timeframe;

    private BigDecimal quantity;

    private BigDecimal multiplier;

    private BigDecimal lotSize;

    private BigDecimal capitalAllocated;

    /** FIXED_QTY | CAPITAL_PERCENT | RISK_PERCENT */
    private String executionMode;

    /** paper | live */
    private String tradeMode;

    public UUID getTradingAccountId() { return tradingAccountId; }
    public void setTradingAccountId(UUID tradingAccountId) { this.tradingAccountId = tradingAccountId; }

    public UUID getRiskProfileId() { return riskProfileId; }
    public void setRiskProfileId(UUID riskProfileId) { this.riskProfileId = riskProfileId; }

    public UUID getSymbolId() { return symbolId; }
    public void setSymbolId(UUID symbolId) { this.symbolId = symbolId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getExchangeCode() { return exchangeCode; }
    public void setExchangeCode(String exchangeCode) { this.exchangeCode = exchangeCode; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

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
