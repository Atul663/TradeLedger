package com.example.tradeLedger.config;

import com.example.tradeLedger.security.JwtFilter;
import com.example.tradeLedger.security.SecurityErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.Customizer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final SecurityErrorHandler securityErrorHandler;

    public SecurityConfig(JwtFilter jwtFilter, SecurityErrorHandler securityErrorHandler) {
        this.jwtFilter = jwtFilter;
        this.securityErrorHandler = securityErrorHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                // The platform health check is unauthenticated;
                                // show-details=never keeps it to UP or DOWN.
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        // /error is permitted on purpose. It carries no data of its own -
                        // it renders the status the request already earned. Boot runs this
                        // chain on every dispatcher type and AuthorizationFilter authorizes
                        // ERROR dispatches, so leaving /error out turns every 404 and 500
                        // raised inside a permitted endpoint into a 401 and hides the real
                        // failure behind an authentication one.
                        .requestMatchers("/api/v1/**", "/error").permitAll()
                        .anyRequest().authenticated()
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler)
                )

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
