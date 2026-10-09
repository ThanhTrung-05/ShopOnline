package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.order.CreateOrderResponse;
import com.example.banhangtructuyen.application.dto.shipping.ShippingCheckoutInfo;
import com.example.banhangtructuyen.application.dto.shipping.ShippingSelectionRequest;
import com.example.banhangtructuyen.application.service.AuthenticatedCustomerResolver;
import com.example.banhangtructuyen.application.service.OrderCreationService;
import com.example.banhangtructuyen.application.service.ShippingPreparationService;
import com.example.banhangtructuyen.domain.model.CartItem;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Inventory;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderItem;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import com.example.banhangtructuyen.domain.repository.CartItemRepository;
import com.example.banhangtructuyen.domain.repository.CartRepository;
import com.example.banhangtructuyen.domain.repository.InventoryRepository;
import com.example.banhangtructuyen.domain.repository.OrderItemRepository;
import com.example.banhangtructuyen.domain.repository.OrderRepository;
import com.example.banhangtructuyen.application.service.ProductService;
import com.example.banhangtructuyen.application.service.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * ATS-15 + ATS-16 + ATS-26: Creates an order from the active cart.
 *
 * <p>ATS-26 changes: VAT is calculated per line item at order creation time
 * using the product's category VAT rate. The rate and amount are persisted in
 * ORDER_ITEMS so invoices remain historically accurate even if VAT rates change.
 *
 * <p>VAT calculation (price stored BEFORE VAT):
 *   subtotal     = unitPrice * quantity
 *   vatAmount    = subtotal * vatRate / 100  (rounded HALF_UP, 0 decimal places)
 *   totalWithVat = subtotal + vatAmount
 *
 * <p>totalBeforeVat = sum(subtotal across all items)
 * <p>totalVatAmount = sum(vatAmount across all items)
 * <p>totalAmount    = totalBeforeVat + totalVatAmount + shippingFee
 */
@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.SERIALIZABLE)
public class OrderCreationServiceImpl implements OrderCreationService {

    private static final int MAX_SHIPPING_ADDRESS_LENGTH = 500;
    private static final String EMPTY_CART_MESSAGE = "Cart must contain at least one item";

    private final AuthenticatedCustomerResolver authenticatedCustomerResolver;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ShippingPreparationService shippingPreparationService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductService productService;
    private final OutboxEventPublisher outboxEventPublisher;

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

        // --- Reserve inventory (ATS-15/ATS-17) and compute per-line VAT (ATS-26) ---
        BigDecimal totalBeforeVat = BigDecimal.ZERO;
        BigDecimal totalVatAmount = BigDecimal.ZERO;

        for (final CartItem cartItem : cartItems) {
            final Long productId = cartItem.getProduct().getProductId();
            final int qty = cartItem.getQuantity();

            // ATS-15/ATS-16: pessimistic lock + reserve
            final Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                    .orElseThrow(() -> new IllegalStateException("Inventory not found for product " + productId));
            inventory.reserve(qty);
            productService.clearProductCaches(productId);

            // ATS-26: accumulate VAT totals
            final BigDecimal subtotal = cartItem.getUnitPrice()
                    .multiply(BigDecimal.valueOf(qty));
            final BigDecimal vatAmount = computeVatAmount(cartItem, subtotal);
            totalBeforeVat = totalBeforeVat.add(subtotal);
            totalVatAmount = totalVatAmount.add(vatAmount);
        }

        final BigDecimal totalAmount = totalBeforeVat
                .add(totalVatAmount)
                .add(shipping.shippingFee());

        // Save Order with VAT breakdown
        final Order order = orderRepository.saveAndFlush(Order.builder()
                .customerId(customer.getCustomerId())
                .orderNumber(generateOrderNumber())
                .status(OrderStatus.PENDING)
                .totalBeforeVat(totalBeforeVat)
                .totalVatAmount(totalVatAmount)
                .totalAmount(totalAmount)
                .shippingAddress(createShippingAddressSnapshot(shipping))
                .build());

        // Save OrderItems with per-line VAT snapshot (ATS-26)
        final List<OrderItem> orderItems = cartItems.stream()
                .map(cartItem -> createOrderItemSnapshot(order, cartItem))
                .toList();
        orderItemRepository.saveAll(orderItems);

        cartItemRepository.deleteAll(cartItems);
        cartItemRepository.flush();

        outboxEventPublisher.publishEvent("ORDER", order.getOrderNumber(), "banhang.order.created",
                Map.of("orderId", order.getOrderId(), "orderNumber", order.getOrderNumber()));

        return new CreateOrderResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalBeforeVat(),
                order.getTotalVatAmount(),
                shipping.shippingFee(),
                order.getTotalAmount(),
                order.getCreatedAt());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * ATS-26: Create OrderItem snapshot with per-line VAT captured from Category at order time.
     */
    private static OrderItem createOrderItemSnapshot(final Order order, final CartItem cartItem) {
        final BigDecimal subtotal = cartItem.getUnitPrice()
                .multiply(BigDecimal.valueOf(cartItem.getQuantity()));
        final BigDecimal vatRate = resolveVatRate(cartItem);
        final BigDecimal vatAmount = computeVatAmount(cartItem, subtotal);

        return OrderItem.builder()
                .order(order)
                .productId(cartItem.getProduct().getProductId())
                .productName(cartItem.getProduct().getProductName())
                .unitPrice(cartItem.getUnitPrice())
                .quantity(cartItem.getQuantity())
                .subtotal(subtotal)
                .vatRate(vatRate)
                .vatAmount(vatAmount)
                .build();
    }

    /**
     * ATS-26: vatAmount = subtotal * vatRate / 100, rounded HALF_UP, 0 decimal places.
     * Consistent with the rounding rule in ProductServiceImpl.toDetailResponse().
     */
    private static BigDecimal computeVatAmount(final CartItem cartItem, final BigDecimal subtotal) {
        final BigDecimal vatRate = resolveVatRate(cartItem);
        return subtotal
                .multiply(vatRate)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }

    /**
     * ATS-26: Resolve VAT rate from the product's category.
     * Falls back to 10% if category or vatRate is missing (same fallback as ProductServiceImpl).
     */
    private static BigDecimal resolveVatRate(final CartItem cartItem) {
        final var category = cartItem.getProduct().getCategory();
        if (category != null && category.getVatRate() != null) {
            return category.getVatRate();
        }
        return BigDecimal.TEN; // fallback 10%
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
