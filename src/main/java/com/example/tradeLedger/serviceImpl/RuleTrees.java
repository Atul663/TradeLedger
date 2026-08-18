package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.entity.StrategyTemplate;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.indicator.IndicatorResolver;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The two things every caller wants from a template's rule tree: does this set of
 * signal params satisfy it, and which indicator computations does it come to.
 *
 * Both subscriptions and saved strategies ask, and the answer has to be the same
 * one - a saved strategy that resolves differently from the subscription it
 * becomes would accept a configuration that then fails at subscribe time.
 */
@Component
public class RuleTrees {

    private static final Logger log = LoggerFactory.getLogger(RuleTrees.class);

    private final JsonSupport json;

    public RuleTrees(JsonSupport json) {
        this.json = json;
    }

    /**
     * Knobs can be individually valid and still not satisfy the rule tree, if the
     * tree binds a {@code $key} that has no signal-scope parameter behind it.
     * Catching that here turns a runtime failure in the engine into a 400 on the
     * request that would have caused it.
     */
    public void assertResolves(StrategyTemplate strategy, Map<String, Object> signalParams) {
        JsonNode ruleTree = json.readTree(strategy.getRuleTree());
        if (ruleTree == null) {
            throw new StrategyValidationException(
                    "Strategy template '" + strategy.getName() + "' has an unreadable rule tree");
        }
        try {
            IndicatorResolver.resolve(ruleTree, json.toNode(signalParams));
        } catch (RuntimeException e) {
            throw new StrategyValidationException(e.getMessage());
        }
    }

    /** {@code ["EMA(period=9)", "EMA(period=21)"]}, or empty when it cannot be resolved. */
    public List<String> indicators(StrategyTemplate strategy, JsonNode signalParams) {
        JsonNode ruleTree = json.readTree(strategy.getRuleTree());
        if (ruleTree == null) {
            return List.of();
        }
        try {
            return new ArrayList<>(IndicatorResolver.resolve(ruleTree, signalParams));
        } catch (RuntimeException e) {
            log.warn("Could not resolve indicators for strategy template {}: {}",
                    strategy.getId(), e.getMessage());
            return List.of();
        }
    }

    public List<String> indicators(StrategyTemplate strategy, Map<String, Object> signalParams) {
        return indicators(strategy, json.toNode(signalParams));
    }
}
