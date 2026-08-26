package com.cardbilling.notification.infrastructure.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Anonymous access, for the {@code local} profile only.
 *
 * <p>Running this service end to end needs Postgres, Redpanda and Keycloak at once, and Keycloak is
 * by far the heaviest of the three - on a constrained machine it can spend ten minutes in Quarkus
 * augmentation and then block its own event loop long enough for the token endpoint to stop
 * answering. That makes the outbox demonstration, which is what this service exists to show, depend
 * on the one container that has nothing to do with it.
 *
 * <p>So {@code --spring.profiles.active=local} drops the token requirement and lets the outbox loop
 * be exercised with nothing but {@code curl}. It is opt-in, it is never the default, and
 * {@link SecurityConfig} - the real resource server - is what runs in every other case, including
 * the integration tests, which still assert that an unauthenticated request is rejected.
 */
@Configuration
@Profile("local")
class LocalSecurityConfig {

    @Bean
    SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
