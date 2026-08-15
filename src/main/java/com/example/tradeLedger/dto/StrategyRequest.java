package com.example.tradeLedger.dto;

import java.util.List;
import java.util.Map;

/**
 * Create / update body for {@code strategies}.
 *
 * <pre>
 * {
 *   "name": "RSI Reversal",
 *   "description": "Long when RSI leaves oversold.",
 *   "ruleTree": {"entry":{"cross_above":[{"ind":"RSI","params":{"period":"$rsiLen"}},
 *                                        {"const": "$oversold"}]}},
 *   "params": [
 *     {"parameterKey":"rsiLen","dataType":"int","scope":"signal",
 *      "defaultValue":"14","validation":{"min":2,"max":100},"displayOrder":1}
 *   ]
 * }
 * </pre>
 *
 * {@code params} is optional on create and, when supplied on update, REPLACES the
 * strategy's knob set. Knobs can also be managed individually through
 * {@code /api/v1/strategies/{id}/params}.
 */
public class StrategyRequest {

    private String name;

    private String description;

    private Integer version;

    private Boolean active;

    /** Conditions over indicators with {@code $key} bindings. */
    private Map<String, Object> ruleTree;

    private List<StrategyParamDefRequest> params;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Map<String, Object> getRuleTree() { return ruleTree; }
    public void setRuleTree(Map<String, Object> ruleTree) { this.ruleTree = ruleTree; }

    public List<StrategyParamDefRequest> getParams() { return params; }
    public void setParams(List<StrategyParamDefRequest> params) { this.params = params; }
}
