package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.order.CreateOrderResponse;
import com.example.banhangtructuyen.application.dto.shipping.ShippingCheckoutInfo;
import com.example.banhangtructuyen.application.dto.shipping.ShippingSelectionRequest;
import com.example.banhangtructuyen.application.service.AuthenticatedCustomerResolver;
import com.example.banhangtructuyen.application.service.OrderCreationService;
import com.example.banhangtructuyen.application.service.ShippingPreparationService;
import com.example.banhangtructuyen.domain.model.CartItem;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderItem;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import com.example.banhangtructuyen.domain.repository.CartItemRepository;
import com.example.banhangtructuyen.domain.repository.CartRepository;
import com.example.banhangtructuyen.domain.repository.OrderItemRepository;
import com.example.banhangtructuyen.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderCreationServiceImpl implements OrderCreationService {

    private static final int MAX_SHIPPING_ADDRESS_LENGTH = 500;
    private static final String EMPTY_CART_MESSAGE = "Cart must contain at least one item";

    private final AuthenticatedCustomerResolver authenticatedCustomerResolver;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ShippingPreparationService shippingPreparationService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Override
    public CreateOrderResponse createOrder(
            final String keycloakSubject,
            final ShippingSelectionRequest shippingSelection) {
        final Customer customer = authenticatedCustomerResolver.resolveActiveCustomer(keycloakSubject);
        cartRepository.findByCustomerIdForUpdate(customer.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException(EMPTY_CART_MESSAGE));
        final List<CartItem> cartItems = cartItemRepository
                .findViewItemsByCustomerId(customer.getCustomerId());
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException(EMPTY_CART_MESSAGE);
        }

        final ShippingCheckoutInfo shipping = shippingPreparationService
                .prepareShipping(keycloakSubject, shippingSelection);
        if (!Objects.equals(customer.getCustomerId(), shipping.customerId())) {
            throw new IllegalStateException("Validated shipping customer does not match authenticated customer");
        }

        final BigDecimal merchandiseTotal = cartItems.stream()
                .map(OrderCreationServiceImpl::calculateSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal totalAmount = merchandiseTotal.add(shipping.shippingFee());

        final Order order = orderRepository.saveAndFlush(Order.builder()
                .customerId(customer.getCustomerId())
                .orderNumber(generateOrderNumber())
                .status(OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .shippingAddress(createShippingAddressSnapshot(shipping))
                .build());

        final List<OrderItem> orderItems = cartItems.stream()
                .map(cartItem -> createOrderItemSnapshot(order, cartItem))
                .toList();
        orderItemRepository.saveAll(orderItems);

        cartItemRepository.deleteAll(cartItems);
        cartItemRepository.flush();

        return new CreateOrderResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                shipping.shippingFee(),
                order.getCreatedAt());
    }

    private static OrderItem createOrderItemSnapshot(final Order order, final CartItem cartItem) {
        return OrderItem.builder()
                .order(order)
                .productId(cartItem.getProduct().getProductId())
                .productName(cartItem.getProduct().getProductName())
                .unitPrice(cartItem.getUnitPrice())
                .quantity(cartItem.getQuantity())
                .subtotal(calculateSubtotal(cartItem))
                .build();
    }

    private static BigDecimal calculateSubtotal(final CartItem cartItem) {
        return cartItem.getUnitPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
    }

    private static String createShippingAddressSnapshot(final ShippingCheckoutInfo shipping) {
        final String snapshot = Stream.of(
                        shipping.recipientName(),
                        shipping.phone(),
                        shipping.line1(),
                        shipping.ward(),
                        shipping.district(),
                        shipping.province())
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow(() -> new IllegalArgumentException("Shipping address is required"));

        if (snapshot.length() > MAX_SHIPPING_ADDRESS_LENGTH) {
            throw new IllegalArgumentException("Shipping address snapshot exceeds 500 characters");
        }
        return snapshot;
    }

    private static String generateOrderNumber() {
        final String date = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        final String randomPart = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
        return "ORD-" + date + "-" + randomPart;
    }
}
