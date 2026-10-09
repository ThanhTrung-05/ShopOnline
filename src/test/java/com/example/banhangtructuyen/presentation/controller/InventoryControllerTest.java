package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.inventory.InventoryAlertResponse;
import com.example.banhangtructuyen.application.dto.inventory.InventoryAlertResponse.AlertLevel;
import com.example.banhangtructuyen.application.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ATS-18: Security and response tests for GET /api/v1/admin/inventory/alerts.
 * Verifies:
 *   - ADMIN can access → 200
 *   - CUSTOMER is forbidden → 403
 *   - Anonymous is unauthorized → 401
 *   - Response fields are correctly serialized
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("ATS-18: InventoryController security and response tests")
class InventoryControllerTest {

    private static final String ADMIN_TOKEN    = "admin-token-inv";
    private static final String CUSTOMER_TOKEN = "customer-token-inv";
    private static final String CLIENT_ID      = "shoponline-backend";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode(ADMIN_TOKEN))
                .thenReturn(jwtWithRoles(ADMIN_TOKEN, "admin-subject", List.of("ADMIN")));
        when(jwtDecoder.decode(CUSTOMER_TOKEN))
                .thenReturn(jwtWithRoles(CUSTOMER_TOKEN, "customer-subject", List.of("CUSTOMER")));
    }

    @Test
    @DisplayName("ADMIN role receives 200 and alert list")
    void adminRole_canAccessAlerts() throws Exception {
        final List<InventoryAlertResponse> alerts = List.of(
                new InventoryAlertResponse(1L, "Bia Tiger", "bia-tiger", "Đồ uống",
                        BigDecimal.valueOf(18000), 0, 0, 0, AlertLevel.OUT_OF_STOCK),
                new InventoryAlertResponse(2L, "Sữa Vinamilk", "sua-vinamilk", "Sữa",
                        BigDecimal.valueOf(35000), 10, 5, 5, AlertLevel.LOW_STOCK)
        );
        when(inventoryService.getAlerts()).thenReturn(alerts);

        mockMvc.perform(get("/api/v1/admin/inventory/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].alertLevel").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.data[0].productName").value("Bia Tiger"))
                .andExpect(jsonPath("$.data[0].availableQuantity").value(0))
                .andExpect(jsonPath("$.data[1].alertLevel").value("LOW_STOCK"))
                .andExpect(jsonPath("$.data[1].availableQuantity").value(5));
    }

    @Test
    @DisplayName("CUSTOMER role receives 403 Forbidden")
    void customerRole_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inventory/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + CUSTOMER_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("anonymous request receives 401 Unauthorized")
    void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inventory/alerts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ADMIN with empty alert list receives 200 with empty data")
    void adminRole_emptyAlerts_returns200WithEmptyList() throws Exception {
        when(inventoryService.getAlerts()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/inventory/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // -------------------------------------------------------------------------

    private static Jwt jwtWithRoles(final String tokenValue, final String subject, final List<String> roles) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("resource_access", Map.of(
                        CLIENT_ID, Map.of("roles", roles)))
                .build();
    }
}
