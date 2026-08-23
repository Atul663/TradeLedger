package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Create / update an indicator. Its {@code paramSchema} is its ENTIRE parameter
 * declaration - there is no parameter table behind it.
 */
@Schema(name = "IndicatorRequest",
        description = """
                An indicator and the SHAPE of its knobs. The schema IS the catalog - there is no \
                parameter table behind it, so declaring a knob is an edit to this row.

                Each entry needs a type (int, decimal, bool, enum or text) and a default - with \
                no catalog behind it, the default is the only thing that can say what applies to \
                a user who never touches the knob. min/max bound a number, options is required \
                for an enum, and gt/lt name another key of the SAME indicator for a cross-field \
                rule.

                The name is uppercased on save; rule trees match it by exact string.""")
public class IndicatorRequest {

    @Schema(description = "Uppercased on save. Unique.", example = "SUPERTREND", maxLength = 50)
    private String name;

    @Schema(description = "The whole parameter declaration.",
            example = "{\"period\": {\"type\":\"int\",\"min\":1,\"max\":100,\"default\":10}, "
                    + "\"multiplier\": {\"type\":\"decimal\",\"min\":0.5,\"max\":10,\"default\":3.0}, "
                    + "\"source\": {\"type\":\"enum\",\"options\":[\"close\",\"hl2\",\"hlc3\"],"
                    + "\"default\":\"close\"}}")
    private Map<String, Object> paramSchema;

    @Schema(description = "Inactive indicators fail rule-tree validation.",
            example = "true", defaultValue = "true")
    private Boolean active;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Map<String, Object> getParamSchema() { return paramSchema; }
    public void setParamSchema(Map<String, Object> paramSchema) { this.paramSchema = paramSchema; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
