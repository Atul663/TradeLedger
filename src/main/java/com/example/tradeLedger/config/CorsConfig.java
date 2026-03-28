package com.example.tradeLedger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {

                registry.addMapping("/**")
                        .allowedOriginPatterns(
                                "https://trade-pnl-analysis.vercel.app",
                                "https://laughing-system-6vq4gq6rrggf4964-5173.app.github.dev",
                                "http://localhost:5173",
                                "http://127.0.0.1:5173",
                                "http://localhost:*",
                                "http://127.0.0.1:*"
                        )
                        .allowedMethods("*")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
