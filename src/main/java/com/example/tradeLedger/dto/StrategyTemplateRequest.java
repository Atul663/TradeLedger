package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Create / update body for {@code strategy_templates}.
 *
 * A template is logic and nothing else. There is no knob list to send: each
 * indicator the rule tree names declares its own parameters in its
 * {@code param_schema}, and every other setting a strategy has is a fixed column
 * on {@code user_strategies}, the same for every template.
 */
@Schema(name = "StrategyTemplateRequest",
        description = """
                A template is LOGIC and nothing else - a rule tree over indicators.

                There is no knob list to send: each indicator the tree names declares its own \
                parameters in its param_schema, and every other setting a strategy has \
                (instrument, strikes, ladder, exits) is a fixed column on user_strategies, \
                identical on every template.

                Rule-tree grammar: an indicator node is {"ind":"<NAME>","params":{...}}, and a \
                value of "$key" binds to that key in the indicator's own param_schema. Nodes nest \
                freely under any object or array.""")
public class StrategyTemplateRequest {

    @Schema(description = "Unique across the platform.", example = "RSI Reversal", maxLength = 100)
    private String name;

    @Schema(example = "Long when RSI leaves oversold.")
    private String description;

    @Schema(description = "Bump when the rule tree changes meaning.",
            example = "1", minimum = "1", defaultValue = "1")
    private Integer version;

    @Schema(example = "true", defaultValue = "true")
    private Boolean active;

    @Schema(description = "Conditions over indicators, with $key bindings.",
            example = "{\"entry\": {\"ind\": \"RSI\", \"params\": {\"period\": \"$period\"}}}")
    private Map<String, Object> ruleTree;

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
}
