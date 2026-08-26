package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.utils.JsonSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Validates and coerces the ONE schemaless thing a strategy stores: an
 * indicator's parameter values, against that indicator's own
 * {@code param_schema}.
 *
 * <pre>
 *   schema   {"k":{"type":"int","min":1,"max":300,"default":21},
 *             "d":{"type":"int","min":1,"max":300,"default":9,"lt":"k"}}
 *   submitted {"k":50}
 *   effective {"d":9,"k":50}      &lt;- d falls through to the schema default
 * </pre>
 *
 * Everything else a strategy has is a typed column with a database constraint
 * behind it. This exists because indicators are pluggable and their knobs are
 * therefore not knowable at schema-design time - so the check that a column would
 * have given for free is done here instead, on every write, with the same
 * outcome: bad data is a 400 and never reaches the table.
 *
 * The returned map is a {@link TreeMap}. It goes straight into the config hash,
 * and a hash over an unordered map would split the dedup silently.
 */
@Component
public class IndicatorParams {

    private final JsonSupport json;

    public IndicatorParams(JsonSupport json) {
        this.json = json;
    }

    /**
     * Merges submitted values over the schema's defaults and validates the result.
     *
     * @param submitted the user's values, keyed by schema key; null or empty means
     *                  "all defaults"
     * @return every parameter the indicator declares, coerced to its type, sorted
     * @throws StrategyValidationException listing every problem found, not just the first
     */
    public Map<String, Object> effective(Indicator indicator, Map<String, Object> submitted) {
        Map<String, Object> schema = json.toMap(indicator.getParamSchema());
        Map<String, Object> raw = submitted != null ? submitted : Map.of();
        List<String> errors = new ArrayList<>();

        for (String key : raw.keySet()) {
            if (!schema.containsKey(key)) {
                errors.add("Indicator '" + indicator.getName() + "' has no parameter '" + key
                        + "' - it declares " + schema.keySet());
            }
        }

        // Pass 1: defaults and type coercion. Cross-field rules need every value
        // resolved first, so they wait for pass 2.
        Map<String, Object> coerced = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> spec = asSpec(entry.getValue());
            Object value = raw.get(key);

            if (value == null || (value instanceof String text && text.isBlank())) {
                value = spec.get("default");
            }
            if (value == null) {
                errors.add("Indicator '" + indicator.getName() + "' parameter '" + key
                        + "' has no value and no default");
                continue;
            }
            try {
                coerced.put(key, coerce(indicator.getName(), key, spec, value));
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
            }
        }

        // Pass 2: ranges, option lists and the cross-field rules.
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String key = entry.getKey();
            if (coerced.containsKey(key)) {
                errors.addAll(checkConstraints(indicator.getName(), key,
                        asSpec(entry.getValue()), coerced.get(key), coerced));
            }
        }

        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }
        return new TreeMap<>(coerced);
    }

    /** The schema's defaults alone - what a freshly created strategy starts on. */
    public Map<String, Object> defaults(Indicator indicator) {
        return effective(indicator, Map.of());
    }

    /**
     * The schema as a form should read it: every parameter carrying a {@code label}.
     *
     * Filled on the way OUT rather than demanded on the way in, the same way
     * {@code FixedParameterOptions} fills a select's options. A label is
     * presentation - requiring one would 400 every catalogue entry written before
     * labels existed, and an indicator is the one thing on this platform an
     * operator plugs in by hand. A parameter that names none is labelled by its
     * key, which is what a form fell back to anyway.
     */
    public static Map<String, Object> labelled(Map<String, Object> paramSchema) {
        if (paramSchema == null) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        paramSchema.forEach((key, value) -> {
            if (!(value instanceof Map<?, ?> spec)) {
                out.put(key, value);
                return;
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            spec.forEach((k, v) -> copy.put(String.valueOf(k), v));
            Object label = copy.get(Indicator.KEY_LABEL);
            if (!(label instanceof String text) || text.isBlank()) {
                copy.put(Indicator.KEY_LABEL, key);
            }
            out.put(key, copy);
        });
        return out;
    }

    // ---------------------------------------------------------------- helpers

    @SuppressWarnings("unchecked")
    private Map<String, Object> asSpec(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private Object coerce(String indicatorName, String key, Map<String, Object> spec, Object value) {
        String type = String.valueOf(spec.getOrDefault("type", Indicator.TYPE_TEXT));
        String text = String.valueOf(value).trim();
        String where = "Indicator '" + indicatorName + "' parameter '" + key + "'";

        switch (type) {
            case Indicator.TYPE_INT -> {
                try {
                    BigDecimal decimal = new BigDecimal(text);
                    if (decimal.stripTrailingZeros().scale() > 0) {
                        throw new IllegalArgumentException(where + " must be a whole number, got " + text);
                    }
                    return decimal.longValueExact();
                } catch (NumberFormatException | ArithmeticException e) {
                    throw new IllegalArgumentException(where + " must be an integer, got " + text);
                }
            }
            case Indicator.TYPE_DECIMAL -> {
                try {
                    return new BigDecimal(text);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(where + " must be a number, got " + text);
                }
            }
            case Indicator.TYPE_BOOL -> {
                if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                    return Boolean.parseBoolean(text);
                }
                throw new IllegalArgumentException(where + " must be true or false, got " + text);
            }
            case Indicator.TYPE_ENUM, Indicator.TYPE_TEXT -> {
                return text;
            }
            default -> throw new IllegalArgumentException(
                    where + " declares unsupported type '" + type + "'");
        }
    }

    private List<String> checkConstraints(String indicatorName, String key, Map<String, Object> spec,
                                          Object value, Map<String, Object> allValues) {
        List<String> errors = new ArrayList<>();
        String where = "Indicator '" + indicatorName + "' parameter '" + key + "'";

        if (spec.get("options") instanceof List<?> options) {
            List<String> allowed = options.stream().map(String::valueOf).toList();
            if (!allowed.contains(String.valueOf(value))) {
                errors.add(where + " must be one of " + allowed + ", got " + value);
            }
        }

        BigDecimal numeric = asNumber(value);
        if (numeric == null) {
            return errors;
        }
        BigDecimal min = asNumber(spec.get("min"));
        if (min != null && numeric.compareTo(min) < 0) {
            errors.add(where + " must be >= " + min.toPlainString() + ", got " + numeric.toPlainString());
        }
        BigDecimal max = asNumber(spec.get("max"));
        if (max != null && numeric.compareTo(max) > 0) {
            errors.add(where + " must be <= " + max.toPlainString() + ", got " + numeric.toPlainString());
        }

        // Cross-field, within one indicator: {"lt":"k"} means d must stay under k.
        if (spec.get("gt") instanceof String otherKey) {
            BigDecimal other = asNumber(allValues.get(otherKey));
            if (other != null && numeric.compareTo(other) <= 0) {
                errors.add(where + " must be greater than '" + otherKey + "' ("
                        + numeric.toPlainString() + " vs " + other.toPlainString() + ")");
            }
        }
        if (spec.get("lt") instanceof String otherKey) {
            BigDecimal other = asNumber(allValues.get(otherKey));
            if (other != null && numeric.compareTo(other) >= 0) {
                errors.add(where + " must be less than '" + otherKey + "' ("
                        + numeric.toPlainString() + " vs " + other.toPlainString() + ")");
            }
        }
        return errors;
    }

    private BigDecimal asNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
