package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.cart.AddCartItemRequest;
import com.example.banhangtructuyen.application.dto.cart.CartItemResponse;
import com.example.banhangtructuyen.application.service.impl.CartServiceImpl;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Cart;
import com.example.banhangtructuyen.domain.model.CartItem;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Product;
import com.example.banhangtructuyen.domain.repository.CartItemRepository;
import com.example.banhangtructuyen.domain.repository.CartRepository;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import com.example.banhangtructuyen.domain.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    private static final String SUBJECT = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final Long CUSTOMER_ID = 1L;
    private static final Long CART_ID = 10L;
    private static final Long PRODUCT_ID = 100L;

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private ProductRepository productRepository;

    private CartServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CartServiceImpl(cartRepository, cartItemRepository, customerRepository, productRepository);
    }

    private static Customer sampleCustomer() {
        return Customer.builder()
                .customerId(CUSTOMER_ID)
                .email("customer@example.com")
                .fullName("Nguyen Van A")
                .passwordHash("$2a$12$hashedvalue")
                .keycloakUserId(SUBJECT)
                .status(Customer.CustomerStatus.ACTIVE)
                .role(Customer.CustomerRole.USER)
                .build();
    }

    private static Cart sampleCart() {
        return Cart.builder()
                .cartId(CART_ID)
                .customerId(CUSTOMER_ID)
                .build();
    }

    private static Product sampleProduct() {
        return Product.builder()
                .productId(PRODUCT_ID)
                .productName("Lavie 500ml")
                .price(new BigDecimal("5000.00"))
                .status(Product.ProductStatus.ACTIVE)
                .build();
    }

    private static CartItem sampleCartItem(final Cart cart, final Product product, final int quantity) {
        return CartItem.builder()
                .cartItemId(1000L)
                .cart(cart)
                .product(product)
                .quantity(quantity)
                .unitPrice(product.getPrice())
                .build();
    }

    private void stubCustomerAndProduct() {
        when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
        when(productRepository.findActiveById(PRODUCT_ID)).thenReturn(Optional.of(sampleProduct()));
    }

    @Nested
    @DisplayName("addItem")
    class AddItem {

        @Test
        @DisplayName("first add creates Cart and CartItem")
        void addItem_shouldCreateCartAndItem_whenCustomerHasNoCart() {
            stubCustomerAndProduct();
            final Cart createdCart = sampleCart();
            when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());
            when(cartRepository.save(any(Cart.class))).thenReturn(createdCart);
            when(cartItemRepository.findByCart_CartIdAndProduct_ProductId(CART_ID, PRODUCT_ID)).thenReturn(Optional.empty());
            when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> {
                final CartItem item = inv.getArgument(0);
                item.setCartItemId(1000L);
                return item;
            });

            final CartItemResponse response = service.addItem(SUBJECT, new AddCartItemRequest(PRODUCT_ID, 2));

            assertThat(response.quantity()).isEqualTo(2);
            assertThat(response.unitPrice()).isEqualByComparingTo("5000.00");
            final ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);
            verify(cartRepository).save(cartCaptor.capture());
            assertThat(cartCaptor.getValue().getCustomerId()).isEqualTo(CUSTOMER_ID);
        }

        @Test
        @DisplayName("existing Cart is reused")
        void addItem_shouldReuseExistingCart() {
            stubCustomerAndProduct();
            final Cart existingCart = sampleCart();
            when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(existingCart));
            when(cartItemRepository.findByCart_CartIdAndProduct_ProductId(CART_ID, PRODUCT_ID)).thenReturn(Optional.empty());
            when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));

            service.addItem(SUBJECT, new AddCartItemRequest(PRODUCT_ID, 1));

            verify(cartRepository, never()).save(any(Cart.class));
        }

        @Test
        @DisplayName("same Product increments quantity")
        void addItem_shouldIncrementQuantity_whenProductAlreadyInCart() {
            stubCustomerAndProduct();
            final Cart cart = sampleCart();
            final Product product = sampleProduct();
            final CartItem existingItem = sampleCartItem(cart, product, 3);
            when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCart_CartIdAndProduct_ProductId(CART_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existingItem));
            when(cartItemRepository.save(existingItem)).thenReturn(existingItem);

            final CartItemResponse response = service.addItem(SUBJECT, new AddCartItemRequest(PRODUCT_ID, 4));

            assertThat(response.quantity()).isEqualTo(7);
        }

        @Test
        @DisplayName("resulting quantity over 1000 is rejected")
        void addItem_shouldReject_whenResultingQuantityExceedsLimit() {
            stubCustomerAndProduct();
            final Cart cart = sampleCart();
            final Product product = sampleProduct();
            when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCart_CartIdAndProduct_ProductId(CART_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(sampleCartItem(cart, product, 999)));

            assertThatThrownBy(() -> service.addItem(SUBJECT, new AddCartItemRequest(PRODUCT_ID, 2)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1000");

            verify(cartItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Product not found or inactive is rejected without creating Cart")
        void addItem_shouldThrowAndNotCreateCart_whenProductNotFoundOrInactive() {
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
            when(productRepository.findActiveById(PRODUCT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addItem(SUBJECT, new AddCartItemRequest(PRODUCT_ID, 1)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product");

            verify(cartRepository, never()).findByCustomerId(any());
            verify(cartRepository, never()).save(any());
            verify(cartItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("customer is resolved from JWT subject")
        void addItem_shouldThrow_whenCustomerSubjectNotFound() {
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addItem(SUBJECT, new AddCartItemRequest(PRODUCT_ID, 1)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Customer");
        }
    }
}
