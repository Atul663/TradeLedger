package com.example.tradeLedger.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Partial update of a subscription. Every field is optional; only what is present
 * changes.
 *
 * {@code params} is MERGED over the current effective parameters, so
 * {@code {"fast":13}} is a valid body. Signal-scope changes never mutate a
 * strategy instance - they repoint this subscription at the instance for the
 * resulting config, creating it only if nobody already runs that exact math.
 * Execution-scope changes stay local to this row.
 */
public class StrategySubscriptionUpdateRequest {

    private Map<String, Object> params;

    private BigDecimal quantity;

    private BigDecimal multiplier;

    private BigDecimal lotSize;

    private BigDecimal capitalAllocated;

    private String executionMode;

    private String tradeMode;

    private UUID riskProfileId;

    private Boolean active;

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

    public UUID getRiskProfileId() { return riskProfileId; }
    public void setRiskProfileId(UUID riskProfileId) { this.riskProfileId = riskProfileId; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
