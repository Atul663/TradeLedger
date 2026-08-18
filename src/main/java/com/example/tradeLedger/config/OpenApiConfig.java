package com.example.tradeLedger.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the Bearer scheme so Swagger UI renders an Authorize button and
 * attaches {@code Authorization: Bearer <token>} to try-it-out calls.
 *
 * Documentation only - it changes nothing about how requests are actually
 * authenticated. {@code JwtFilter} remains the single enforcement point; without
 * this, the UI simply had no field in which to put a token.
 *
 * The requirement is declared at the root so it applies to every operation. The
 * {@code /api/v1/auth/**} endpoints do not need it and ignore the header, which
 * costs nothing and keeps the config to one place.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI tradeLedgerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TradeLedger API")
                        .version("v1")
                        .description("StrategyTemplate configuration and trading control plane."))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("""
                                        Access token from GET /api/v1/auth/me (field: accessToken).
                                        Paste the raw token only - Swagger adds the "Bearer " prefix.
                                        Expires after 30 minutes; refresh via POST /api/v1/auth/refresh.""")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
