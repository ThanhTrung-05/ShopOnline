package com.example.banhangtructuyen.config;

import com.example.banhangtructuyen.config.security.AudienceValidator;
import com.example.banhangtructuyen.config.security.KeycloakRoleConverter;
import com.example.banhangtructuyen.infrastructure.security.JwtAuthenticationFilter;
import com.example.banhangtructuyen.presentation.security.RestAccessDeniedHandler;
import com.example.banhangtructuyen.presentation.security.RestAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final String jwkSetUri;
    private final String issuer;
    private final String audience;
    private final List<String> corsAllowedOrigins;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            @Value("${app.security.jwt.jwk-set-uri:}") final String jwkSetUri,
            @Value("${app.security.jwt.issuer:}") final String issuer,
            @Value("${app.security.jwt.audience:}") final String audience,
            @Value("${app.security.cors.allowed-origins:*}") final List<String> corsAllowedOrigins,
            final JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwkSetUri = jwkSetUri;
        this.issuer = issuer;
        this.audience = audience;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public AuthenticationManager authenticationManager(final AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        if (jwkSetUri == null || jwkSetUri.isEmpty()) {
            return token -> null; // Dummy for tests without OAuth2 config
        }
        final NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        final OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new AudienceValidator(audience));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        final JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter(audience));
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsAllowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            final HttpSecurity http,
            final JwtDecoder jwtDecoder,
            final JwtAuthenticationConverter jwtAuthenticationConverter,
            final CorsConfigurationSource corsConfigurationSource,
            final RestAuthenticationEntryPoint authenticationEntryPoint,
            final RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/api-docs/**",
                        "/v3/api-docs/**",
                        "/actuator/health").permitAll()
                .requestMatchers("/api/v1/cart/**").hasRole("CUSTOMER")
                .requestMatchers("/api/v1/customers/me", "/api/v1/customers/me/**").hasRole("CUSTOMER")
                .anyRequest().authenticated())
            // Add custom local JWT filter from HEAD
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // Add OAuth2 Resource Server from the other branch
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter))
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
