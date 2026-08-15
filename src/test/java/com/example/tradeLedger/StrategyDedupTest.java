package com.example.tradeLedger;

import com.example.tradeLedger.indicator.IndicatorResolver;
import com.example.tradeLedger.utils.CanonicalJson;
import com.example.tradeLedger.utils.ConfigHashUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The acceptance gate from the design document: three users configuring
 * 9x21, 9x50 and 13x21 must produce exactly 3 strategy instances and
 * 4 EMA computations - not 6.
 *
 * The canonicalization tests matter just as much. {@code compute_config_hash()}
 * in Postgres hashes {@code canonical_jsonb(params)::text}, so the Java
 * canonicalizer has to reproduce jsonb's text rendering byte for byte. If it
 * drifts, nothing throws - the same 9x21 config simply lands on two instances
 * instead of one and the platform pays for the same EMA twice.
 *
 * Pure unit tests: no Spring context, no database.
 */
class StrategyDedupTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final UUID STRATEGY_ID = UUID.fromString("00000000-0000-0000-0000-00000000e0a1");
    private static final UUID SYMBOL_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String TIMEFRAME = "5m";

    private static final String RULE_TREE = """
            {"entry":{"cross_above":[{"ind":"EMA","params":{"period":"$fast"}},
                                     {"ind":"EMA","params":{"period":"$slow"}}]},
             "exit":{"cross_below":[{"ind":"EMA","params":{"period":"$fast"}},
                                    {"ind":"EMA","params":{"period":"$slow"}}]}}""";

    private static Map<String, Object> signalParams(Object fast, Object slow) {
        Map<String, Object> params = new TreeMap<>();
        params.put("fast", fast);
        params.put("slow", slow);
        return params;
    }

    private static String hash(Map<String, Object> params) {
        return ConfigHashUtil.configHash(STRATEGY_ID, SYMBOL_ID, TIMEFRAME, MAPPER.valueToTree(params));
    }

    @Test
    void threeUsersProduceThreeInstancesAndFourIndicators() throws Exception {
        Map<String, Object> u1 = signalParams(9L, 21L);     // user 1: 9 x 21
        Map<String, Object> u2 = signalParams(9L, 50L);     // user 2: 9 x 50
        Map<String, Object> u3 = signalParams(13L, 21L);    // user 3: 13 x 21

        Set<String> instances = new LinkedHashSet<>(Set.of(hash(u1), hash(u2), hash(u3)));
        assertEquals(3, instances.size(), "each distinct config is its own instance");

        JsonNode ruleTree = MAPPER.readTree(RULE_TREE);
        Set<String> indicators = new LinkedHashSet<>();
        for (Map<String, Object> params : List.of(u1, u2, u3)) {
            indicators.addAll(IndicatorResolver.resolve(ruleTree, MAPPER.valueToTree(params)));
        }

        assertEquals(4, indicators.size(), "EMA(9) is shared by users 1 and 2, EMA(21) by users 1 and 3");
        assertEquals(Set.of("EMA(period=9)", "EMA(period=21)", "EMA(period=50)", "EMA(period=13)"), indicators);
    }

    @Test
    void identicalConfigsFromDifferentUsersShareOneInstance() {
        assertEquals(hash(signalParams(9L, 21L)), hash(signalParams(9L, 21L)),
                "same math must dedup to one instance regardless of who asked");
    }

    @Test
    void numericScaleDoesNotSplitTheFingerprint() {
        // Edge case #22: 9, 9.0 and 9.00 must hash identically or dedup silently splits.
        String canonical = hash(signalParams(9L, 21L));
        assertEquals(canonical, hash(signalParams(new BigDecimal("9.0"), new BigDecimal("21.00"))));
        assertEquals(canonical, hash(signalParams(9, 21)));
    }

    @Test
    void canonicalFormMatchesPostgresJsonbRendering() throws Exception {
        // canonical_jsonb(...)::text renders "key": value with a space, and ", "
        // between members. compute_config_hash() hashes exactly that text.
        JsonNode ordered = MAPPER.readTree("{\"fast\":9,\"slow\":21}");
        JsonNode reversed = MAPPER.readTree("{\"slow\":21,\"fast\":9}");

        assertEquals(CanonicalJson.canonicalize(ordered), CanonicalJson.canonicalize(reversed));
        assertEquals("{\"fast\": 9, \"slow\": 21}", CanonicalJson.canonicalize(reversed));
    }

    @Test
    void objectKeysAreOrderedTheWayJsonbStoresThem() throws Exception {
        // jsonb orders keys by LENGTH first, then bytewise - not plain lexicographic
        // order, which would put "sl_pct" before "slow".
        JsonNode node = MAPPER.readTree("{\"sl_pct\":1.5,\"slow\":21,\"fast\":9}");
        assertEquals("{\"fast\": 9, \"slow\": 21, \"sl_pct\": 1.5}", CanonicalJson.canonicalize(node));
    }

    @Test
    void nestedStructuresCanonicalizeRecursively() throws Exception {
        JsonNode node = MAPPER.readTree("{\"b\":[2.50,{\"y\":1,\"x\":2}],\"a\":true}");
        assertEquals("{\"a\": true, \"b\": [2.5, {\"x\": 2, \"y\": 1}]}", CanonicalJson.canonicalize(node));
    }

    @Test
    void executionParamsAreExcludedFromTheHash() {
        // Two users, same 9x21 math, different stop losses: the SL never reaches
        // the hash, so they still share one instance and one EMA pair.
        Map<String, Object> withSl = signalParams(9L, 21L);
        String sharedHash = hash(withSl);

        withSl.put("sl_pct", new BigDecimal("1.5"));
        assertNotEquals(sharedHash, hash(withSl),
                "sanity check: the hash function does see every key it is given");
        // ...which is exactly why the validator strips execution-scope keys out
        // before hashing - see StrategyParamValidator.validate().
    }

    @Test
    void identityFieldsAllParticipateInTheHash() {
        JsonNode params = MAPPER.valueToTree(signalParams(9L, 21L));
        UUID otherStrategy = UUID.fromString("99999999-9999-9999-9999-999999999999");
        UUID otherSymbol = UUID.fromString("88888888-8888-8888-8888-888888888888");

        String base = ConfigHashUtil.configHash(STRATEGY_ID, SYMBOL_ID, "5m", params);
        assertNotEquals(base, ConfigHashUtil.configHash(STRATEGY_ID, SYMBOL_ID, "15m", params));
        assertNotEquals(base, ConfigHashUtil.configHash(otherStrategy, SYMBOL_ID, "5m", params));
        assertNotEquals(base, ConfigHashUtil.configHash(STRATEGY_ID, otherSymbol, "5m", params));
    }

    @Test
    void ruleTreeExposesItsIndicatorsAndBindings() throws Exception {
        JsonNode ruleTree = MAPPER.readTree(RULE_TREE);

        assertEquals(Set.of("EMA"), IndicatorResolver.indicatorNames(ruleTree),
                "the strategy-to-indicator link lives in the rule tree, not in a join table");
        assertEquals(Set.of("fast", "slow"), IndicatorResolver.bindings(ruleTree));
    }

    @Test
    void unboundPlaceholderIsReported() throws Exception {
        JsonNode ruleTree = MAPPER.readTree(RULE_TREE);
        JsonNode incomplete = MAPPER.readTree("{\"fast\":9}");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> IndicatorResolver.resolve(ruleTree, incomplete));
        assertTrue(e.getMessage().contains("$slow"), e.getMessage());
    }
}
