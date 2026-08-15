package com.example.tradeLedger.indicator;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reads a strategy's rule tree - the place where the strategy-to-indicator
 * relationship actually lives.
 *
 * Three things are derivable from it, and this class is the only place that
 * knows the tree's shape:
 * <ul>
 *   <li>{@link #indicatorNames} - which indicator_defs a strategy depends on
 *       ({@code ["EMA"]}), used to validate a rule tree at save time</li>
 *   <li>{@link #bindings} - which {@code $key} placeholders it expects
 *       ({@code ["fast","slow"]}), used to check the strategy's knob set covers them</li>
 *   <li>{@link #resolve} - the concrete computations one instance needs
 *       ({@code ["EMA(period=9)","EMA(period=21)"]}), which is what dedup counts</li>
 * </ul>
 *
 * Because fingerprints come from RESOLVED parameters, dedup works across
 * strategies as well as within one: EMA(9) requested by two different strategies
 * is a single computation.
 *
 * <pre>
 *   rule_tree : {"ind":"EMA","params":{"period":"$fast"}}
 *   params    : {"fast":9,"slow":21}
 *   resolve   : ["EMA(period=9)"]
 * </pre>
 */
public final class IndicatorResolver {

    private IndicatorResolver() {
    }

    /** Distinct indicator names referenced by the tree, e.g. {@code ["EMA"]}. */
    public static Set<String> indicatorNames(JsonNode ruleTree) {
        Set<String> names = new LinkedHashSet<>();
        collectNames(ruleTree, names);
        return names;
    }

    /** Distinct {@code $key} placeholders the tree expects, without the leading '$'. */
    public static Set<String> bindings(JsonNode ruleTree) {
        Set<String> keys = new LinkedHashSet<>();
        collectBindings(ruleTree, keys);
        return keys;
    }

    /**
     * The distinct indicator computations required once this instance's signal
     * params are substituted into the tree.
     *
     * @throws IllegalStateException if the tree references a {@code $key} that the
     *                               supplied params do not bind
     */
    public static Set<String> resolve(JsonNode ruleTree, JsonNode signalParams) {
        Set<String> fingerprints = new LinkedHashSet<>();
        walk(ruleTree, signalParams, fingerprints);
        return fingerprints;
    }

    private static void collectNames(JsonNode node, Set<String> out) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectNames(child, out));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (node.hasNonNull("ind")) {
            out.add(node.get("ind").asText());
        }
        node.properties().forEach(entry -> collectNames(entry.getValue(), out));
    }

    private static void collectBindings(JsonNode node, Set<String> out) {
        if (node == null) {
            return;
        }
        if (node.isTextual() && node.asText().startsWith("$") && node.asText().length() > 1) {
            out.add(node.asText().substring(1));
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectBindings(child, out));
            return;
        }
        if (node.isObject()) {
            node.properties().forEach(entry -> collectBindings(entry.getValue(), out));
        }
    }

    private static void walk(JsonNode node, JsonNode params, Set<String> out) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> walk(child, params, out));
            return;
        }
        if (!node.isObject()) {
            return;
        }

        if (node.hasNonNull("ind")) {
            out.add(fingerprint(node, params));
            // an indicator node may still nest others (e.g. EMA of RSI)
        }
        node.properties().forEach(entry -> walk(entry.getValue(), params, out));
    }

    private static String fingerprint(JsonNode indicatorNode, JsonNode params) {
        String indicator = indicatorNode.get("ind").asText();
        Map<String, Object> resolved = new LinkedHashMap<>();

        JsonNode paramNode = indicatorNode.get("params");
        if (paramNode != null && paramNode.isObject()) {
            paramNode.properties().forEach(entry ->
                    resolved.put(entry.getKey(), substitute(entry.getValue(), params)));
        }
        return IndicatorFingerprint.of(indicator, resolved);
    }

    /** "$fast" -> the value of signalParams.fast; literals pass through unchanged. */
    private static Object substitute(JsonNode value, JsonNode params) {
        if (value != null && value.isTextual() && value.asText().startsWith("$")) {
            String key = value.asText().substring(1);
            JsonNode bound = params != null ? params.get(key) : null;
            if (bound == null || bound.isNull()) {
                throw new IllegalStateException(
                        "Rule tree references $" + key + " but no such signal param was supplied");
            }
            return bound.isNumber() ? bound.decimalValue() : bound.asText();
        }
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isNumber() ? value.decimalValue() : value.asText();
    }
}
