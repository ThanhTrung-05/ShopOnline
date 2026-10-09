package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.order.CreateOrderResponse;
import com.example.banhangtructuyen.application.dto.shipping.ShippingCheckoutInfo;
import com.example.banhangtructuyen.application.dto.shipping.ShippingSelectionRequest;
import com.example.banhangtructuyen.application.service.impl.OrderCreationServiceImpl;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Cart;
import com.example.banhangtructuyen.domain.model.CartItem;
import com.example.banhangtructuyen.domain.model.Category;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Inventory;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderItem;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import com.example.banhangtructuyen.domain.model.Product;
import com.example.banhangtructuyen.domain.model.ShippingMethod;
import com.example.banhangtructuyen.domain.model.ShippingRegion;
import com.example.banhangtructuyen.domain.repository.CartItemRepository;
import com.example.banhangtructuyen.domain.repository.CartRepository;
import com.example.banhangtructuyen.domain.repository.InventoryRepository;
import com.example.banhangtructuyen.domain.repository.OrderItemRepository;
import com.example.banhangtructuyen.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OrderCreationServiceImpl.
 * Updated for ATS-26: CartItems now include Category with vatRate,
 * and all total/VAT assertions use the correct expected values.
 *
 * Sample data (VAT 10%):
 *   Product A: 12000 x 2 = subtotal 24000, vatAmount 2400
 *   Product B:  5000 x 3 = subtotal 15000, vatAmount 1500
 *   totalBeforeVat = 39000
 *   totalVatAmount =  3900
 *   shippingFee    = 10000
 *   totalAmount    = 52900
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCreationService")
class OrderCreationServiceImplTest {

    private static final String SUBJECT = "customer-subject";
    private static final Long CUSTOMER_ID = 7L;
    private static final Long CART_ID = 11L;
    private static final Instant CREATED_AT = Instant.parse("2026-09-07T02:00:00Z");
    private static final ShippingSelectionRequest REQUEST =
            new ShippingSelectionRequest(42L, ShippingMethod.STANDARD);

    // Expected totals with VAT 10%
    private static final BigDecimal TOTAL_BEFORE_VAT  = new BigDecimal("39000");
    private static final BigDecimal TOTAL_VAT_AMOUNT  = new BigDecimal("3900");
    private static final BigDecimal SHIPPING_FEE      = new BigDecimal("10000.00");
    private static final BigDecimal TOTAL_AMOUNT      = new BigDecimal("52900");

    @Mock private AuthenticatedCustomerResolver authenticatedCustomerResolver;
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ShippingPreparationService shippingPreparationService;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductService productService;
    @Mock private OutboxEventPublisher outboxEventPublisher;

    private OrderCreationService service;

    @BeforeEach
    void setUp() {
        service = new OrderCreationServiceImpl(
                authenticatedCustomerResolver,
                cartRepository,
                cartItemRepository,
                shippingPreparationService,
                orderRepository,
                orderItemRepository,
                inventoryRepository,
                productService,
                outboxEventPublisher);
    }

    // ==========================================================================
    // Regression tests (pre-ATS-26) — updated expected values for new VAT totals
    // ==========================================================================

    @Test
    @DisplayName("creates trusted PENDING snapshots, totals them, and clears the locked cart")
    void createOrder_shouldPersistSnapshotsAndClearCart() {
        final List<CartItem> cartItems = sampleCartItems();
        stubCart(cartItems);
        when(shippingPreparationService.prepareShipping(SUBJECT, REQUEST))
                .thenReturn(sampleShipping(CUSTOMER_ID));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            final Order order = invocation.getArgument(0);
            order.setOrderId(100L);
            order.setCreatedAt(CREATED_AT);
            order.setUpdatedAt(CREATED_AT);
            return order;
        });

        when(inventoryRepository.findByProductIdWithLock(101L))
                .thenReturn(Optional.of(Inventory.builder().inventoryId(1L).quantity(10).reservedQuantity(0).version(1L).build()));
        when(inventoryRepository.findByProductIdWithLock(102L))
                .thenReturn(Optional.of(Inventory.builder().inventoryId(2L).quantity(10).reservedQuantity(0).version(1L).build()));

        final CreateOrderResponse response = service.createOrder(SUBJECT, REQUEST);

        // Verify Order persisted with VAT breakdown (ATS-26)
        final ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(orderCaptor.capture());
        final Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(savedOrder.getOrderNumber()).matches("ORD-\\d{8}-[0-9A-F]{8}");
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(savedOrder.getTotalBeforeVat()).isEqualByComparingTo(TOTAL_BEFORE_VAT);
        assertThat(savedOrder.getTotalVatAmount()).isEqualByComparingTo(TOTAL_VAT_AMOUNT);
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(TOTAL_AMOUNT);
        assertThat(savedOrder.getShippingAddress()).isEqualTo(
                "Nguyen Van A, 0987654321, 123 Le Loi, Ben Nghe, District 1, Ha Noi");

        // Verify OrderItems with per-line VAT (ATS-26)
        @SuppressWarnings({"rawtypes", "unchecked"})
        final ArgumentCaptor<Iterable<OrderItem>> itemsCaptor =
                ArgumentCaptor.forClass((Class) Iterable.class);
        verify(orderItemRepository).saveAll(itemsCaptor.capture());
        final List<OrderItem> savedItems = new ArrayList<>();
        itemsCaptor.getValue().forEach(savedItems::add);
        assertThat(savedItems).hasSize(2);
        assertThat(savedItems).allSatisfy(item -> assertThat(item.getOrder()).isSameAs(savedOrder));
        assertThat(savedItems).extracting(
                        OrderItem::getProductId,
                        OrderItem::getProductName,
                        OrderItem::getUnitPrice,
                        OrderItem::getQuantity,
                        i -> i.getSubtotal().toPlainString(),
                        i -> i.getVatRate().toPlainString(),
                        i -> i.getVatAmount().toPlainString())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                101L, "Product A", new BigDecimal("12000.00"), 2,
                                "24000.00", "10.00", "2400"),
                        org.assertj.core.groups.Tuple.tuple(
                                102L, "Product B", new BigDecimal("5000.00"), 3,
                                "15000.00", "10.00", "1500"));

        // Verify response contains VAT breakdown (ATS-26)
        assertThat(response.orderNumber()).isEqualTo(savedOrder.getOrderNumber());
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalBeforeVat()).isEqualByComparingTo(TOTAL_BEFORE_VAT);
        assertThat(response.totalVatAmount()).isEqualByComparingTo(TOTAL_VAT_AMOUNT);
        assertThat(response.shippingFee()).isEqualByComparingTo(SHIPPING_FEE);
        assertThat(response.totalAmount()).isEqualByComparingTo(TOTAL_AMOUNT);
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);

        verify(cartItemRepository).deleteAll(cartItems);
        verify(cartItemRepository).flush();
        final InOrder writeOrder = inOrder(orderRepository, orderItemRepository, cartItemRepository);
        writeOrder.verify(orderRepository).saveAndFlush(any(Order.class));
        writeOrder.verify(orderItemRepository).saveAll(anyList());
        writeOrder.verify(cartItemRepository).deleteAll(cartItems);
        writeOrder.verify(cartItemRepository).flush();
    }

    @Test
    @DisplayName("rejects a missing lifetime cart as empty before shipping or persistence")
    void createOrder_shouldRejectMissingCart() {
        when(authenticatedCustomerResolver.resolveActiveCustomer(SUBJECT))
                .thenReturn(activeCustomer());
        when(cartRepository.findByCustomerIdForUpdate(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createOrder(SUBJECT, REQUEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cart must contain at least one item");

        verifyNoInteractions(shippingPreparationService, orderRepository, orderItemRepository);
        verify(cartItemRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("rejects an empty cart before shipping or persistence")
    void createOrder_shouldRejectEmptyCart() {
        stubCart(List.of());

        assertThatThrownBy(() -> service.createOrder(SUBJECT, REQUEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cart must contain at least one item");

        verifyNoInteractions(shippingPreparationService, orderRepository, orderItemRepository);
        verify(cartItemRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("propagates ownership-safe foreign-address failure without writing or clearing")
    void createOrder_shouldRejectForeignAddressWithoutMutation() {
        final List<CartItem> cartItems = sampleCartItems();
        stubCart(cartItems);
        when(shippingPreparationService.prepareShipping(SUBJECT, REQUEST))
                .thenThrow(new ResourceNotFoundException("Address", REQUEST.addressId()));

        assertThatThrownBy(() -> service.createOrder(SUBJECT, REQUEST))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Address not found with id: 42");

        verifyNoInteractions(orderRepository, orderItemRepository);
        verify(cartItemRepository, never()).deleteAll(cartItems);
    }

    @Test
    @DisplayName("propagates unsupported shipping failure without writing or clearing")
    void createOrder_shouldRejectUnsupportedShippingWithoutMutation() {
        final ShippingSelectionRequest unsupportedRequest =
                new ShippingSelectionRequest(42L, ShippingMethod.EXPRESS);
        final List<CartItem> cartItems = sampleCartItems();
        stubCart(cartItems);
        when(shippingPreparationService.prepareShipping(SUBJECT, unsupportedRequest))
                .thenThrow(new IllegalArgumentException(
                        "EXPRESS shipping is not supported for region OTHER"));

        assertThatThrownBy(() -> service.createOrder(SUBJECT, unsupportedRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EXPRESS shipping is not supported for region OTHER");

        verifyNoInteractions(orderRepository, orderItemRepository);
        verify(cartItemRepository, never()).deleteAll(cartItems);
    }

    @Test
    @DisplayName("does not clear the cart when order-item persistence fails")
    void createOrder_shouldNotClearCartWhenOrderItemPersistenceFails() {
        final List<CartItem> cartItems = sampleCartItems();
        stubCart(cartItems);
        when(shippingPreparationService.prepareShipping(SUBJECT, REQUEST))
                .thenReturn(sampleShipping(CUSTOMER_ID));

        when(inventoryRepository.findByProductIdWithLock(101L))
                .thenReturn(Optional.of(Inventory.builder().inventoryId(1L).quantity(10).reservedQuantity(0).version(1L).build()));
        when(inventoryRepository.findByProductIdWithLock(102L))
                .thenReturn(Optional.of(Inventory.builder().inventoryId(2L).quantity(10).reservedQuantity(0).version(1L).build()));

        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAll(anyList()))
                .thenThrow(new DataIntegrityViolationException("order item insert failed"));

        assertThatThrownBy(() -> service.createOrder(SUBJECT, REQUEST))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(cartItemRepository, never()).deleteAll(cartItems);
        verify(cartItemRepository, never()).flush();
    }

    // ==========================================================================
    // ATS-26: VAT calculation tests
    // ==========================================================================

    @Nested
    @DisplayName("ATS-26: VAT calculation")
    class VatCalculation {

        @Test
        @DisplayName("computes correct VAT for 10% rate: subtotal*10/100 rounded HALF_UP")
        void vatAmount_10Percent() {
            // 24000 * 10% = 2400 (exact, no rounding needed)
            stubCart(sampleCartItems());
            when(shippingPreparationService.prepareShipping(SUBJECT, REQUEST))
                    .thenReturn(sampleShipping(CUSTOMER_ID));
            when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setOrderId(1L); o.setCreatedAt(CREATED_AT); o.setUpdatedAt(CREATED_AT);
                return o;
            });
            when(inventoryRepository.findByProductIdWithLock(101L))
                    .thenReturn(Optional.of(Inventory.builder().inventoryId(1L).quantity(10).reservedQuantity(0).version(1L).build()));
            when(inventoryRepository.findByProductIdWithLock(102L))
                    .thenReturn(Optional.of(Inventory.builder().inventoryId(2L).quantity(10).reservedQuantity(0).version(1L).build()));

            final CreateOrderResponse response = service.createOrder(SUBJECT, REQUEST);

            assertThat(response.totalBeforeVat()).isEqualByComparingTo("39000");
            assertThat(response.totalVatAmount()).isEqualByComparingTo("3900");
            assertThat(response.totalAmount()).isEqualByComparingTo("52900");
        }

        @Test
        @DisplayName("computes correct VAT for 5% rate")
        void vatAmount_5Percent() {
            final List<CartItem> items = sampleCartItemsWithVatRate("5.00");
            stubCart(items);
            when(shippingPreparationService.prepareShipping(SUBJECT, REQUEST))
                    .thenReturn(sampleShipping(CUSTOMER_ID));
            when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setOrderId(1L); o.setCreatedAt(CREATED_AT); o.setUpdatedAt(CREATED_AT);
                return o;
            });
            when(inventoryRepository.findByProductIdWithLock(101L))
                    .thenReturn(Optional.of(Inventory.builder().inventoryId(1L).quantity(10).reservedQuantity(0).version(1L).build()));
            when(inventoryRepository.findByProductIdWithLock(102L))
                    .thenReturn(Optional.of(Inventory.builder().inventoryId(2L).quantity(10).reservedQuantity(0).version(1L).build()));

            final CreateOrderResponse response = service.createOrder(SUBJECT, REQUEST);

            // totalBeforeVat = 24000 + 15000 = 39000
            // totalVatAmount = 24000*5/100 + 15000*5/100 = 1200 + 750 = 1950
            // totalAmount    = 39000 + 1950 + 10000 = 50950
            assertThat(response.totalBeforeVat()).isEqualByComparingTo("39000");
            assertThat(response.totalVatAmount()).isEqualByComparingTo("1950");
            assertThat(response.totalAmount()).isEqualByComparingTo("50950");
        }

        @Test
        @DisplayName("does not double-count VAT — totalAmount = before + vat + shipping only")
        void noDoubleVat() {
            stubCart(sampleCartItems());
            when(shippingPreparationService.prepareShipping(SUBJECT, REQUEST))
                    .thenReturn(sampleShipping(CUSTOMER_ID));
            when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setOrderId(1L); o.setCreatedAt(CREATED_AT); o.setUpdatedAt(CREATED_AT);
                return o;
            });
            when(inventoryRepository.findByProductIdWithLock(101L))
                    .thenReturn(Optional.of(Inventory.builder().inventoryId(1L).quantity(10).reservedQuantity(0).version(1L).build()));
            when(inventoryRepository.findByProductIdWithLock(102L))
                    .thenReturn(Optional.of(Inventory.builder().inventoryId(2L).quantity(10).reservedQuantity(0).version(1L).build()));

            final CreateOrderResponse response = service.createOrder(SUBJECT, REQUEST);

            // totalAmount must equal the sum of its parts — not more, not less
            final BigDecimal expected = response.totalBeforeVat()
                    .add(response.totalVatAmount())
                    .add(response.shippingFee());
            assertThat(response.totalAmount()).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("falls back to 10% VAT when category is null")
        void vatFallback_nullCategory() {
            final Product product = Product.builder()
                    .productId(201L).productName("No Category Product")
                    .price(BigDecimal.valueOf(10000))
                    .category(null) // no category
                    .build();
            final CartItem item = CartItem.builder()
                    .cart(Cart.builder().cartId(CART_ID).customerId(CUSTOMER_ID).build())
                    .product(product).unitPrice(BigDecimal.valueOf(10000)).quantity(1)
                    .build();
            stubCart(List.of(item));
            when(shippingPreparationService.prepareShipping(SUBJECT, REQUEST))
                    .thenReturn(sampleShipping(CUSTOMER_ID));
            when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setOrderId(1L); o.setCreatedAt(CREATED_AT); o.setUpdatedAt(CREATED_AT);
                return o;
            });
            when(inventoryRepository.findByProductIdWithLock(201L))
                    .thenReturn(Optional.of(Inventory.builder().inventoryId(1L).quantity(10).reservedQuantity(0).version(1L).build()));

            final CreateOrderResponse response = service.createOrder(SUBJECT, REQUEST);

            // subtotal=10000, vatAmount=10000*10/100=1000, total=10000+1000+10000=21000
            assertThat(response.totalVatAmount()).isEqualByComparingTo("1000");
            assertThat(response.totalAmount()).isEqualByComparingTo("21000");
        }
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    private void stubCart(final List<CartItem> cartItems) {
        when(authenticatedCustomerResolver.resolveActiveCustomer(SUBJECT))
                .thenReturn(activeCustomer());
        when(cartRepository.findByCustomerIdForUpdate(CUSTOMER_ID))
                .thenReturn(Optional.of(Cart.builder()
                        .cartId(CART_ID)
                        .customerId(CUSTOMER_ID)
                        .build()));
        when(cartItemRepository.findViewItemsByCustomerId(CUSTOMER_ID)).thenReturn(cartItems);
    }

    private static Customer activeCustomer() {
        return Customer.builder()
                .customerId(CUSTOMER_ID)
                .keycloakUserId(SUBJECT)
                .status(Customer.CustomerStatus.ACTIVE)
                .build();
    }

    /**
     * Helper: builds CartItems with 10% VAT on both products.
     * Product A: 12000 x 2, Product B: 5000 x 3.
     */
    private static List<CartItem> sampleCartItems() {
        return sampleCartItemsWithVatRate("10.00");
    }

    /**
     * Helper: builds CartItems with the given vatRate (same for both products).
     */
    private static List<CartItem> sampleCartItemsWithVatRate(final String vatRateStr) {
        final Category category = Category.builder()
                .categoryId(1L)
                .categoryName("Test Category")
                .vatRate(new BigDecimal(vatRateStr))
                .build();
        final Product firstProduct = Product.builder()
                .productId(101L).productName("Product A")
                .price(new BigDecimal("99999.00"))
                .category(category)
                .build();
        final Product secondProduct = Product.builder()
                .productId(102L).productName("Product B")
                .price(new BigDecimal("88888.00"))
                .category(category)
                .build();
        return List.of(
                CartItem.builder()
                        .cart(Cart.builder().cartId(CART_ID).customerId(CUSTOMER_ID).build())
                        .product(firstProduct)
                        .unitPrice(new BigDecimal("12000.00"))
                        .quantity(2)
                        .build(),
                CartItem.builder()
                        .cart(Cart.builder().cartId(CART_ID).customerId(CUSTOMER_ID).build())
                        .product(secondProduct)
                        .unitPrice(new BigDecimal("5000.00"))
                        .quantity(3)
                        .build());
    }

    private static ShippingCheckoutInfo sampleShipping(final Long customerId) {
        return new ShippingCheckoutInfo(
                customerId, 42L,
                "Nguyen Van A", "0987654321",
                "123 Le Loi", "Ben Nghe", "District 1", "Ha Noi",
                ShippingMethod.STANDARD, ShippingRegion.LOCAL,
                new BigDecimal("10000.00"));
    }
}
