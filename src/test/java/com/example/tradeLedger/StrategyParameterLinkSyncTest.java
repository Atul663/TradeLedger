package com.example.tradeLedger;

import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.entity.IndicatorParameterLink;
import com.example.tradeLedger.entity.Parameter;
import com.example.tradeLedger.entity.StrategyTemplate;
import com.example.tradeLedger.entity.StrategyParamDefinition;
import com.example.tradeLedger.entity.StrategyParameterLink;
import com.example.tradeLedger.serviceImpl.StrategyParameterLinkSync;
import com.example.tradeLedger.serviceImpl.StrategyParameterLinkSync.DerivedDef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The StrategyTemplate - Indicator - Parameter model, exercised through the one function
 * that turns it into the engine's flat knob set.
 *
 * These are the relationship tests: they assert that ownership is preserved
 * (indicator parameters stay signal scope, strategy parameters stay execution
 * scope), that per-usage overrides win over catalog values, and that a parameter
 * shared by two indicators produces one knob rather than two.
 *
 * Pure unit tests - no Spring context, no database. The derivation is a static
 * function of its inputs precisely so this is possible.
 */
class StrategyParameterLinkSyncTest {

    // ------------------------------------------------------------- fixtures

    private static Parameter parameter(long id, String code, String name, String dataType,
                                       String scope, String defaultValue, String validation,
                                       boolean universal) {
        Parameter parameter = new Parameter();
        parameter.setId(id);
        parameter.setCode(code);
        parameter.setName(name);
        parameter.setDataType(dataType);
        parameter.setScope(scope);
        parameter.setDefaultValue(defaultValue);
        parameter.setValidation(validation);
        parameter.setUniversal(universal);
        return parameter;
    }

    private static Parameter k() {
        return parameter(1L, "k", "K", "int", Parameter.SCOPE_SIGNAL, "9", "{\"min\":1,\"max\":300}", false);
    }

    private static Parameter d() {
        return parameter(2L, "d", "D", "int", Parameter.SCOPE_SIGNAL, "21",
                "{\"min\":1,\"max\":300,\"gt\":\"k\"}", false);
    }

    private static Parameter period() {
        return parameter(3L, "period", "Period", "int", Parameter.SCOPE_SIGNAL, "14",
                "{\"min\":2,\"max\":300}", false);
    }

    private static Parameter sl() {
        return parameter(4L, "sl", "SL", "decimal", Parameter.SCOPE_EXECUTION, "1.5",
                "{\"min\":0.1,\"max\":50}", true);
    }

    private static Parameter tp() {
        return parameter(5L, "tp", "TP", "decimal", Parameter.SCOPE_EXECUTION, "3.0",
                "{\"min\":0.1,\"max\":100}", true);
    }

    private static Parameter quantity() {
        return parameter(6L, "quantity", "Quantity", "int", Parameter.SCOPE_EXECUTION, "1",
                "{\"min\":1,\"max\":1000000}", true);
    }

    private static Parameter candleDuration() {
        return parameter(7L, "candle_duration", "Candle Duration", "timeframe",
                Parameter.SCOPE_EXECUTION, "5m", "{\"options\":[\"1m\",\"5m\",\"15m\"]}", true);
    }

    private static Parameter triggerDuration() {
        return parameter(8L, "trigger_duration", "Trigger Duration", "timeframe",
                Parameter.SCOPE_EXECUTION, "5m", "{\"options\":[\"1m\",\"5m\",\"15m\"]}", true);
    }

    private static Indicator indicator(String name) {
        Indicator def = new Indicator();
        def.setName(name);
        return def;
    }

    private static IndicatorParameterLink indicatorLink(Indicator indicator, Parameter parameter,
                                                    String defaultOverride, String validationOverride,
                                                    int order) {
        IndicatorParameterLink link = new IndicatorParameterLink();
        link.setIndicator(indicator);
        link.setParameter(parameter);
        link.setDefaultValue(defaultOverride);
        link.setValidation(validationOverride);
        link.setDisplayOrder(order);
        link.setRequired(true);
        return link;
    }

    private static StrategyParameterLink strategyLink(Parameter parameter, int order) {
        StrategyParameterLink link = new StrategyParameterLink();
        link.setStrategy(new StrategyTemplate());
        link.setParameter(parameter);
        link.setDisplayOrder(order);
        link.setRequired(true);
        return link;
    }

    /** The seeded EMA Crossover hierarchy, exactly as ControlPlaneSeeder builds it. */
    private static List<DerivedDef> emaCrossover() {
        Indicator crossover = indicator("EMA CROSSOVER");
        return StrategyParameterLinkSync.desiredDefs(
                List.of(indicatorLink(crossover, k(), null, null, 1),
                        indicatorLink(crossover, d(), null, null, 2)),
                List.of(strategyLink(sl(), 101),
                        strategyLink(tp(), 102),
                        strategyLink(quantity(), 103),
                        strategyLink(candleDuration(), 104),
                        strategyLink(triggerDuration(), 105)));
    }

    private static Map<String, DerivedDef> byKey(List<DerivedDef> defs) {
        return defs.stream().collect(Collectors.toMap(DerivedDef::parameterKey, Function.identity()));
    }

    // ---------------------------------------------------------------- tests

    @Test
    void strategyGathersItsIndicatorParameterLinksAndItsOwn() {
        List<String> keys = emaCrossover().stream().map(DerivedDef::parameterKey).toList();

        assertEquals(List.of("k", "d", "sl", "tp", "quantity", "candle_duration", "trigger_duration"), keys,
                "indicator parameters lead, strategy parameters follow, both in link order");
    }

    @Test
    void ownershipDecidesScopeSoDedupStaysIntact() {
        Map<String, DerivedDef> defs = byKey(emaCrossover());

        assertEquals(StrategyParamDefinition.SCOPE_SIGNAL, defs.get("k").scope());
        assertEquals(StrategyParamDefinition.SCOPE_SIGNAL, defs.get("d").scope());

        for (String code : List.of("sl", "tp", "quantity", "candle_duration", "trigger_duration")) {
            assertEquals(StrategyParamDefinition.SCOPE_EXECUTION, defs.get(code).scope(),
                    code + " configures execution and must never enter the config hash");
        }
    }

    @Test
    void catalogValuesFlowThroughWhenTheLinkOverridesNothing() {
        DerivedDef k = byKey(emaCrossover()).get("k");

        assertEquals("K", k.displayLabel(), "the catalog name becomes the form label");
        assertEquals("int", k.dataType());
        assertEquals("9", k.defaultValue());
        assertEquals("{\"min\":1,\"max\":300}", k.validation());
    }

    @Test
    void perUsageOverridesWinOverCatalogValues() {
        Indicator rsi = indicator("RSI");
        List<DerivedDef> defs = StrategyParameterLinkSync.desiredDefs(
                List.of(indicatorLink(rsi, period(), "14", "{\"min\":2,\"max\":100}", 1)),
                List.of());

        assertEquals(1, defs.size());
        assertEquals("{\"min\":2,\"max\":100}", defs.get(0).validation(),
                "RSI narrows the shared period parameter without forking the catalog row");
    }

    @Test
    void oneParameterSharedByTwoIndicatorsIsOneKnob() {
        // The point of a catalog: EMA and RSI both take `period`, from one row.
        Parameter shared = period();
        List<DerivedDef> defs = StrategyParameterLinkSync.desiredDefs(
                List.of(indicatorLink(indicator("EMA"), shared, "9", "{\"min\":2,\"max\":300}", 1),
                        indicatorLink(indicator("RSI"), shared, "14", "{\"min\":2,\"max\":100}", 1)),
                List.of());

        assertEquals(1, defs.size(), "the code is the knob identity, so the same parameter collapses");
        assertEquals("9", defs.get(0).defaultValue(), "first link wins, in link order");
    }

    @Test
    void aStrategyWithNoIndicatorStillGetsItsUniversalParameters() {
        List<DerivedDef> defs = StrategyParameterLinkSync.desiredDefs(
                List.of(), List.of(strategyLink(sl(), 101), strategyLink(tp(), 102)));

        assertEquals(List.of("sl", "tp"), defs.stream().map(DerivedDef::parameterKey).toList());
        assertTrue(defs.stream().allMatch(def -> StrategyParamDefinition.SCOPE_EXECUTION.equals(def.scope())));
    }

    @Test
    void aFutureCustomStrategyDerivesThroughTheSameFunction() {
        // Custom StrategyTemplate A: two indicators, plus the universal execution knobs.
        // No new code path - the model is generic, EMA Crossover is just data.
        Indicator ema = indicator("EMA");
        Indicator rsi = indicator("RSI");
        Parameter emaPeriod = parameter(10L, "ema_period", "EMA Period", "int",
                Parameter.SCOPE_SIGNAL, "20", "{\"min\":2,\"max\":300}", false);
        Parameter rsiPeriod = parameter(11L, "rsi_period", "RSI Period", "int",
                Parameter.SCOPE_SIGNAL, "14", "{\"min\":2,\"max\":100}", false);

        List<DerivedDef> defs = StrategyParameterLinkSync.desiredDefs(
                List.of(indicatorLink(ema, emaPeriod, null, null, 1),
                        indicatorLink(rsi, rsiPeriod, null, null, 1)),
                List.of(strategyLink(sl(), 101), strategyLink(tp(), 102), strategyLink(quantity(), 103)));

        assertEquals(List.of("ema_period", "rsi_period", "sl", "tp", "quantity"),
                defs.stream().map(DerivedDef::parameterKey).toList());

        Map<String, DerivedDef> byKey = byKey(defs);
        assertEquals(StrategyParamDefinition.SCOPE_SIGNAL, byKey.get("ema_period").scope());
        assertEquals(StrategyParamDefinition.SCOPE_SIGNAL, byKey.get("rsi_period").scope());
        assertEquals(StrategyParamDefinition.SCOPE_EXECUTION, byKey.get("sl").scope());
    }

    @Test
    void derivedKnobsFeedTheExistingValidatorUnchanged() {
        // The bridge: everything the catalog produces is exactly what
        // strategy_param_definitions has always carried, so validation, scope splitting
        // and the config hash are untouched by any of this.
        for (DerivedDef def : emaCrossover()) {
            assertNotNull(def.parameterKey());
            assertNotNull(def.dataType());
            assertTrue(StrategyParamDefinition.SCOPE_SIGNAL.equals(def.scope())
                            || StrategyParamDefinition.SCOPE_EXECUTION.equals(def.scope()),
                    "scope must be one the validator understands, got " + def.scope());
        }
    }
}
