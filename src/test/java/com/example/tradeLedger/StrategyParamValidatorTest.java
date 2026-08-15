package com.example.tradeLedger;

import com.example.tradeLedger.entity.StrategyParamDef;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.serviceImpl.StrategyParamValidator;
import com.example.tradeLedger.serviceImpl.StrategyParamValidator.ValidatedParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parameter validation is entirely data-driven: every rule below comes from
 * {@code strategy_param_defs} rows, not from EMA-specific code. An RSI or
 * SuperTrend strategy validates through this same path the day its rows are
 * inserted, which is the property that lets new strategies ship as INSERTs.
 */
class StrategyParamValidatorTest {

    private final StrategyParamValidator validator = new StrategyParamValidator(new ObjectMapper());

    /** The EMA Crossover knob set, exactly as seeded. */
    private static List<StrategyParamDef> emaCrossoverDefs() {
        return List.of(
                def("fast", "int", "signal", "9", "{\"min\":2,\"max\":200}", 1),
                def("slow", "int", "signal", "21", "{\"min\":3,\"max\":300,\"gt\":\"fast\"}", 2),
                def("sl_pct", "decimal", "execution", "1.5", "{\"min\":0.1,\"max\":20}", 3),
                def("tp_pct", "decimal", "execution", "3.0", "{\"min\":0.1,\"max\":50}", 4));
    }

    private static StrategyParamDef def(String key, String dataType, String scope,
                                        String defaultValue, String validation, int order) {
        StrategyParamDef def = new StrategyParamDef();
        def.setParameterKey(key);
        def.setDataType(dataType);
        def.setScope(scope);
        def.setDefaultValue(defaultValue);
        def.setValidation(validation);
        def.setDisplayOrder(order);
        def.setRequired(true);
        return def;
    }

    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    @Test
    void scopeSplitsParamsIntoSharedAndPersonalBuckets() {
        ValidatedParams result = validator.validate(emaCrossoverDefs(),
                params("fast", 9, "slow", 21, "sl_pct", 1.5, "tp_pct", 3.0));

        assertEquals(java.util.Set.of("fast", "slow"), result.getSignal().keySet(),
                "signal scope is what gets hashed into the shared instance");
        assertEquals(java.util.Set.of("sl_pct", "tp_pct"), result.getExecution().keySet(),
                "execution scope stays personal and never reaches the hash");
    }

    @Test
    void missingParamsFallBackToTheirDefaults() {
        ValidatedParams result = validator.validate(emaCrossoverDefs(), Map.of());

        assertEquals(9L, result.getSignal().get("fast"));
        assertEquals(21L, result.getSignal().get("slow"));
        assertEquals(new BigDecimal("1.5"), result.getExecution().get("sl_pct"));
    }

    @Test
    void rangeViolationsAreReportedTogether() {
        StrategyValidationException e = assertThrows(StrategyValidationException.class,
                () -> validator.validate(emaCrossoverDefs(), params("fast", 1, "slow", 5000)));

        assertEquals(2, e.getErrors().size(), e.getErrors().toString());
        assertTrue(e.getErrors().stream().anyMatch(m -> m.contains("'fast'") && m.contains(">=")));
        assertTrue(e.getErrors().stream().anyMatch(m -> m.contains("'slow'") && m.contains("<=")));
    }

    @Test
    void crossFieldRuleRejectsSlowBelowFast() {
        StrategyValidationException e = assertThrows(StrategyValidationException.class,
                () -> validator.validate(emaCrossoverDefs(), params("fast", 21, "slow", 9)));

        assertTrue(e.getErrors().stream().anyMatch(m -> m.contains("greater than")), e.getErrors().toString());
    }

    @Test
    void unknownParameterIsRejectedRatherThanIgnored() {
        StrategyValidationException e = assertThrows(StrategyValidationException.class,
                () -> validator.validate(emaCrossoverDefs(), params("fast", 9, "slow", 21, "leverage", 5)));

        assertTrue(e.getErrors().stream().anyMatch(m -> m.contains("leverage")), e.getErrors().toString());
    }

    @Test
    void intTypeRejectsFractionalValues() {
        StrategyValidationException e = assertThrows(StrategyValidationException.class,
                () -> validator.validate(emaCrossoverDefs(), params("fast", 9.5, "slow", 21)));

        assertTrue(e.getErrors().stream().anyMatch(m -> m.contains("whole number")), e.getErrors().toString());
    }

    @Test
    void enumTypeAcceptsOnlyDeclaredOptions() {
        List<StrategyParamDef> defs = List.of(
                def("side", "enum", "signal", "LONG", "{\"options\":[\"LONG\",\"SHORT\"]}", 1));

        assertEquals("SHORT", validator.validate(defs, params("side", "SHORT")).getSignal().get("side"));

        StrategyValidationException e = assertThrows(StrategyValidationException.class,
                () -> validator.validate(defs, params("side", "SIDEWAYS")));
        assertTrue(e.getErrors().stream().anyMatch(m -> m.contains("must be one of")), e.getErrors().toString());
    }

    @Test
    void requiredParamWithoutDefaultMustBeSupplied() {
        StrategyParamDef required = def("period", "int", "signal", null, null, 1);
        StrategyValidationException e = assertThrows(StrategyValidationException.class,
                () -> validator.validate(List.of(required), Map.of()));

        assertTrue(e.getErrors().stream().anyMatch(m -> m.contains("Missing required")), e.getErrors().toString());
    }

    @Test
    void strategyWithoutKnobsIsRejected() {
        assertThrows(StrategyValidationException.class, () -> validator.validate(List.of(), Map.of()));
    }
}
