package com.cardbilling.notification.infrastructure.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 resource server. Every business endpoint needs a valid Bearer token from the
 * {@code card-billing} Keycloak realm - including calls from {@code collections-service}, which
 * obtains one via client credentials rather than being trusted for sitting inside the network.
 *
 * <p>Health and the OpenAPI documents are deliberately open: a readiness probe that needs a token
 * is a liability, and an API description is not a secret.
 *
 * <p>This is the default and is what runs everywhere except the explicitly opted-into {@code local}
 * profile - see {@link LocalSecurityConfig} for what that profile is for and what it gives up.
 */
@Configuration
@Profile("!local")
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                HttpMethod.GET,
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/actuator/info",
                                                "/v3/api-docs",
                                                "/v3/api-docs/**",
                                                "/swagger-ui.html",
                                                "/swagger-ui/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
