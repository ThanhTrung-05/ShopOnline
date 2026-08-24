package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.cart.AddCartItemRequest;
import com.example.banhangtructuyen.application.dto.cart.CartItemResponse;
import com.example.banhangtructuyen.application.dto.cart.CartResponse;
import com.example.banhangtructuyen.application.dto.cart.CartViewItemResponse;
import com.example.banhangtructuyen.application.dto.cart.UpdateCartItemQuantityRequest;
import com.example.banhangtructuyen.application.service.CartService;
import com.example.banhangtructuyen.domain.exception.InsufficientInventoryException;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc controller tests for {@link CartController}.
 *
 * <p>ATS-14: Verifies that insufficient inventory is returned as HTTP 422 Unprocessable Entity
 * (not 400 Bad Request) when {@link InsufficientInventoryException} is thrown by the service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("CartController MockMvc Tests")
class CartControllerTest {

    private static final String SUBJECT = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CartService cartService;

    @Test
    @DisplayName("GET /api/v1/cart returns current cart with subtotal for CUSTOMER")
    void getCurrentCart_shouldReturnOk_whenCustomer() throws Exception {
        when(cartService.getCurrentCart(SUBJECT)).thenReturn(new CartResponse(
                List.of(new CartViewItemResponse(
                        1L,
                        10L,
                        "Lavie 500ml",
                        2,
                        new BigDecimal("5000.00"),
                        new BigDecimal("10000.00"))),
                new BigDecimal("10000.00")
        ));

        mockMvc.perform(get("/api/v1/cart")
                        .with(customerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].cartItemId").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(10))
                .andExpect(jsonPath("$.data.items[0].productName").value("Lavie 500ml"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(5000.00))
                .andExpect(jsonPath("$.data.items[0].itemSubtotal").value(10000.00))
                .andExpect(jsonPath("$.data.subtotal").value(10000.00));
    }

    @Test
    @DisplayName("GET /api/v1/cart returns empty cart")
    void getCurrentCart_shouldReturnEmptyCart() throws Exception {
        when(cartService.getCurrentCart(SUBJECT)).thenReturn(new CartResponse(List.of(), BigDecimal.ZERO));

        mockMvc.perform(get("/api/v1/cart")
                        .with(customerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.subtotal").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/cart returns 404 when Customer is not found")
    void getCurrentCart_shouldReturn404_whenCustomerNotFound() throws Exception {
        when(cartService.getCurrentCart(SUBJECT))
                .thenThrow(new ResourceNotFoundException("Customer", SUBJECT));

        mockMvc.perform(get("/api/v1/cart")
                        .with(customerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/cart returns 403 for ADMIN without CUSTOMER")
    void getCurrentCart_shouldReturn403_whenAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .with(adminJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/cart returns 401 for anonymous")
    void getCurrentCart_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/cart/items returns created cart item")
    void addItem_shouldReturnCreated() throws Exception {
        when(cartService.addItem(eq(SUBJECT), any(AddCartItemRequest.class)))
                .thenReturn(new CartItemResponse(1L, 10L, "Lavie 500ml", 2, new BigDecimal("5000.00")));

        mockMvc.perform(post("/api/v1/cart/items")
                        .with(customerJwt())
                        .contentType("application/json")
                        .content(toJson(new AddCartItemRequest(10L, 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value(10))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.unitPrice").value(5000.00));
    }

    @Test
    @DisplayName("quantity invalid (0) returns 400")
    void addItem_shouldReturn400_whenQuantityInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .with(customerJwt())
                        .contentType("application/json")
                        .content(toJson(new AddCartItemRequest(10L, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("resulting quantity over 1000 returns 400")
    void addItem_shouldReturn400_whenResultingQuantityTooLarge() throws Exception {
        when(cartService.addItem(eq(SUBJECT), any(AddCartItemRequest.class)))
                .thenThrow(new IllegalArgumentException("Cart item quantity must not exceed 1000"));

        mockMvc.perform(post("/api/v1/cart/items")
                        .with(customerJwt())
                        .contentType("application/json")
                        .content(toJson(new AddCartItemRequest(10L, 2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * ATS-14: Insufficient inventory returns HTTP 422 Unprocessable Entity.
     * This is the core ATS-14 controller-level verification.
     */
    @Test
    @DisplayName("ATS-14 — quantity over available stock returns 422 Unprocessable Entity")
    void addItem_shouldReturn422_whenQuantityExceedsAvailableStock() throws Exception {
        when(cartService.addItem(eq(SUBJECT), any(AddCartItemRequest.class)))
                .thenThrow(new InsufficientInventoryException(10L, 4, 3));

        mockMvc.perform(post("/api/v1/cart/items")
                        .with(customerJwt())
                        .contentType("application/json")
                        .content(toJson(new AddCartItemRequest(10L, 4))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("Product not found or inactive returns 404")
    void addItem_shouldReturn404_whenProductNotFoundOrInactive() throws Exception {
        when(cartService.addItem(eq(SUBJECT), any(AddCartItemRequest.class)))
                .thenThrow(new ResourceNotFoundException("Product", 99L));

        mockMvc.perform(post("/api/v1/cart/items")
                        .with(customerJwt())
                        .contentType("application/json")
                        .content(toJson(new AddCartItemRequest(99L, 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Cart database constraint conflict is not reported as duplicate email")
    void addItem_shouldReturnGeneric409_whenDatabaseConstraintFails() throws Exception {
        when(cartService.addItem(eq(SUBJECT), any(AddCartItemRequest.class)))
                .thenThrow(new DataIntegrityViolationException("UQ_CART_ITEMS_PRODUCT"));

        mockMvc.perform(post("/api/v1/cart/items")
                        .with(customerJwt())
                        .contentType("application/json")
                        .content(toJson(new AddCartItemRequest(10L, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Request conflicts with existing data"));
    }

    @Test
    @DisplayName("anonymous request returns 401")
    void addItem_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType("application/json")
                        .content(toJson(new AddCartItemRequest(10L, 1))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/v1/cart/items/{cartItemId} returns updated cart item")
    void updateItemQuantity_shouldReturnOk() throws Exception {
        when(cartService.updateItemQuantity(eq(SUBJECT), eq(1L), any(UpdateCartItemQuantityRequest.class)))
                .thenReturn(new CartItemResponse(1L, 10L, "Lavie 500ml", 5, new BigDecimal("5000.00")));

        mockMvc.perform(put("/api/v1/cart/items/1")
                        .with(customerJwt())
                        .contentType("application/json")
                        .content(toJson(new UpdateCartItemQuantityRequest(5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cartItemId").value(1))
                .andExpect(jsonPath("$.data.quantity").value(5));
    }

    @Test
    @DisplayName("update quantity 0 returns 400")
    void updateItemQuantity_shouldReturn400_whenQuantityIsZero() throws Exception {
        mockMvc.perform(put("/api/v1/cart/items/1")
                        .with(customerJwt())
                        .contentType("application/json")
                        .content(toJson(new UpdateCartItemQuantityRequest(0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("update quantity over 1000 returns 400")
    void updateItemQuantity_shouldReturn400_whenQuantityExceedsLimit() throws Exception {
        mockMvc.perform(put("/api/v1/cart/items/1")
                        .with(customerJwt())
                        .contentType("application/json")
                        .content(toJson(new UpdateCartItemQuantityRequest(1001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * ATS-14: Update quantity over available stock returns HTTP 422 Unprocessable Entity.
     */
    @Test
    @DisplayName("ATS-14 — update quantity over available stock returns 422 Unprocessable Entity")
    void updateItemQuantity_shouldReturn422_whenQuantityExceedsAvailableStock() throws Exception {
        when(cartService.updateItemQuantity(eq(SUBJECT), eq(1L), any(UpdateCartItemQuantityRequest.class)))
                .thenThrow(new InsufficientInventoryException(10L, 6, 5));

        mockMvc.perform(put("/api/v1/cart/items/1")
                        .with(customerJwt())
                        .contentType("application/json")
                        .content(toJson(new UpdateCartItemQuantityRequest(6))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("update missing or not-owned cart item returns 404")
    void updateItemQuantity_shouldReturn404_whenCartItemMissingOrNotOwned() throws Exception {
        when(cartService.updateItemQuantity(eq(SUBJECT), eq(99L), any(UpdateCartItemQuantityRequest.class)))
                .thenThrow(new ResourceNotFoundException("CartItem", 99L));

        mockMvc.perform(put("/api/v1/cart/items/99")
                        .with(customerJwt())
                        .contentType("application/json")
                        .content(toJson(new UpdateCartItemQuantityRequest(1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DELETE /api/v1/cart/items/{cartItemId} returns success")
    void removeItem_shouldReturnOk() throws Exception {
        mockMvc.perform(delete("/api/v1/cart/items/1")
                        .with(customerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("delete missing or not-owned cart item returns 404")
    void removeItem_shouldReturn404_whenCartItemMissingOrNotOwned() throws Exception {
        doThrow(new ResourceNotFoundException("CartItem", 99L))
                .when(cartService).removeItem(SUBJECT, 99L);

        mockMvc.perform(delete("/api/v1/cart/items/99")
                        .with(customerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    private String toJson(final Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static RequestPostProcessor customerJwt() {
        return jwt()
                .jwt(builder -> builder.subject(SUBJECT))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static RequestPostProcessor adminJwt() {
        return jwt()
                .jwt(builder -> builder.subject(SUBJECT))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
