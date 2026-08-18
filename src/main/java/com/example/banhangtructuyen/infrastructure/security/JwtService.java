package com.example.banhangtructuyen.infrastructure.security;

import com.example.banhangtructuyen.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Issues and validates HS256 JWT access tokens.
 * The signing secret and token lifetime come from {@code app.jwt.*}.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final AppProperties appProperties;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(
                appProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** Builds a signed token whose subject is the email and that carries the role claim. */
    public String generateToken(final String email, final String role) {
        final long now = System.currentTimeMillis();
        final long expiry = now + appProperties.getJwt().getExpirationMs();
        return Jwts.builder()
                .subject(email)
                .claims(Map.of("role", role))
                .issuedAt(new Date(now))
                .expiration(new Date(expiry))
                .signWith(signingKey())
                .compact();
    }

    public String extractEmail(final String token) {
        return parse(token).getSubject();
    }

    public String extractRole(final String token) {
        return parse(token).get("role", String.class);
    }

    /** Returns true when the signature is valid and the token has not expired. */
    public boolean isValid(final String token) {
        try {
            parse(token);
            return true;
        } catch (final Exception e) {
            return false;
        }
    }

    private Claims parse(final String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
