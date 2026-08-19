package com.example.banhangtructuyen.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AudienceValidator Unit Tests")
class AudienceValidatorTest {

    private static final String REQUIRED_AUDIENCE = "shoponline-backend";

    private final AudienceValidator validator = new AudienceValidator(REQUIRED_AUDIENCE);

    private static Jwt.Builder baseJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-123");
    }

    @Test
    @DisplayName("success — aud contains the required audience")
    void validate_shouldSucceed_whenAudienceMatches() {
        final Jwt jwt = baseJwt().audience(List.of(REQUIRED_AUDIENCE)).build();

        final OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("success — aud contains the required audience among several")
    void validate_shouldSucceed_whenAudienceAmongMany() {
        final Jwt jwt = baseJwt().audience(List.of("other-client", REQUIRED_AUDIENCE)).build();

        final OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("failure — aud is a different client")
    void validate_shouldFail_whenAudienceWrong() {
        final Jwt jwt = baseJwt().audience(List.of("some-other-client")).build();

        final OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> e.getErrorCode().equals("invalid_token"));
    }

    @Test
    @DisplayName("failure — aud claim is absent")
    void validate_shouldFail_whenAudienceMissing() {
        final Jwt jwt = baseJwt().build();

        final OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
    }
}
