package com.example.banhangtructuyen.config;

import com.example.banhangtructuyen.application.dto.customer.AddressResponse;
import com.example.banhangtructuyen.application.dto.customer.CustomerResponse;
import com.example.banhangtructuyen.application.service.AddressService;
import com.example.banhangtructuyen.application.service.AuthService;
import com.example.banhangtructuyen.application.service.CategoryService;
import com.example.banhangtructuyen.application.service.CustomerProfileService;
import com.example.banhangtructuyen.application.service.ProductService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real resource-server filter path for customer endpoints:
 * BearerTokenAuthenticationFilter -> JwtDecoder -> configured JwtAuthenticationConverter
 * -> KeycloakRoleConverter -> ROLE_CUSTOMER -> hasRole("CUSTOMER").
 *
 * <p>JwtDecoder is mocked to avoid signing-key and issuer/audience network dependencies. This
 * validates the SecurityFilterChain, JwtAuthenticationConverter, KeycloakRoleConverter, and
 * authorization rules. It does not retest signature, issuer, audience, or timestamp validation;
 * those are covered by the JWT validator unit tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Customer endpoint security integration")
class CustomerEndpointSecurityIntegrationTest {

    private static final String CLIENT_ID = "shoponline-backend";
    private static final String CUSTOMER_SUBJECT = "customer-subject";
    private static final String CUSTOMER_TOKEN = "customer-token";
    private static final String ADMIN_TOKEN = "admin-token";
    private static final String EMPTY_ROLES_TOKEN = "empty-roles-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private CustomerProfileService customerProfileService;

    @MockBean
    private AddressService addressService;

    @MockBean
    private ProductService productService;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode(CUSTOMER_TOKEN))
                .thenReturn(jwtWithRoles(CUSTOMER_TOKEN, CUSTOMER_SUBJECT, List.of("CUSTOMER")));
        when(jwtDecoder.decode(ADMIN_TOKEN))
                .thenReturn(jwtWithRoles(ADMIN_TOKEN, "admin-subject", List.of("ADMIN")));
        when(jwtDecoder.decode(EMPTY_ROLES_TOKEN))
                .thenReturn(jwtWithRoles(EMPTY_ROLES_TOKEN, "no-customer-role-subject", List.of()));
    }

    @Test
    @DisplayName("CUSTOMER role can access current profile")
    void customerRole_canAccessCurrentProfile() throws Exception {
        when(customerProfileService.getProfile(CUSTOMER_SUBJECT)).thenReturn(sampleCustomerResponse());

        mockMvc.perform(get("/api/v1/customers/me").header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_TOKEN)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN-only role cannot access customer profile")
    void adminOnlyRole_cannotAccessCurrentProfile() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("token without CUSTOMER role cannot access customer profile")
    void tokenWithoutCustomerRole_cannotAccessCurrentProfile() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me").header(HttpHeaders.AUTHORIZATION, bearer(EMPTY_ROLES_TOKEN)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("anonymous request to customer profile is unauthorized")
    void anonymousRequest_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CUSTOMER role can access own address endpoint")
    void customerRole_canAccessOwnAddressEndpoint() throws Exception {
        when(addressService.getAddress(CUSTOMER_SUBJECT, 1L)).thenReturn(sampleAddressResponse());

        mockMvc.perform(get("/api/v1/customers/me/addresses/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_TOKEN)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("owned-address lookup returning not found remains 404, not 403")
    void otherCustomerAddress_returnsNotFound() throws Exception {
        when(addressService.getAddress(eq(CUSTOMER_SUBJECT), eq(99L)))
                .thenThrow(new ResourceNotFoundException("Address", 99L));

        mockMvc.perform(get("/api/v1/customers/me/addresses/99")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_TOKEN)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("product GET remains public")
    void productGet_remainsPublic() throws Exception {
        when(productService.findAll(0, 20, null, null)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("category GET remains public")
    void categoryGet_remainsPublic() throws Exception {
        when(categoryService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("anonymous product write is unauthorized")
    void anonymousProductWrite_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CUSTOMER product write is forbidden")
    void customerProductWrite_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_TOKEN))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN product write reaches controller")
    void adminProductWrite_reachesController() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("anonymous category write is unauthorized")
    void anonymousCategoryWrite_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CUSTOMER category write is forbidden")
    void customerCategoryWrite_isForbidden() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_TOKEN))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN category write reaches controller")
    void adminCategoryWrite_reachesController() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("registration remains public")
    void registerPost_remainsPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Swagger API docs path remains unauthenticated")
    void apiDocs_remainPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("session endpoint remains authenticated-only, not CUSTOMER-only")
    void sessionEndpoint_remainsAuthenticatedOnly() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                .andExpect(status().isOk());
    }

    private static String bearer(final String token) {
        return "Bearer " + token;
    }

    private static Jwt jwtWithRoles(final String tokenValue, final String subject, final List<String> roles) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.parse("2026-08-12T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-12T01:00:00Z"))
                .claim("iss", "http://localhost:8081/realms/shoponline")
                .audience(List.of(CLIENT_ID))
                .claim("resource_access", Map.of(CLIENT_ID, Map.of("roles", roles)))
                .build();
    }

    private static CustomerResponse sampleCustomerResponse() {
        return new CustomerResponse(1L, "customer@example.com", "Nguyen Van A", "0987654321",
                "USER", "ACTIVE", Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"));
    }

    private static AddressResponse sampleAddressResponse() {
        return new AddressResponse(1L, "Nguyen Van A", "0987654321", "123 Le Loi",
                "Ben Nghe", "District 1", "Ho Chi Minh City", true);
    }
}
