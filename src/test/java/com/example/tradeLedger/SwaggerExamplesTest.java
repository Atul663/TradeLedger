package com.example.tradeLedger;

import com.example.tradeLedger.controller.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every example Swagger UI renders has to be valid JSON.
 *
 * These live in annotation strings, so nothing type-checks them: a missing comma
 * or an unbalanced brace compiles perfectly and then shows up as a broken
 * "Example Value" box, or worse, as a body a user pastes and cannot get to work.
 * Parsing them here is the only thing standing between a typo and that.
 *
 * The same goes for the {@code @Schema(example = ...)} strings on jsonb-backed
 * fields, which is where the indicator schemas and parameter maps are documented.
 */
class SwaggerExamplesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Every controller that documents a request body with examples. */
    private static final List<Class<?>> CONTROLLERS = List.of(
            UserStrategyController.class,
            StrategySubscriptionController.class,
            UserBrokerController.class,
            StrategyTemplateController.class,
            IndicatorController.class);

    /** DTOs whose @Schema examples are JSON objects rather than scalars. */
    private static final List<Class<?>> JSON_EXAMPLE_TYPES = List.of(
            com.example.tradeLedger.dto.UserStrategyRequest.IndicatorTuning.class,
            com.example.tradeLedger.dto.UserStrategyIndicatorResponse.class,
            com.example.tradeLedger.dto.UserStrategyRuntimeResponse.class,
            com.example.tradeLedger.dto.StrategyTemplateRequest.class,
            com.example.tradeLedger.dto.StrategyTemplateDetailResponse.class,
            com.example.tradeLedger.dto.IndicatorRequest.class,
            com.example.tradeLedger.dto.IndicatorResponse.class,
            com.example.tradeLedger.dto.IndicatorSummaryResponse.class,
            com.example.tradeLedger.dto.SharedStrategyConfigResponse.class,
            com.example.tradeLedger.dto.IndicatorPlanResponse.class,
            com.example.tradeLedger.dto.StrategySubscriptionResponse.class,
            com.example.tradeLedger.dto.ApiError.class);

    @Test
    void everyRequestBodyExampleIsValidJson() {
        List<String> failures = new ArrayList<>();
        int checked = 0;

        for (Class<?> controller : CONTROLLERS) {
            for (Method method : controller.getDeclaredMethods()) {
                RequestBody body = method.getAnnotation(RequestBody.class);
                if (body == null) {
                    continue;
                }
                for (io.swagger.v3.oas.annotations.media.Content content : body.content()) {
                    for (ExampleObject example : content.examples()) {
                        checked++;
                        String where = controller.getSimpleName() + "." + method.getName()
                                + " example '" + example.name() + "'";
                        try {
                            JsonNode parsed = MAPPER.readTree(example.value());
                            if (!parsed.isObject()) {
                                failures.add(where + " is not a JSON object");
                            }
                        } catch (Exception e) {
                            failures.add(where + " is not valid JSON: " + e.getMessage());
                        }
                    }
                }
            }
        }

        assertTrue(failures.isEmpty(), String.join("\n", failures));
        assertTrue(checked >= 15, "expected the documented examples to still be here, found " + checked);
    }

    /**
     * A {@code $key} in a rule-tree example is a binding, not a template
     * placeholder - a build step that interpolated it away would leave an example
     * that looks fine and does nothing.
     */
    @Test
    void ruleTreeExamplesKeepTheirBindings() {
        List<String> trees = new ArrayList<>();
        for (Method method : StrategyTemplateController.class.getDeclaredMethods()) {
            RequestBody body = method.getAnnotation(RequestBody.class);
            if (body == null) {
                continue;
            }
            for (io.swagger.v3.oas.annotations.media.Content content : body.content()) {
                for (ExampleObject example : content.examples()) {
                    trees.add(example.value());
                }
            }
        }
        assertFalse(trees.isEmpty(), "no rule-tree examples found");
        for (String tree : trees) {
            assertTrue(tree.contains("\"$"),
                    "a rule-tree example lost its $bindings:\n" + tree);
        }
    }

    /** The jsonb fields are documented with object examples; those have to parse too. */
    @Test
    void everySchemaExampleThatLooksLikeJsonParses() {
        List<String> failures = new ArrayList<>();

        for (Class<?> type : JSON_EXAMPLE_TYPES) {
            if (type.isRecord()) {
                for (RecordComponent component : type.getRecordComponents()) {
                    check(failures, type.getSimpleName() + "." + component.getName(),
                            component.getAnnotation(Schema.class));
                }
            } else {
                for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                    check(failures, type.getSimpleName() + "." + field.getName(),
                            field.getAnnotation(Schema.class));
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private void check(List<String> failures, String where, Schema schema) {
        if (schema == null) {
            return;
        }
        String example = schema.example();
        if (example == null) {
            return;
        }
        String trimmed = example.trim();
        // Only the ones that claim to be JSON structures - a scalar example like
        // "5m" or "65" is not meant to parse as an object.
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return;
        }
        try {
            MAPPER.readTree(trimmed);
        } catch (Exception e) {
            failures.add(where + " has an unparseable example: " + e.getMessage() + "\n  " + trimmed);
        }
    }

    /** A guard against the annotation being dropped in a refactor. */
    @Test
    void theHighTrafficEndpointsStillCarryExamples() {
        assertHasExamples(UserStrategyController.class, "create");
        assertHasExamples(UserStrategyController.class, "update");
        assertHasExamples(UserStrategyController.class, "deploy");
        assertHasExamples(StrategySubscriptionController.class, "create");
        assertHasExamples(UserBrokerController.class, "setup");
    }

    private void assertHasExamples(Class<?> controller, String methodName) {
        for (Method method : controller.getDeclaredMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            RequestBody body = method.getAnnotation(RequestBody.class);
            if (body == null) {
                continue;
            }
            for (io.swagger.v3.oas.annotations.media.Content content : body.content()) {
                if (content.examples().length > 0) {
                    return;
                }
            }
        }
        fail(controller.getSimpleName() + "." + methodName + " no longer documents any example");
    }
}
