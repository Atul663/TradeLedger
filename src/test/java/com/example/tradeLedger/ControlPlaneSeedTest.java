package com.example.tradeLedger;

import com.example.tradeLedger.config.ControlPlaneSeeder;
import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.indicator.IndicatorResolver;
import com.example.tradeLedger.repository.IndicatorRepository;
import com.example.tradeLedger.serviceImpl.IndicatorParams;
import com.example.tradeLedger.serviceImpl.StrategyTemplateValidator;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The seeded catalogue, checked the way the API checks anything an operator
 * POSTs.
 *
 * ControlPlaneSeeder writes straight through the repositories, so nothing on the
 * write path ever sees this data - a schema missing a default, a tree binding a
 * key no indicator declares, or defaults that violate their own gt/lt rule would
 * all reach the database and only surface as a 400 on the first strategy built
 * from it. Running the same validators over the table here is what makes adding
 * a row to it safe.
 */
class ControlPlaneSeedTest {

    private final JsonSupport json = new JsonSupport(new ObjectMapper());
    private final IndicatorParams params = new IndicatorParams(json);

    /** A validator backed by the seeded catalogue itself, since that IS the catalogue at boot. */
    private StrategyTemplateValidator validatorOverSeededCatalogue() {
        Map<String, Indicator> catalogue = new HashMap<>();
        ControlPlaneSeeder.STRATEGIES.forEach(s -> catalogue.put(s.name(), indicator(s.name(), s.paramSchema())));
        ControlPlaneSeeder.PRIMITIVES.forEach((name, schema) -> catalogue.put(name, indicator(name, schema)));

        IndicatorRepository repository = mock(IndicatorRepository.class);
        when(repository.findByNameIgnoreCase(anyString())).thenAnswer(call ->
                catalogue.entrySet().stream()
                        .filter(e -> e.getKey().equalsIgnoreCase(call.getArgument(0)))
                        .map(Map.Entry::getValue)
                        .findFirst());
        when(repository.findByName(anyString())).thenAnswer(call ->
                Optional.ofNullable(catalogue.get((String) call.getArgument(0))));
        return new StrategyTemplateValidator(repository, json);
    }

    private static Indicator indicator(String name, String paramSchema) {
        Indicator indicator = new Indicator();
        indicator.setName(name);
        indicator.setParamSchema(paramSchema);
        indicator.setActive(true);
        return indicator;
    }

    @Test
    void everySeededParamSchemaPassesTheSameValidationAPostWouldGet() {
        StrategyTemplateValidator validator = validatorOverSeededCatalogue();

        ControlPlaneSeeder.STRATEGIES.forEach(s ->
                assertEquals(List.of(), validator.validateParamSchema(json.toMap(s.paramSchema())),
                        "paramSchema of " + s.name()));
        ControlPlaneSeeder.PRIMITIVES.forEach((name, schema) ->
                assertEquals(List.of(), validator.validateParamSchema(json.toMap(schema)),
                        "paramSchema of " + name));
    }

    /**
     * The trap a table of JSON invites: bounds and cross-field rules that the
     * DEFAULTS themselves break. Nobody would ever be able to save the strategy,
     * because seeding its indicator rows starts from exactly these values.
     */
    @Test
    void everySeededSchemaResolvesItsOwnDefaults() {
        ControlPlaneSeeder.STRATEGIES.forEach(s -> {
            Map<String, Object> defaults = params.defaults(indicator(s.name(), s.paramSchema()));
            assertEquals(json.toMap(s.paramSchema()).keySet(), defaults.keySet(),
                    "defaults of " + s.name() + " must cover every declared key");
        });
        ControlPlaneSeeder.PRIMITIVES.forEach((name, schema) ->
                assertNotNull(params.defaults(indicator(name, schema)), name));
    }

    @Test
    void everySeededRuleTreePassesTheSameValidationAPostWouldGet() {
        StrategyTemplateValidator validator = validatorOverSeededCatalogue();

        ControlPlaneSeeder.STRATEGIES.forEach(s ->
                assertEquals(List.of(), validator.validateRuleTree(json.toMap(s.ruleTree())),
                        "ruleTree of " + s.name()));
    }

    /** A tree naming an indicator the seeder never writes is a template nobody can use. */
    @Test
    void everySeededRuleTreeNamesItsOwnIndicator() {
        ControlPlaneSeeder.STRATEGIES.forEach(s -> {
            Set<String> named = IndicatorResolver.indicatorNames(json.toNode(json.toMap(s.ruleTree())));
            assertEquals(Set.of(s.name()), named,
                    s.name() + " should name itself and nothing else");
        });
    }

    /**
     * The tree binds every knob, so a form that only sends what the tree asks for
     * can still reach all of them.
     */
    @Test
    void everySeededRuleTreeBindsEveryKnobItsIndicatorDeclares() {
        ControlPlaneSeeder.STRATEGIES.forEach(s -> assertEquals(
                json.toMap(s.paramSchema()).keySet(),
                IndicatorResolver.bindings(json.toNode(json.toMap(s.ruleTree()))),
                "bindings of " + s.name()));
    }

    @Test
    void everySeededParameterCarriesALabel() {
        ControlPlaneSeeder.STRATEGIES.forEach(s ->
                IndicatorParams.labelled(json.toMap(s.paramSchema())).forEach((key, spec) -> {
                    Object label = ((Map<?, ?>) spec).get(Indicator.KEY_LABEL);
                    assertTrue(label instanceof String text && !text.isBlank(),
                            s.name() + "." + key + " has no label");
                }));
    }

    /** Names are the identity of both an indicator and its template, so no two may collide. */
    @Test
    void theCatalogueHasNoRepeatedName() {
        List<String> names = ControlPlaneSeeder.STRATEGIES.stream()
                .map(ControlPlaneSeeder.Strategy::name).toList();
        assertEquals(names.size(), Set.copyOf(names).size(), names.toString());
        names.forEach(name -> assertTrue(!ControlPlaneSeeder.PRIMITIVES.containsKey(name),
                name + " is both a strategy and a primitive"));
    }
}
