package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.entity.StrategyParamDef;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Validates a flat parameter map against a strategy's knob definitions and splits
 * it by scope.
 *
 * Nothing here is EMA-specific: the rules come from strategy_param_def rows, so
 * an RSI or SuperTrend strategy validates through the same code path the day its
 * rows are inserted.
 */
@Component
public class StrategyParamValidator {

    private final ObjectMapper objectMapper;

    public StrategyParamValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Coerced, defaulted parameters split into the two scopes. */
    public static class ValidatedParams {
        private final Map<String, Object> signal;
        private final Map<String, Object> execution;

        ValidatedParams(Map<String, Object> signal, Map<String, Object> execution) {
            this.signal = signal;
            this.execution = execution;
        }

        /** Sorted - goes into the config hash. */
        public Map<String, Object> getSignal() { return signal; }

        /** Sorted - personal, stored on the subscription. */
        public Map<String, Object> getExecution() { return execution; }
    }

    public ValidatedParams validate(List<StrategyParamDef> defs, Map<String, Object> submitted) {
        if (defs == null || defs.isEmpty()) {
            throw new StrategyValidationException("Strategy has no parameter definitions configured");
        }
        Map<String, Object> raw = submitted != null ? submitted : Map.of();
        List<String> errors = new ArrayList<>();

        Map<String, StrategyParamDef> defByKey = new LinkedHashMap<>();
        defs.forEach(d -> defByKey.put(d.getParameterKey(), d));

        for (String key : raw.keySet()) {
            if (!defByKey.containsKey(key)) {
                errors.add("Unknown parameter '" + key + "'");
            }
        }

        // Pass 1: presence, defaults and type coercion.
        Map<String, Object> coerced = new LinkedHashMap<>();
        for (StrategyParamDef def : defs) {
            String key = def.getParameterKey();
            Object value = raw.get(key);

            if (value == null || (value instanceof String s && s.isBlank())) {
                if (def.getDefaultValue() != null) {
                    value = def.getDefaultValue();
                } else if (def.isRequired()) {
                    errors.add("Missing required parameter '" + key + "'");
                    continue;
                } else {
                    continue;
                }
            }
            try {
                coerced.put(key, coerce(def, value));
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
            }
        }

        // Pass 2: value constraints, including cross-field rules that need pass 1 complete.
        for (StrategyParamDef def : defs) {
            String key = def.getParameterKey();
            if (!coerced.containsKey(key)) {
                continue;
            }
            errors.addAll(checkConstraints(def, coerced.get(key), coerced));
        }

        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }

        Map<String, Object> signal = new TreeMap<>();
        Map<String, Object> execution = new TreeMap<>();
        for (StrategyParamDef def : defs) {
            Object value = coerced.get(def.getParameterKey());
            if (value == null) {
                continue;
            }
            if (StrategyParamDef.SCOPE_SIGNAL.equals(def.getScope())) {
                signal.put(def.getParameterKey(), value);
            } else {
                execution.put(def.getParameterKey(), value);
            }
        }
        return new ValidatedParams(signal, execution);
    }

    private Object coerce(StrategyParamDef def, Object value) {
        String key = def.getParameterKey();
        String text = String.valueOf(value).trim();

        switch (def.getDataType()) {
            case "int" -> {
                try {
                    BigDecimal decimal = new BigDecimal(text);
                    if (decimal.stripTrailingZeros().scale() > 0) {
                        throw new IllegalArgumentException("Parameter '" + key + "' must be a whole number, got " + text);
                    }
                    return decimal.longValueExact();
                } catch (NumberFormatException | ArithmeticException e) {
                    throw new IllegalArgumentException("Parameter '" + key + "' must be an integer, got " + text);
                }
            }
            case "decimal" -> {
                try {
                    return new BigDecimal(text);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Parameter '" + key + "' must be a number, got " + text);
                }
            }
            case "bool" -> {
                if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                    return Boolean.parseBoolean(text);
                }
                throw new IllegalArgumentException("Parameter '" + key + "' must be true or false, got " + text);
            }
            case "enum", "timeframe", "text" -> {
                return text;
            }
            default -> throw new IllegalArgumentException(
                    "Parameter '" + key + "' has unsupported data type '" + def.getDataType() + "'");
        }
    }

    private List<String> checkConstraints(StrategyParamDef def, Object value, Map<String, Object> allValues) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> rules = readJson(def.getValidation());
        if (rules.isEmpty()) {
            return errors;
        }
        String key = def.getParameterKey();

        if (rules.get("options") instanceof List<?> options) {
            List<String> allowed = options.stream().map(String::valueOf).toList();
            if (!allowed.contains(String.valueOf(value))) {
                errors.add("Parameter '" + key + "' must be one of " + allowed + ", got " + value);
            }
        }

        BigDecimal numeric = asNumber(value);
        if (numeric == null) {
            return errors;
        }

        BigDecimal min = asNumber(rules.get("min"));
        if (min != null && numeric.compareTo(min) < 0) {
            errors.add("Parameter '" + key + "' must be >= " + min.toPlainString() + ", got " + numeric.toPlainString());
        }
        BigDecimal max = asNumber(rules.get("max"));
        if (max != null && numeric.compareTo(max) > 0) {
            errors.add("Parameter '" + key + "' must be <= " + max.toPlainString() + ", got " + numeric.toPlainString());
        }

        // Cross-field: {"gt":"fast"} means slow must exceed fast.
        if (rules.get("gt") instanceof String otherKey) {
            BigDecimal other = asNumber(allValues.get(otherKey));
            if (other != null && numeric.compareTo(other) <= 0) {
                errors.add("Parameter '" + key + "' must be greater than '" + otherKey
                        + "' (" + numeric.toPlainString() + " vs " + other.toPlainString() + ")");
            }
        }
        if (rules.get("lt") instanceof String otherKey) {
            BigDecimal other = asNumber(allValues.get(otherKey));
            if (other != null && numeric.compareTo(other) >= 0) {
                errors.add("Parameter '" + key + "' must be less than '" + otherKey
                        + "' (" + numeric.toPlainString() + " vs " + other.toPlainString() + ")");
            }
        }
        return errors;
    }

    private BigDecimal asNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
