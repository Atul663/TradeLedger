package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.indicator.IndicatorResolver;
import com.example.tradeLedger.repository.IndicatorRepository;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Structural validation for the two things authored by hand: a template's rule
 * tree and an indicator's parameter schema.
 *
 * This is where the template-to-indicator relationship is enforced. The link is
 * expressed inside {@code rule_tree} rather than by a foreign key - there is no
 * index table beside it any more - so a typo like {@code {"ind":"EMAA"}} would
 * otherwise stay invisible until a user deployed and the resolver blew up at
 * signal time. Checking it at save time turns that into a 400 on the request that
 * caused it.
 */
@Component
public class StrategyTemplateValidator {

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
     * Three things have to hold: every {@code "ind"} names a live indicator, every
     * parameter passed to one is declared by that indicator's schema, and every
     * {@code $binding} names a key some referenced indicator declares. The third
     * used to need the strategy's knob set passed in; now the schemas ARE the knob
     * set, so it is self-contained.
     *
     * @return error messages, empty when the tree is usable
     */
    public List<String> validateRuleTree(Map<String, Object> ruleTree) {
        List<String> errors = new ArrayList<>();
        if (ruleTree == null || ruleTree.isEmpty()) {
            errors.add("ruleTree is required and must be a non-empty JSON object");
            return errors;
        }

        JsonNode tree = json.toNode(ruleTree);
        Set<String> names = IndicatorResolver.indicatorNames(tree);
        if (names.isEmpty()) {
            errors.add("ruleTree references no indicators - expected at least one {\"ind\":\"...\"} node");
            return errors;
        }

        Set<String> declaredKeys = new LinkedHashSet<>();
        for (String name : names) {
            Optional<Indicator> found = indicatorRepository.findByNameIgnoreCase(name);
            if (found.isEmpty()) {
                errors.add("ruleTree references unknown indicator '" + name + "'");
                continue;
            }
            Indicator indicator = found.get();
            if (!indicator.isActive()) {
                errors.add("ruleTree references inactive indicator '" + name + "'");
                continue;
            }
            Set<String> allowed = json.toMap(indicator.getParamSchema()).keySet();
            declaredKeys.addAll(allowed);
            collectIndicatorParamKeys(tree, name, allowed, errors);
        }

        for (String binding : IndicatorResolver.bindings(tree)) {
            if (!declaredKeys.contains(binding)) {
                errors.add("ruleTree binds $" + binding + " but no indicator it references declares '"
                        + binding + "' - the declared keys are " + declaredKeys);
            }
        }
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

    // ------------------------------------------------------ indicator schema

    /**
     * Validates an indicator's {@code param_schema} - the only dynamic schema left
     * on the platform, and therefore the only place a type has to be declared
     * rather than being a column's.
     *
     * {@code default} is required: a user who never touches a knob has to land
     * somewhere, and with no parameter catalog behind it this schema is the only
     * thing that can say where.
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
                errors.add("paramSchema." + name
                        + " must be an object, e.g. {\"type\":\"int\",\"min\":2,\"default\":9}");
                continue;
            }
            Object type = spec.get("type");
            if (!(type instanceof String typeName) || !Indicator.TYPES.contains(typeName)) {
                errors.add("paramSchema." + name + ".type must be one of " + Indicator.TYPES);
            }
            Object label = spec.get(Indicator.KEY_LABEL);
            if (label != null && !(label instanceof String text && !text.isBlank())) {
                errors.add("paramSchema." + name + ".label must be a non-empty string - "
                        + "omit it entirely to be labelled by the key");
            }
            if (spec.get("default") == null) {
                errors.add("paramSchema." + name + ".default is required - it is what applies "
                        + "to every user who never sets this knob");
            }
            Object min = spec.get("min");
            Object max = spec.get("max");
            if (min != null && !(min instanceof Number)) {
                errors.add("paramSchema." + name + ".min must be numeric");
            }
            if (max != null && !(max instanceof Number)) {
                errors.add("paramSchema." + name + ".max must be numeric");
            }
            if (min instanceof Number lo && max instanceof Number hi && lo.doubleValue() > hi.doubleValue()) {
                errors.add("paramSchema." + name + ".min must not exceed .max");
            }
            Object options = spec.get("options");
            if (options != null && !(options instanceof List<?> list && !list.isEmpty())) {
                errors.add("paramSchema." + name + ".options must be a non-empty list");
            }
            if (Indicator.TYPE_ENUM.equals(type) && options == null) {
                errors.add("paramSchema." + name + " is an enum and needs a non-empty options list");
            }
            for (String crossField : List.of("gt", "lt")) {
                Object other = spec.get(crossField);
                if (other != null && !(other instanceof String otherKey && paramSchema.containsKey(otherKey))) {
                    errors.add("paramSchema." + name + "." + crossField
                            + " must name another parameter of this indicator, got " + other);
                }
            }
        }
        return errors;
    }

    /**
     * Trim only - the stored casing is the display casing ('EMA Averaging'), and it is
     * what a rule tree's "ind" value carries. Upper-casing here would write a name no
     * tree can name and no lookup can find.
     */
    public String normalizeIndicatorName(String name) {
        return name == null ? null : name.trim();
    }
}
