package com.example.tradeLedger.config;

import com.example.tradeLedger.controller.SecuredController;
import com.example.tradeLedger.dto.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI wiring: the Bearer scheme, and the error responses every
 * strategy-module endpoint can return.
 *
 * Documentation only - it changes nothing about how requests are authenticated or
 * how errors are produced. {@code JwtFilter} remains the single enforcement point
 * and {@code StrategyApiExceptionHandler} the single error mapper; this just
 * makes both visible in the UI.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final String ERROR_REF = "#/components/schemas/ApiError";

    @Bean
    public OpenAPI tradeLedgerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TradeLedger API")
                        .version("v1")
                        .description("""
                                Strategy configuration and trading control plane.

                                **The shape of the module.** A *template* is platform-owned logic \
                                (a rule tree over indicators). A *strategy* is one user's complete \
                                configuration of it - market, instrument, CE and PE strikes, \
                                averaging ladder, exits - held in typed columns on one row. A \
                                *deployment* points at a strategy and adds only what differs per \
                                broker account: size, risk profile, paper or live.

                                **So editing a strategy moves every broker it runs on**, \
                                immediately. Deployments do not carry a copy.

                                Start at `POST /api/v1/my-brokers/setup`, then \
                                `POST /api/v1/my-strategies`, then \
                                `POST /api/v1/my-strategies/{id}/deploy`."""))
                .components(new Components()
                        // Registered explicitly: no operation declares ApiError as a return
                        // type, so nothing would otherwise put it in components for the
                        // error responses below to $ref.
                        .schemas(ModelConverters.getInstance().read(ApiError.class))
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("""
                                        Access token from GET /api/v1/auth/me (field: accessToken).
                                        Paste the raw token only - Swagger adds the "Bearer " prefix.
                                        Expires after 1 day; refresh via POST /api/v1/auth/refresh.""")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /**
     * Attaches the four error responses to every strategy-module operation.
     *
     * Scoped by {@link SecuredController} rather than by a hand-kept list, which is
     * exactly the set {@code StrategyApiExceptionHandler} advises - so the two
     * cannot drift as controllers are added. The auth and legacy toggle
     * controllers build their own responses and are left alone.
     *
     * Existing entries are never overwritten: an operation that documents its own
     * 409 keeps it.
     */
    @Bean
    public OperationCustomizer strategyModuleErrorResponses() {
        return (Operation operation, org.springframework.web.method.HandlerMethod handlerMethod) -> {
            if (!SecuredController.class.isAssignableFrom(handlerMethod.getBeanType())) {
                return operation;
            }
            ApiResponses responses = operation.getResponses() != null
                    ? operation.getResponses()
                    : new ApiResponses();

            addIfAbsent(responses, "400", """
                            Validation failed. `errors[]` holds EVERY problem found, not just the \
                            first - render the whole list. Field names in the messages match the \
                            request field names.""",
                    example("Strike out of range", """
                            { "error": "ceStrikeOffset must be 1..15 for OTM, got 16",
                              "errors": [ "ceStrikeOffset must be 1..15 for OTM, got 16" ] }"""),
                    example("Several problems at once", """
                            { "error": "derivative is OPTION but neither side is on - enable ceEnabled, peEnabled, or both",
                              "errors": [
                                "derivative is OPTION but neither side is on - enable ceEnabled, peEnabled, or both",
                                "averagingCount must be 0..10, got 25" ] }"""),
                    example("Indicator value rejected", """
                            { "error": "Indicator 'EMA AVERAGING' parameter 'd' must be less than 'k' (21 vs 9)",
                              "errors": [ "Indicator 'EMA AVERAGING' parameter 'd' must be less than 'k' (21 vs 9)" ] }"""));

            addIfAbsent(responses, "401",
                    "Missing, invalid or expired Bearer token.",
                    example("Unauthorized", """
                            { "error": "Unauthorized: valid Bearer token required" }"""));

            addIfAbsent(responses, "404", """
                            Not found - **or owned by another user**. The ownership filter is part \
                            of the query, so the API never confirms that someone else's row \
                            exists. There is no 403.""",
                    example("Not found", """
                            { "error": "Strategy not found: us000000-1111-4222-8333-444444444444" }"""));

            addIfAbsent(responses, "409", """
                            Conflict: a duplicate, a locked system row, or an operation blocked by \
                            something that references it. The message usually says what to do \
                            instead.""",
                    example("Already deployed", """
                            { "error": "Strategy NIFTY 21/9 both sides is already deployed on account main (subscriptionId=sub00000-1111-4222-8333-444444444444). Change it with PUT /api/v1/my-subscriptions/sub00000-1111-4222-8333-444444444444." }"""),
                    example("Delete blocked", """
                            { "error": "Strategy NIFTY 21/9 both sides is deployed on 3 account(s). Withdraw those deployments first, or archive it with PUT /api/v1/my-strategies/us000000-1111-4222-8333-444444444444 {\\"active\\":false}." }"""));

            operation.setResponses(responses);
            return operation;
        };
    }

    private void addIfAbsent(ApiResponses responses, String status, String description,
                             Example... examples) {
        if (responses.containsKey(status)) {
            return;
        }
        MediaType mediaType = new MediaType().schema(new Schema<>().$ref(ERROR_REF));
        for (Example each : examples) {
            mediaType.addExamples(each.getSummary(), each);
        }
        responses.addApiResponse(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", mediaType)));
    }

    private Example example(String summary, String value) {
        return new Example().summary(summary).value(value);
    }
}
