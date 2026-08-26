package com.cardbilling.notification.integration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Stands in for Keycloak by decoding any token value into a valid {@link Jwt}.
 *
 * <p>Deliberately not "turn security off in tests": the real filter chain stays in place, so a
 * request without a token is still rejected and the suite proves that. What is replaced is only the
 * issuer, which would otherwise mean running Keycloak to assert something about the outbox.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestSecurityConfiguration {

    @Bean
    JwtDecoder jwtDecoder() {
        return token ->
                Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject("collections-service")
                        .issuer("https://test.local/realms/card-billing")
                        .claim("scope", "notifications")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                        .build();
    }
}
