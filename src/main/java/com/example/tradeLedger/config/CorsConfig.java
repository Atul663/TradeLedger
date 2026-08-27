package com.example.tradeLedger.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class CorsConfig {

    private final FrontendOrigins frontendOrigins;

    public CorsConfig(FrontendOrigins frontendOrigins) {
        this.frontendOrigins = frontendOrigins;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {

                // The origins live in app.cors.allowed-origins so the Vercel URL
                // is set per environment. allowCredentials is on because the
                // refresh cookie is cross-site, which is also why patterns are
                // used instead of "*" - the two are mutually exclusive.
                registry.addMapping("/**")
                        .allowedOriginPatterns(frontendOrigins.patterns())
                        .allowedMethods("*")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
