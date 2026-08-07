package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.ApiResponse;
import com.example.banhangtructuyen.application.dto.auth.SessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session verification endpoint for ATS-21.
 *
 * <p>Reads only the current JWT to report session state. It is deliberately NOT a customer
 * profile: no Oracle lookup, no {@code Customer} entity. Separate from {@code AuthController}
 * (ATS-20) so registration remains untouched.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Session", description = "Authenticated session verification — reads the current JWT")
public class SessionController {

    @Operation(
        summary = "Get current session",
        description = "Returns minimal session info derived from the validated JWT access token: "
                    + "authenticated flag, subject (sub), and username (preferred_username). "
                    + "Requires a valid Bearer token; returns 401 otherwise."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session info returned",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing, invalid, or expired token",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/session")
    public ResponseEntity<ApiResponse<SessionResponse>> session(@AuthenticationPrincipal final Jwt jwt) {
        final SessionResponse response = new SessionResponse(
                true,
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
