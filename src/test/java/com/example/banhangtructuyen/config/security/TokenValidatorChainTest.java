package com.example.banhangtructuyen.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the issuer + audience validator chain used by
 * {@link com.example.banhangtructuyen.config.SecurityConfig#jwtDecoder()} rejects tokens
 * with the wrong issuer or wrong audience, and accepts a well-formed token.
 */
@DisplayName("Token Validator Chain (issuer + audience) Tests")
class TokenValidatorChainTest {

    private static final String ISSUER = "http://localhost:8081/realms/shoponline";
    private static final String AUDIENCE = "shoponline-backend";

    private final OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(ISSUER),
            new AudienceValidator(AUDIENCE));

    private static Jwt.Builder baseJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-123");
    }

    @Test
    @DisplayName("success — correct issuer and audience")
    void validate_shouldSucceed_whenIssuerAndAudienceCorrect() {
        final Jwt jwt = baseJwt()
                .claim(JwtClaimNames.ISS, ISSUER)
                .audience(List.of(AUDIENCE))
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("reject — wrong issuer")
    void validate_shouldReject_whenIssuerWrong() {
        final Jwt jwt = baseJwt()
                .claim(JwtClaimNames.ISS, "http://evil.example.com/realms/other")
                .audience(List.of(AUDIENCE))
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("reject — wrong audience")
    void validate_shouldReject_whenAudienceWrong() {
        final Jwt jwt = baseJwt()
                .claim(JwtClaimNames.ISS, ISSUER)
                .audience(List.of("some-other-client"))
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }
}
