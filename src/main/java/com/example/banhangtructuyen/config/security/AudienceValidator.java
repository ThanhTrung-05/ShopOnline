package com.example.banhangtructuyen.config.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Validates that the JWT's {@code aud} claim contains the expected backend audience,
 * rejecting tokens minted for other clients. Spring does not validate audience by default.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final String ERROR_CODE = "invalid_token";

    private final String requiredAudience;

    public AudienceValidator(final String requiredAudience) {
        this.requiredAudience = requiredAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(final Jwt jwt) {
        final List<String> audiences = jwt.getAudience();
        if (audiences != null && audiences.contains(requiredAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        final OAuth2Error error = new OAuth2Error(
                ERROR_CODE,
                "The required audience '" + requiredAudience + "' is missing from the token",
                null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
