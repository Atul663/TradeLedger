package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Create / update a fixed-parameter descriptor.
 *
 * PUT is partial: an absent field keeps its stored value. {@code description},
 * {@code defaultValue}, {@code validation} and {@code paramGroup} are the
 * nullable ones - send an empty string, or an empty object for validation, to
 * clear them.
 */
@Schema(name = "FixedParameterRequest",
        description = """
                One FIXED knob: its name, label, type, default and bounds.

                A DESCRIPTOR, not a value. The value of a fixed parameter is a typed column on \
                user_strategies or user_strategy_subscriptions and is written through those \
                APIs; this row is what lets a form render the field - its label, its type, the \
                default to pre-fill, the bounds to enforce, and where it sits on the form.

                name is the machine key and is UNIQUE case-insensitively; by convention it is \
                the API field name of the column it describes, so a form can bind the two \
                without a mapping. defaultValue is text whatever the type is, and is parsed \
                against dataType and validation on save - an int default that is not an \
                integer, or an enum default that is not one of its options, is refused.""")
public class FixedParameterRequest {

    @Schema(description = "The machine key. UNIQUE (case-insensitive), and by convention the "
            + "API field name of the column it describes.",
            example = "slPct", maxLength = 100)
    private String name;

    @Schema(description = "What a human sees next to the field.",
            example = "SL %", maxLength = 100)
    private String label;

    @Schema(description = "Help text shown under the field.",
            example = "Percent move against the position that closes it. Empty means the "
                    + "strategy carries no stop of its own.")
    private String description;

    @Schema(description = "How the value is read. An enum needs options in validation.",
            example = "decimal",
            allowableValues = {"int", "decimal", "bool", "enum", "timeframe", "text", "symbol", "exchange"})
    private String dataType;

    @Schema(description = "signal knobs are part of a strategy's shared identity, execution "
            + "knobs are personal and never enter the config hash.",
            example = "execution", allowableValues = {"signal", "execution"},
            defaultValue = "execution")
    private String scope;

    @Schema(description = "What a form pre-fills. Text whatever the type is, and parsed "
            + "against dataType on save.",
            example = "2.5")
    private String defaultValue;

    @Schema(description = "The bounds a form should enforce: min/max for a number, options "
            + "for an enum (required for one).",
            example = "{\"min\": 0, \"max\": 100}")
    private Map<String, Object> validation;

    @Schema(description = "The section of the form it belongs to.",
            example = "Exits", maxLength = 50)
    private String paramGroup;

    @Schema(description = "Position within its group. Ties break on name.",
            example = "1", minimum = "0", defaultValue = "0")
    private Integer displayOrder;

    @Schema(description = "Whether a form must refuse to submit without a value.",
            example = "false", defaultValue = "false")
    private Boolean required;

    @Schema(description = "An inactive descriptor is hidden from forms without being deleted.",
            example = "true", defaultValue = "true")
    private Boolean active;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public Map<String, Object> getValidation() { return validation; }
    public void setValidation(Map<String, Object> validation) { this.validation = validation; }

    public String getParamGroup() { return paramGroup; }
    public void setParamGroup(String paramGroup) { this.paramGroup = paramGroup; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
