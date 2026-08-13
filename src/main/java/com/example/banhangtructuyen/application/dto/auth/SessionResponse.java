package com.example.banhangtructuyen.application.dto.auth;

/**
 * Minimal view of the current authenticated session, derived solely from the JWT.
 * Intentionally NOT a customer profile: no Oracle lookup, no Customer entity.
 */
public record SessionResponse(
        boolean authenticated,
        String subject,
        String username
) {
}
