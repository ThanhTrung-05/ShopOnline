package com.example.banhangtructuyen.config.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps Keycloak client roles from the {@code resource_access.<clientId>.roles} claim
 * to Spring {@code ROLE_*} authorities (e.g. {@code CUSTOMER} -> {@code ROLE_CUSTOMER}).
 *
 * <p>Fail-safe by design: any missing or malformed shape in the claim (not a map, not a
 * list, non-string entries) yields an empty authority set instead of throwing.
 */
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String CLAIM_RESOURCE_ACCESS = "resource_access";
    private static final String CLAIM_ROLES = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    private final String clientId;

    public KeycloakRoleConverter(final String clientId) {
        this.clientId = clientId;
    }

    @Override
    public Collection<GrantedAuthority> convert(final Jwt jwt) {
        final Object resourceAccessClaim = jwt.getClaim(CLAIM_RESOURCE_ACCESS);
        if (!(resourceAccessClaim instanceof Map<?, ?> resourceAccess)) {
            return Set.of();
        }

        final Object clientAccessClaim = resourceAccess.get(clientId);
        if (!(clientAccessClaim instanceof Map<?, ?> clientAccess)) {
            return Set.of();
        }

        final Object rolesClaim = clientAccess.get(CLAIM_ROLES);
        if (!(rolesClaim instanceof Collection<?> roles)) {
            return Set.of();
        }

        final Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (final Object role : roles) {
            if (role instanceof String roleName && !roleName.isBlank()) {
                authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + roleName));
            }
        }
        return authorities;
    }
}
