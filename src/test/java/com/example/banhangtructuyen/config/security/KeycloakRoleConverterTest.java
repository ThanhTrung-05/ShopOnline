package com.example.banhangtructuyen.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KeycloakRoleConverter Unit Tests")
class KeycloakRoleConverterTest {

    private static final String CLIENT_ID = "shoponline-backend";

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter(CLIENT_ID);

    private static Jwt.Builder baseJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
    }

    private static Map<String, Object> resourceAccessWithRoles(final Object roles) {
        return Map.of(CLIENT_ID, Map.of("roles", roles));
    }

    @Test
    @DisplayName("CUSTOMER -> ROLE_CUSTOMER")
    void convert_shouldMapCustomerRole() {
        final Jwt jwt = baseJwt()
                .claim("resource_access", resourceAccessWithRoles(List.of("CUSTOMER")))
                .build();

        final Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOMER");
    }

    @Test
    @DisplayName("WAREHOUSE_STAFF -> ROLE_WAREHOUSE_STAFF")
    void convert_shouldMapWarehouseStaffRole() {
        final Jwt jwt = baseJwt()
                .claim("resource_access", resourceAccessWithRoles(List.of("WAREHOUSE_STAFF")))
                .build();

        final Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_WAREHOUSE_STAFF");
    }

    @Test
    @DisplayName("ADMIN -> ROLE_ADMIN")
    void convert_shouldMapAdminRole() {
        final Jwt jwt = baseJwt()
                .claim("resource_access", resourceAccessWithRoles(List.of("ADMIN")))
                .build();

        final Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("multiple roles -> all mapped")
    void convert_shouldMapMultipleRoles() {
        final Jwt jwt = baseJwt()
                .claim("resource_access", resourceAccessWithRoles(List.of("CUSTOMER", "WAREHOUSE_STAFF", "ADMIN")))
                .build();

        final Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_WAREHOUSE_STAFF", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("missing resource_access claim -> empty authorities")
    void convert_shouldReturnEmpty_whenResourceAccessMissing() {
        final Jwt jwt = baseJwt().build();

        final Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("missing backend client entry -> empty authorities")
    void convert_shouldReturnEmpty_whenBackendClientMissing() {
        final Jwt jwt = baseJwt()
                .claim("resource_access", Map.of("some-other-client", Map.of("roles", List.of("CUSTOMER"))))
                .build();

        final Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("missing roles key under backend client -> empty authorities")
    void convert_shouldReturnEmpty_whenRolesKeyMissing() {
        final Jwt jwt = baseJwt()
                .claim("resource_access", Map.of(CLIENT_ID, Map.of()))
                .build();

        final Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("malformed claim (resource_access is a String, not a Map) -> fail-safe empty, no exception")
    void convert_shouldReturnEmpty_whenResourceAccessMalformed() {
        final Jwt jwt = baseJwt()
                .claim("resource_access", "not-a-map")
                .build();

        final Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("malformed claim (client entry is a String, not a Map) -> fail-safe empty, no exception")
    void convert_shouldReturnEmpty_whenClientEntryMalformed() {
        final Jwt jwt = baseJwt()
                .claim("resource_access", Map.of(CLIENT_ID, "not-a-map"))
                .build();

        final Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("malformed claim (roles is a String, not a Collection) -> fail-safe empty, no exception")
    void convert_shouldReturnEmpty_whenRolesMalformed() {
        final Jwt jwt = baseJwt()
                .claim("resource_access", resourceAccessWithRoles("CUSTOMER"))
                .build();

        final Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("duplicate roles in claim -> authority not duplicated")
    void convert_shouldNotDuplicateAuthorities() {
        final Jwt jwt = baseJwt()
                .claim("resource_access", resourceAccessWithRoles(List.of("CUSTOMER", "CUSTOMER")))
                .build();

        final Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOMER");
    }
}
