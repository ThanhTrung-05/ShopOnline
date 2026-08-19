package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.cart.AddCartItemRequest;
import com.example.banhangtructuyen.application.dto.cart.CartItemResponse;
import com.example.banhangtructuyen.application.dto.cart.CartResponse;
import com.example.banhangtructuyen.application.dto.cart.UpdateCartItemQuantityRequest;
import com.example.banhangtructuyen.application.service.impl.CartServiceImpl;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Cart;
import com.example.banhangtructuyen.domain.model.CartItem;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Inventory;
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
import java.util.List;
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
        return sampleProduct(10);
    }

    private static Product sampleProduct(final int availableStock) {
        final Product product = Product.builder()
                .productId(PRODUCT_ID)
                .productName("Lavie 500ml")
                .price(new BigDecimal("5000.00"))
                .status(Product.ProductStatus.ACTIVE)
                .build();
        final Inventory inventory = Inventory.builder()
                .inventoryId(500L)
                .product(product)
                .quantity(availableStock)
                .reservedQuantity(0)
                .build();
        product.setInventory(inventory);
        return product;
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

    private static CartItem sampleCartItem(
            final Long cartItemId,
            final Cart cart,
            final Product product,
            final int quantity,
            final String unitPrice) {
        return CartItem.builder()
                .cartItemId(cartItemId)
                .cart(cart)
                .product(product)
                .quantity(quantity)
                .unitPrice(new BigDecimal(unitPrice))
                .build();
    }

    private void stubCustomerAndProduct() {
        when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
        when(productRepository.findActiveById(PRODUCT_ID)).thenReturn(Optional.of(sampleProduct()));
    }

    @Nested
    @DisplayName("getCurrentCart")
    class GetCurrentCart {

        @Test
        @DisplayName("cart with one item returns item and subtotal")
        void getCurrentCart_shouldReturnOneItemAndSubtotal() {
            final Cart cart = sampleCart();
            final Product product = sampleProduct();
            final CartItem item = sampleCartItem(1000L, cart, product, 2, "5000.00");
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
            when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findViewItemsByCustomerId(CUSTOMER_ID)).thenReturn(List.of(item));

            final CartResponse response = service.getCurrentCart(SUBJECT);

            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).cartItemId()).isEqualTo(1000L);
            assertThat(response.items().get(0).productId()).isEqualTo(PRODUCT_ID);
            assertThat(response.items().get(0).productName()).isEqualTo("Lavie 500ml");
            assertThat(response.items().get(0).quantity()).isEqualTo(2);
            assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("5000.00");
            assertThat(response.items().get(0).itemSubtotal()).isEqualByComparingTo("10000.00");
            assertThat(response.subtotal()).isEqualByComparingTo("10000.00");
        }

        @Test
        @DisplayName("cart with multiple items sums item subtotals")
        void getCurrentCart_shouldReturnMultipleItemsAndSubtotal() {
            final Cart cart = sampleCart();
            final Product productOne = sampleProduct();
            final Product productTwo = Product.builder()
                    .productId(200L)
                    .productName("Snack")
                    .price(new BigDecimal("9999.00"))
                    .status(Product.ProductStatus.ACTIVE)
                    .build();
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
            when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findViewItemsByCustomerId(CUSTOMER_ID)).thenReturn(List.of(
                    sampleCartItem(1000L, cart, productOne, 2, "5000.00"),
                    sampleCartItem(1001L, cart, productTwo, 3, "12000.00")
            ));

            final CartResponse response = service.getCurrentCart(SUBJECT);

            assertThat(response.items()).hasSize(2);
            assertThat(response.items().get(0).itemSubtotal()).isEqualByComparingTo("10000.00");
            assertThat(response.items().get(1).itemSubtotal()).isEqualByComparingTo("36000.00");
            assertThat(response.subtotal()).isEqualByComparingTo("46000.00");
        }

        @Test
        @DisplayName("uses CartItem unitPrice even when Product price differs")
        void getCurrentCart_shouldUseCartItemUnitPrice_whenProductPriceDiffers() {
            final Cart cart = sampleCart();
            final Product product = Product.builder()
                    .productId(PRODUCT_ID)
                    .productName("Lavie 500ml")
                    .price(new BigDecimal("9999.00"))
                    .status(Product.ProductStatus.ACTIVE)
                    .build();
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
            when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findViewItemsByCustomerId(CUSTOMER_ID)).thenReturn(List.of(
                    sampleCartItem(1000L, cart, product, 4, "5000.00")
            ));

            final CartResponse response = service.getCurrentCart(SUBJECT);

            assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("5000.00");
            assertThat(response.items().get(0).itemSubtotal()).isEqualByComparingTo("20000.00");
            assertThat(response.subtotal()).isEqualByComparingTo("20000.00");
            verify(productRepository, never()).findActiveById(any());
        }

        @Test
        @DisplayName("empty cart returns empty items and zero subtotal")
        void getCurrentCart_shouldReturnEmpty_whenCartHasNoItems() {
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
            when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(sampleCart()));
            when(cartItemRepository.findViewItemsByCustomerId(CUSTOMER_ID)).thenReturn(List.of());

            final CartResponse response = service.getCurrentCart(SUBJECT);

            assertThat(response.items()).isEmpty();
            assertThat(response.subtotal()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("no cart returns empty items and zero subtotal")
        void getCurrentCart_shouldReturnEmpty_whenCustomerHasNoCart() {
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
            when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

            final CartResponse response = service.getCurrentCart(SUBJECT);

            assertThat(response.items()).isEmpty();
            assertThat(response.subtotal()).isEqualByComparingTo("0");
            verify(cartItemRepository, never()).findViewItemsByCustomerId(any());
        }

        @Test
        @DisplayName("customer not found returns ResourceNotFound")
        void getCurrentCart_shouldThrow_whenCustomerSubjectNotFound() {
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCurrentCart(SUBJECT))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Customer");

            verify(cartRepository, never()).findByCustomerId(any());
            verify(cartItemRepository, never()).findViewItemsByCustomerId(any());
        }
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
        @DisplayName("new item quantity below available stock succeeds")
        void addItem_shouldSucceed_whenNewItemQuantityBelowStock() {
            final Product product = sampleProduct(5);
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
            when(productRepository.findActiveById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());
            when(cartRepository.save(any(Cart.class))).thenReturn(sampleCart());
            when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));

            final CartItemResponse response = service.addItem(SUBJECT, new AddCartItemRequest(PRODUCT_ID, 4));

            assertThat(response.quantity()).isEqualTo(4);
            assertThat(product.getInventory().getQuantity()).isEqualTo(5);
            assertThat(product.getInventory().getReservedQuantity()).isZero();
        }

        @Test
        @DisplayName("new item quantity equal to available stock succeeds")
        void addItem_shouldSucceed_whenNewItemQuantityEqualsStock() {
            final Product product = sampleProduct(5);
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
            when(productRepository.findActiveById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());
            when(cartRepository.save(any(Cart.class))).thenReturn(sampleCart());
            when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));

            final CartItemResponse response = service.addItem(SUBJECT, new AddCartItemRequest(PRODUCT_ID, 5));

            assertThat(response.quantity()).isEqualTo(5);
            assertThat(product.getInventory().getQuantity()).isEqualTo(5);
            assertThat(product.getInventory().getReservedQuantity()).isZero();
        }

        @Test
        @DisplayName("new item quantity over available stock is rejected without creating cart or item")
        void addItem_shouldRejectAndNotCreateCartOrItem_whenNewItemQuantityExceedsStock() {
            final Product product = sampleProduct(3);
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
            when(productRepository.findActiveById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addItem(SUBJECT, new AddCartItemRequest(PRODUCT_ID, 4)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Requested quantity exceeds available stock");

            verify(cartRepository, never()).save(any());
            verify(cartItemRepository, never()).save(any());
            assertThat(product.getInventory().getQuantity()).isEqualTo(3);
            assertThat(product.getInventory().getReservedQuantity()).isZero();
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
        @DisplayName("existing item resulting quantity over available stock is rejected without changing quantity")
        void addItem_shouldRejectAndKeepOldQuantity_whenResultingQuantityExceedsStock() {
            final Cart cart = sampleCart();
            final Product product = sampleProduct(5);
            final CartItem existingItem = sampleCartItem(cart, product, 3);
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
            when(productRepository.findActiveById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCart_CartIdAndProduct_ProductId(CART_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existingItem));

            assertThatThrownBy(() -> service.addItem(SUBJECT, new AddCartItemRequest(PRODUCT_ID, 3)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Requested quantity exceeds available stock");

            assertThat(existingItem.getQuantity()).isEqualTo(3);
            verify(cartItemRepository, never()).save(any());
            assertThat(product.getInventory().getQuantity()).isEqualTo(5);
            assertThat(product.getInventory().getReservedQuantity()).isZero();
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

    @Nested
    @DisplayName("updateItemQuantity")
    class UpdateItemQuantity {

        @Test
        @DisplayName("owned CartItem quantity is replaced")
        void updateItemQuantity_shouldReplaceQuantity_whenItemBelongsToCustomer() {
            final Cart cart = sampleCart();
            final Product product = sampleProduct();
            final CartItem item = sampleCartItem(cart, product, 3);
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
            when(cartItemRepository.findByCart_CustomerIdAndCartItemId(CUSTOMER_ID, 1000L))
                    .thenReturn(Optional.of(item));
            when(cartItemRepository.save(item)).thenReturn(item);

            final CartItemResponse response = service.updateItemQuantity(
                    SUBJECT, 1000L, new UpdateCartItemQuantityRequest(9));

            assertThat(response.quantity()).isEqualTo(9);
            verify(productRepository, never()).findActiveById(any());
        }

        @Test
        @DisplayName("update quantity equal to available stock succeeds")
        void updateItemQuantity_shouldSucceed_whenQuantityEqualsStock() {
            final Cart cart = sampleCart();
            final Product product = sampleProduct(5);
            final CartItem item = sampleCartItem(cart, product, 3);
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
            when(cartItemRepository.findByCart_CustomerIdAndCartItemId(CUSTOMER_ID, 1000L))
                    .thenReturn(Optional.of(item));
            when(cartItemRepository.save(item)).thenReturn(item);

            final CartItemResponse response = service.updateItemQuantity(
                    SUBJECT, 1000L, new UpdateCartItemQuantityRequest(5));

            assertThat(response.quantity()).isEqualTo(5);
            assertThat(product.getInventory().getQuantity()).isEqualTo(5);
            assertThat(product.getInventory().getReservedQuantity()).isZero();
        }

        @Test
        @DisplayName("update quantity over available stock is rejected without changing quantity")
        void updateItemQuantity_shouldRejectAndKeepOldQuantity_whenQuantityExceedsStock() {
            final Cart cart = sampleCart();
            final Product product = sampleProduct(5);
            final CartItem item = sampleCartItem(cart, product, 3);
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
            when(cartItemRepository.findByCart_CustomerIdAndCartItemId(CUSTOMER_ID, 1000L))
                    .thenReturn(Optional.of(item));

            assertThatThrownBy(() -> service.updateItemQuantity(
                    SUBJECT, 1000L, new UpdateCartItemQuantityRequest(6)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Requested quantity exceeds available stock");

            assertThat(item.getQuantity()).isEqualTo(3);
            verify(cartItemRepository, never()).save(any());
            assertThat(product.getInventory().getQuantity()).isEqualTo(5);
            assertThat(product.getInventory().getReservedQuantity()).isZero();
        }

        @Test
        @DisplayName("missing CartItem is rejected")
        void updateItemQuantity_shouldThrow_whenCartItemMissing() {
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
            when(cartItemRepository.findByCart_CustomerIdAndCartItemId(CUSTOMER_ID, 404L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateItemQuantity(
                    SUBJECT, 404L, new UpdateCartItemQuantityRequest(1)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("CartItem");

            verify(cartItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("not-owned CartItem is rejected as not found")
        void updateItemQuantity_shouldThrowNotFound_whenCartItemBelongsToAnotherCustomer() {
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
            when(cartItemRepository.findByCart_CustomerIdAndCartItemId(CUSTOMER_ID, 2000L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateItemQuantity(
                    SUBJECT, 2000L, new UpdateCartItemQuantityRequest(1)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("CartItem not found with id: 2000");

            verify(cartItemRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("removeItem")
    class RemoveItem {

        @Test
        @DisplayName("owned CartItem is deleted")
        void removeItem_shouldDelete_whenItemBelongsToCustomer() {
            final Product product = sampleProduct(5);
            final CartItem item = sampleCartItem(sampleCart(), product, 3);
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
            when(cartItemRepository.findByCart_CustomerIdAndCartItemId(CUSTOMER_ID, 1000L))
                    .thenReturn(Optional.of(item));

            service.removeItem(SUBJECT, 1000L);

            verify(cartItemRepository).delete(item);
            verify(productRepository, never()).findActiveById(any());
            assertThat(product.getInventory().getQuantity()).isEqualTo(5);
            assertThat(product.getInventory().getReservedQuantity()).isZero();
        }

        @Test
        @DisplayName("missing or not-owned CartItem is rejected as not found")
        void removeItem_shouldThrowNotFound_whenCartItemMissingOrNotOwned() {
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
            when(cartItemRepository.findByCart_CustomerIdAndCartItemId(CUSTOMER_ID, 2000L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.removeItem(SUBJECT, 2000L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("CartItem not found with id: 2000");

            verify(cartItemRepository, never()).delete(any());
        }
    }
}
