package com.example.tradeLedger.dto;

import java.util.Map;

/**
 * Create / update body for {@code indicators}.
 *
 * {@code paramSchema} IS the indicator's parameter definition - the design stores
 * indicator parameters as a JSON schema on the indicator row, not as rows in a
 * separate parameter table:
 *
 * <pre>
 * { "name": "RSI",
 *   "paramSchema": {"period": {"type":"int", "min":2, "max":100}} }
 * </pre>
 *
 * Each entry must declare a {@code type} of int | decimal | bool | enum |
 * timeframe | text, and may add {@code min} / {@code max} / {@code options} /
 * {@code default}.
 */
public class IndicatorRequest {

    private String name;

    private Map<String, Object> paramSchema;

    private Boolean active;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Map<String, Object> getParamSchema() { return paramSchema; }
    public void setParamSchema(Map<String, Object> paramSchema) { this.paramSchema = paramSchema; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
