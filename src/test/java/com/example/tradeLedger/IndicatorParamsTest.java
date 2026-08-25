package com.example.tradeLedger;

import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.serviceImpl.IndicatorParams;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The indicator schema is the only dynamic declaration left on the platform, so
 * it is the only place a column constraint is not doing the checking. These are
 * the checks standing in for one.
 */
class IndicatorParamsTest {

    private final IndicatorParams params = new IndicatorParams(new JsonSupport(new ObjectMapper()));

    /** k is the EMA of the highs, d the shorter signal leg - so d stays under k. */
    private static Indicator emaAveraging() {
        Indicator indicator = new Indicator();
        indicator.setName("EMA Averaging");
        indicator.setParamSchema("""
                {"k":{"type":"int","min":1,"max":300,"default":21},\
                "d":{"type":"int","min":1,"max":300,"default":9,"lt":"k"}}""");
        return indicator;
    }

    @Test
    void fillsEveryKnobFromTheSchemaWhenNothingIsSubmitted() {
        Map<String, Object> effective = params.defaults(emaAveraging());

        assertEquals(21L, effective.get("k"));
        assertEquals(9L, effective.get("d"));
    }

    @Test
    void keepsUnsubmittedKnobsOnTheirDefault() {
        Map<String, Object> effective = params.effective(emaAveraging(), Map.of("k", 50));

        assertEquals(50L, effective.get("k"));
        assertEquals(9L, effective.get("d"), "d was not submitted, so it stays on its default");
    }

    /** The map feeds the config hash directly, so its order has to be stable. */
    @Test
    void returnsKnobsSorted() {
        Map<String, Object> submitted = new LinkedHashMap<>();
        submitted.put("k", 50);
        submitted.put("d", 21);

        assertEquals("[d, k]", params.effective(emaAveraging(), submitted).keySet().toString());
    }

    /** 9, 9.0 and "9" have to land on one value or two users pay for one EMA twice. */
    @Test
    void coercesToTheDeclaredTypeSoTheHashCannotSplit() {
        assertEquals(params.effective(emaAveraging(), Map.of("k", 50)).get("k"),
                params.effective(emaAveraging(), Map.of("k", "50")).get("k"));
        assertEquals(params.effective(emaAveraging(), Map.of("k", 50)).get("k"),
                params.effective(emaAveraging(), Map.of("k", 50.0)).get("k"));
    }

    @Test
    void rejectsAKnobTheIndicatorDoesNotDeclare() {
        StrategyValidationException thrown = assertThrows(StrategyValidationException.class,
                () -> params.effective(emaAveraging(), Map.of("period", 14)));

        assertTrue(thrown.getMessage().contains("period"), thrown.getMessage());
    }

    @Test
    void rejectsAValueOutsideItsDeclaredRange() {
        StrategyValidationException thrown = assertThrows(StrategyValidationException.class,
                () -> params.effective(emaAveraging(), Map.of("k", 400)));

        assertTrue(thrown.getMessage().contains("300"), thrown.getMessage());
    }

    @Test
    void rejectsANonIntegerForAnIntKnob() {
        assertThrows(StrategyValidationException.class,
                () -> params.effective(emaAveraging(), Map.of("k", "21.5")));
    }

    /** The cross-field rule the spreadsheet implies: 21/9 and 50/21 are fine, 9/21 is not. */
    @Test
    void enforcesTheCrossFieldRuleBetweenKnobs() {
        params.effective(emaAveraging(), Map.of("k", 50, "d", 21));

        StrategyValidationException thrown = assertThrows(StrategyValidationException.class,
                () -> params.effective(emaAveraging(), Map.of("k", 9, "d", 21)));

        assertTrue(thrown.getMessage().contains("less than"), thrown.getMessage());
    }

    /** A form with two mistakes should report two, not send the user round twice. */
    @Test
    void reportsEveryProblemAtOnce() {
        StrategyValidationException thrown = assertThrows(StrategyValidationException.class,
                () -> params.effective(emaAveraging(), Map.of("k", 400, "period", 14)));

        assertEquals(2, thrown.getErrors().size(), thrown.getErrors().toString());
    }
}
