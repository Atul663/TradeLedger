package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.StrategyParamDefinitionRequest;
import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.entity.StrategyParamDefinition;
import com.example.tradeLedger.indicator.IndicatorResolver;
import com.example.tradeLedger.repository.IndicatorRepository;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Structural validation for the two things a strategy author writes by hand: the
 * rule tree and the knob definitions.
 *
 * This is where the strategy-to-indicator relationship is enforced. Because the
 * link is expressed inside {@code rule_tree} rather than by a foreign key, a
 * typo like {@code {"ind":"EMAA"}} would otherwise stay invisible until a user
 * subscribed and the resolver blew up at signal time. Checking it at save time
 * turns that into a 400 on the request that caused it.
 */
@Component
public class StrategyTemplateValidator {

    private static final Set<String> DATA_TYPES = Set.of(
            StrategyParamDefinition.TYPE_INT, StrategyParamDefinition.TYPE_DECIMAL, StrategyParamDefinition.TYPE_BOOL,
            StrategyParamDefinition.TYPE_ENUM, StrategyParamDefinition.TYPE_TIMEFRAME, StrategyParamDefinition.TYPE_TEXT);

    private static final Set<String> SCOPES = Set.of(
            StrategyParamDefinition.SCOPE_SIGNAL, StrategyParamDefinition.SCOPE_EXECUTION);

    private final IndicatorRepository indicatorRepository;
    private final JsonSupport json;

    public StrategyTemplateValidator(IndicatorRepository indicatorRepository, JsonSupport json) {
        this.indicatorRepository = indicatorRepository;
        this.json = json;
    }

    // ------------------------------------------------------------- rule tree

    /**
     * Validates a rule tree against the indicator catalog.
     *
     * @param knownParamKeys the strategy's knob keys, or null to skip binding
     *                       checks (a strategy may be created before its knobs)
     * @return error messages, empty when the tree is usable
     */
    public List<String> validateRuleTree(Map<String, Object> ruleTree, Collection<String> knownParamKeys) {
        List<String> errors = new ArrayList<>();
        if (ruleTree == null || ruleTree.isEmpty()) {
            errors.add("ruleTree is required and must be a non-empty JSON object");
            return errors;
        }

        JsonNode tree = json.toNode(ruleTree);
        Set<String> names = IndicatorResolver.indicatorNames(tree);
        if (names.isEmpty()) {
            errors.add("ruleTree references no indicators - expected at least one {\"ind\":\"...\"} node");
        }

        for (String name : names) {
            Optional<Indicator> def = indicatorRepository.findByName(name);
            if (def.isEmpty()) {
                errors.add("ruleTree references unknown indicator '" + name + "'");
            } else if (!def.get().isActive()) {
                errors.add("ruleTree references inactive indicator '" + name + "'");
            } else {
                errors.addAll(checkIndicatorParams(tree, def.get()));
            }
        }

        if (knownParamKeys != null) {
            Set<String> known = new HashSet<>(knownParamKeys);
            for (String binding : IndicatorResolver.bindings(tree)) {
                if (!known.contains(binding)) {
                    errors.add("ruleTree binds $" + binding + " but the strategy defines no parameter '"
                            + binding + "'");
                }
            }
        }
        return errors;
    }

    /** Every param an indicator node passes must exist in that indicator's schema. */
    private List<String> checkIndicatorParams(JsonNode tree, Indicator def) {
        List<String> errors = new ArrayList<>();
        Set<String> allowed = json.toMap(def.getParamSchema()).keySet();
        collectIndicatorParamKeys(tree, def.getName(), allowed, errors);
        return errors;
    }

    private void collectIndicatorParamKeys(JsonNode node, String indicatorName,
                                           Set<String> allowed, List<String> errors) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectIndicatorParamKeys(child, indicatorName, allowed, errors));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (node.hasNonNull("ind") && indicatorName.equals(node.get("ind").asText())) {
            JsonNode params = node.get("params");
            if (params != null && params.isObject()) {
                params.properties().forEach(entry -> {
                    if (!allowed.contains(entry.getKey())) {
                        errors.add("Indicator '" + indicatorName + "' has no parameter '" + entry.getKey()
                                + "' - its schema declares " + allowed);
                    }
                });
            }
        }
        node.properties().forEach(entry ->
                collectIndicatorParamKeys(entry.getValue(), indicatorName, allowed, errors));
    }

    // ------------------------------------------------------------ knob defs

    /**
     * Validates one knob definition.
     *
     * @param siblingKeys every parameter key the strategy will have, so cross-field
     *                    rules ({@code {"gt":"fast"}}) can be checked for dangling
     *                    references
     */
    public List<String> validateParamDef(StrategyParamDefinitionRequest request, Collection<String> siblingKeys) {
        List<String> errors = new ArrayList<>();
        if (request == null) {
            errors.add("Parameter definition is required");
            return errors;
        }
        String key = request.getParameterKey();
        if (isBlank(key)) {
            errors.add("parameterKey is required");
        } else if (key.length() > 100) {
            errors.add("parameterKey must be at most 100 characters");
        }

        if (isBlank(request.getDataType()) || !DATA_TYPES.contains(request.getDataType())) {
            errors.add("dataType must be one of " + DATA_TYPES + " for parameter '" + key + "'");
        }
        if (isBlank(request.getScope()) || !SCOPES.contains(request.getScope())) {
            errors.add("scope must be one of " + SCOPES + " for parameter '" + key + "'");
        }
        if (request.getDisplayLabel() != null && request.getDisplayLabel().length() > 100) {
            errors.add("displayLabel must be at most 100 characters for parameter '" + key + "'");
        }

        Map<String, Object> validation = request.getValidation();
        if (validation != null) {
            for (String crossField : List.of("gt", "lt")) {
                Object other = validation.get(crossField);
                if (other instanceof String otherKey && siblingKeys != null && !siblingKeys.contains(otherKey)) {
                    errors.add("Parameter '" + key + "' has a '" + crossField + "' rule referencing unknown "
                            + "parameter '" + otherKey + "'");
                }
            }
            if (StrategyParamDefinition.TYPE_ENUM.equals(request.getDataType())
                    && !(validation.get("options") instanceof List<?> options && !options.isEmpty())) {
                errors.add("Parameter '" + key + "' is an enum and needs a non-empty validation.options list");
            }
        } else if (StrategyParamDefinition.TYPE_ENUM.equals(request.getDataType())) {
            errors.add("Parameter '" + key + "' is an enum and needs a non-empty validation.options list");
        }
        return errors;
    }

    // ------------------------------------------------------ indicator schema

    /**
     * Validates an indicator's {@code param_schema} - the JSON structure that
     * stands in for a per-indicator parameter table in this design.
     */
    public List<String> validateParamSchema(Map<String, Object> paramSchema) {
        List<String> errors = new ArrayList<>();
        if (paramSchema == null || paramSchema.isEmpty()) {
            errors.add("paramSchema is required and must declare at least one parameter");
            return errors;
        }
        for (Map.Entry<String, Object> entry : paramSchema.entrySet()) {
            String name = entry.getKey();
            if (!(entry.getValue() instanceof Map<?, ?> spec)) {
                errors.add("paramSchema." + name + " must be an object, e.g. {\"type\":\"int\",\"min\":2}");
                continue;
            }
            Object type = spec.get("type");
            if (!(type instanceof String typeName) || !DATA_TYPES.contains(typeName)) {
                errors.add("paramSchema." + name + ".type must be one of " + DATA_TYPES);
            }
            Object min = spec.get("min");
            Object max = spec.get("max");
            if (min != null && !(min instanceof Number)) {
                errors.add("paramSchema." + name + ".min must be numeric");
            }
            if (max != null && !(max instanceof Number)) {
                errors.add("paramSchema." + name + ".max must be numeric");
            }
            if (min instanceof Number lo && max instanceof Number hi
                    && lo.doubleValue() > hi.doubleValue()) {
                errors.add("paramSchema." + name + ".min must not exceed .max");
            }
            Object options = spec.get("options");
            if (options != null && !(options instanceof List<?> list && !list.isEmpty())) {
                errors.add("paramSchema." + name + ".options must be a non-empty list");
            }
        }
        return errors;
    }

    /** Indicator names are matched by exact string against rule trees, so they are normalized. */
    public String normalizeIndicatorName(String name) {
        return name == null ? null : name.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
