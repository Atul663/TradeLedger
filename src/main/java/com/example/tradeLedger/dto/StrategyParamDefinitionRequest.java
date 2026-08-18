package com.example.tradeLedger.dto;

import java.util.Map;

/**
 * One tunable knob of a strategy - a row in {@code strategy_param_definitions}.
 *
 * These are the parameters a user actually sets. They reach an indicator through
 * the {@code $key} bindings in the strategy's rule tree, which is why this is
 * where an indicator's parameters are configured per strategy rather than in a
 * per-indicator parameter table.
 */
public class StrategyParamDefinitionRequest {

    private String parameterKey;

    /** int | decimal | bool | enum | timeframe | text */
    private String dataType;

    /** signal (hashed, shared) | execution (personal, never hashed) */
    private String scope;

    private String defaultValue;

    /** {@code {"min":2,"max":200}} / {@code {"options":[...]}} / {@code {"gt":"fast"}} */
    private Map<String, Object> validation;

    private String displayLabel;

    private Integer displayOrder;

    private Boolean required;

    public String getParameterKey() { return parameterKey; }
    public void setParameterKey(String parameterKey) { this.parameterKey = parameterKey; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public Map<String, Object> getValidation() { return validation; }
    public void setValidation(Map<String, Object> validation) { this.validation = validation; }

    public String getDisplayLabel() { return displayLabel; }
    public void setDisplayLabel(String displayLabel) { this.displayLabel = displayLabel; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
}
