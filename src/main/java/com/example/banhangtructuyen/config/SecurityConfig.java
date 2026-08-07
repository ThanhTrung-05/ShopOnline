package com.example.banhangtructuyen.config;

import com.example.banhangtructuyen.config.security.AudienceValidator;
import com.example.banhangtructuyen.presentation.security.RestAccessDeniedHandler;
import com.example.banhangtructuyen.presentation.security.RestAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 Resource Server configuration for ATS-21.
 *
 * <p>Validates Keycloak-issued JWT access tokens (realm {@code shoponline}) via a
 * directly-configured JWK Set URI — no OIDC discovery, so application startup and tests
 * do not depend on Keycloak being reachable (JWKS is fetched lazily on first validation).
 *
 * <p>Token validation enforces timestamp (exp/nbf), issuer, and audience
 * ({@code shoponline-backend}). Keycloak role -> Spring authority mapping is intentionally
 * out of scope; a valid token yields an authenticated principal only.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final String jwkSetUri;
    private final String issuer;
    private final String audience;

    public SecurityConfig(
            @Value("${app.security.jwt.jwk-set-uri}") final String jwkSetUri,
            @Value("${app.security.jwt.issuer}") final String issuer,
            @Value("${app.security.jwt.audience}") final String audience) {
        this.jwkSetUri = jwkSetUri;
        this.issuer = issuer;
        this.audience = audience;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        final NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        final OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new AudienceValidator(audience));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            final HttpSecurity http,
            final JwtDecoder jwtDecoder,
            final RestAuthenticationEntryPoint authenticationEntryPoint,
            final RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                .requestMatchers(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/api-docs/**",
                        "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.decoder(jwtDecoder))
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
