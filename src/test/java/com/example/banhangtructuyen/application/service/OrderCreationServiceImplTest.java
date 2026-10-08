package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.order.CreateOrderResponse;
import com.example.banhangtructuyen.application.dto.shipping.ShippingCheckoutInfo;
import com.example.banhangtructuyen.application.dto.shipping.ShippingSelectionRequest;
import com.example.banhangtructuyen.application.service.impl.OrderCreationServiceImpl;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Cart;
import com.example.banhangtructuyen.domain.model.CartItem;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderItem;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import com.example.banhangtructuyen.domain.model.Product;
import com.example.banhangtructuyen.domain.model.ShippingMethod;
import com.example.banhangtructuyen.domain.model.ShippingRegion;
import com.example.banhangtructuyen.domain.repository.CartItemRepository;
import com.example.banhangtructuyen.domain.repository.CartRepository;
import com.example.banhangtructuyen.domain.repository.OrderItemRepository;
import com.example.banhangtructuyen.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCreationService")
class OrderCreationServiceImplTest {

    private static final String SUBJECT = "customer-subject";
    private static final Long CUSTOMER_ID = 7L;
    private static final Long CART_ID = 11L;
    private static final Instant CREATED_AT = Instant.parse("2026-09-07T02:00:00Z");
    private static final ShippingSelectionRequest REQUEST =
            new ShippingSelectionRequest(42L, ShippingMethod.STANDARD);

    @Mock private AuthenticatedCustomerResolver authenticatedCustomerResolver;
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ShippingPreparationService shippingPreparationService;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;

    private OrderCreationService service;

    @BeforeEach
    void setUp() {
        service = new OrderCreationServiceImpl(
                authenticatedCustomerResolver,
                cartRepository,
                cartItemRepository,
                shippingPreparationService,
                orderRepository,
                orderItemRepository);
    }

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

        final CreateOrderResponse response = service.createOrder(SUBJECT, REQUEST);

        final ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(orderCaptor.capture());
        final Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(savedOrder.getOrderNumber()).matches("ORD-\\d{8}-[0-9A-F]{8}");
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo("49000.00");
        assertThat(savedOrder.getShippingAddress()).isEqualTo(
                "Nguyen Van A, 0987654321, 123 Le Loi, Ben Nghe, District 1, Ha Noi");

        @SuppressWarnings({"rawtypes", "unchecked"})
        final ArgumentCaptor<Iterable<OrderItem>> itemsCaptor =
                ArgumentCaptor.forClass((Class) Iterable.class);
        verify(orderItemRepository).saveAll(itemsCaptor.capture());
        final List<OrderItem> savedItems = new ArrayList<>();
        itemsCaptor.getValue().forEach(savedItems::add);
        assertThat(savedItems).hasSize(2);
        assertThat(savedItems).allSatisfy(item -> assertThat(item.getOrder()).isSameAs(savedOrder));
        assertThat(savedItems)
                .extracting(
                        OrderItem::getProductId,
                        OrderItem::getProductName,
                        OrderItem::getUnitPrice,
                        OrderItem::getQuantity,
                        OrderItem::getSubtotal)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                101L, "Product A", new BigDecimal("12000.00"), 2,
                                new BigDecimal("24000.00")),
                        org.assertj.core.groups.Tuple.tuple(
                                102L, "Product B", new BigDecimal("5000.00"), 3,
                                new BigDecimal("15000.00")));

        assertThat(response.orderNumber()).isEqualTo(savedOrder.getOrderNumber());
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalAmount()).isEqualByComparingTo("49000.00");
        assertThat(response.shippingFee()).isEqualByComparingTo("10000.00");
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);

        verify(cartItemRepository).deleteAll(cartItems);
        verify(cartItemRepository).flush();
        final InOrder writeOrder = inOrder(
                orderRepository, orderItemRepository, cartItemRepository);
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
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAll(anyList()))
                .thenThrow(new DataIntegrityViolationException("order item insert failed"));

        assertThatThrownBy(() -> service.createOrder(SUBJECT, REQUEST))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(cartItemRepository, never()).deleteAll(cartItems);
        verify(cartItemRepository, never()).flush();
    }

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

    private static List<CartItem> sampleCartItems() {
        final Product firstProduct = Product.builder()
                .productId(101L)
                .productName("Product A")
                .price(new BigDecimal("99999.00"))
                .build();
        final Product secondProduct = Product.builder()
                .productId(102L)
                .productName("Product B")
                .price(new BigDecimal("88888.00"))
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
                customerId,
                42L,
                "Nguyen Van A",
                "0987654321",
                "123 Le Loi",
                "Ben Nghe",
                "District 1",
                "Ha Noi",
                ShippingMethod.STANDARD,
                ShippingRegion.LOCAL,
                new BigDecimal("10000.00"));
    }
}
