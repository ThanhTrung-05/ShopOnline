package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.order.OrderItemResponse;
import com.example.banhangtructuyen.application.dto.order.OrderResponse;
import com.example.banhangtructuyen.application.dto.order.PlaceOrderRequest;
import com.example.banhangtructuyen.application.service.AuthenticatedCustomerResolver;
import com.example.banhangtructuyen.application.service.OrderService;
import com.example.banhangtructuyen.domain.exception.InsufficientStockException;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.CartItem;
import com.example.banhangtructuyen.domain.model.Inventory;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderItem;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.repository.CartItemRepository;
import com.example.banhangtructuyen.domain.repository.InventoryRepository;
import com.example.banhangtructuyen.domain.repository.OrderItemRepository;
import com.example.banhangtructuyen.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core of ATS-14: Places orders atomically using PESSIMISTIC_WRITE locks.
 *
 * <p>Order placement flow:
 * <ol>
 *   <li>Resolve customer from JWT subject.</li>
 *   <li>Load all cart items (requires non-empty cart).</li>
 *   <li>For each product: acquire SELECT FOR UPDATE lock on INVENTORY row,
 *       verify available stock ≥ requested, then call {@code Inventory.deduct()}.</li>
 *   <li>Save Order + OrderItems, clear cart — all in a single transaction.</li>
 * </ol>
 *
 * <p>If any product has insufficient stock, {@link InsufficientStockException} is thrown,
 * the transaction rolls back, and all inventory locks are released automatically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AuthenticatedCustomerResolver authenticatedCustomerResolver;
    private final CartItemRepository cartItemRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Override
    @Transactional
    public OrderResponse placeOrder(final String keycloakSubject, final PlaceOrderRequest request) {
        // 1. Resolve authenticated customer
        final Customer customer = authenticatedCustomerResolver.resolveActiveCustomer(keycloakSubject);
        final Long customerId = customer.getCustomerId();

        // 2. Load cart items — must have at least one
        final List<CartItem> cartItems = cartItemRepository.findViewItemsByCustomerId(customerId);
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Giỏ hàng đang trống. Vui lòng thêm sản phẩm trước khi đặt hàng.");
        }

        // 3. Lock-and-deduct inventory for every cart item (Pessimistic Lock — ATS-14 core)
        BigDecimal totalAmount = BigDecimal.ZERO;
        final List<OrderItem> orderItems = new ArrayList<>();

        for (final CartItem cartItem : cartItems) {
            final Long productId   = cartItem.getProduct().getProductId();
            final int requestedQty = cartItem.getQuantity();

            // SELECT … FOR UPDATE — blocks any concurrent request for the same row
            final Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory for product", productId));

            final int available = inventory.getAvailableQuantity();
            if (available < requestedQty) {
                // Transaction will rollback → all acquired locks released automatically
                log.warn("Insufficient stock for productId={}: requested={}, available={}",
                        productId, requestedQty, available);
                throw new InsufficientStockException(
                        cartItem.getProduct().getProductName(), requestedQty, available);
            }

            // Deduct quantity under lock — safe because we hold the exclusive row lock
            inventory.deduct(requestedQty);
            inventoryRepository.save(inventory);

            final BigDecimal lineSubtotal = cartItem.getUnitPrice()
                    .multiply(BigDecimal.valueOf(requestedQty));
            totalAmount = totalAmount.add(lineSubtotal);

            orderItems.add(OrderItem.builder()
                    .productId(productId)
                    .productName(cartItem.getProduct().getProductName())
                    .quantity(requestedQty)
                    .unitPrice(cartItem.getUnitPrice())
                    .subtotal(lineSubtotal)
                    .build());
        }

        // 4. Persist Order
        final Order order = Order.builder()
                .customerId(customerId)
                .orderNumber(generateOrderNumber())
                .status(Order.OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .shippingAddress(request.shippingAddress())
                .note(request.note())
                .build();
        final Order savedOrder = orderRepository.save(order);

        // 5. Persist OrderItems linked to the saved order
        for (final OrderItem item : orderItems) {
            item.setOrder(savedOrder);
        }
        final List<OrderItem> savedItems = orderItemRepository.saveAll(orderItems);

        // 6. Clear cart (all items from this session)
        cartItemRepository.deleteAll(cartItems);

        log.info("Order placed: orderNumber={}, customerId={}, total={}",
                savedOrder.getOrderNumber(), customerId, totalAmount);

        return toResponse(savedOrder, savedItems);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(final String keycloakSubject) {
        final Customer customer = authenticatedCustomerResolver.resolveActiveCustomer(keycloakSubject);
        final List<Order> orders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(
                customer.getCustomerId());

        return orders.stream().map(order -> {
            final List<OrderItem> items = orderItemRepository.findByOrderId(order.getOrderId());
            return toResponse(order, items);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(final String keycloakSubject, final Long orderId) {
        final Customer customer = authenticatedCustomerResolver.resolveActiveCustomer(keycloakSubject);
        final Order order = orderRepository
                .findByOrderIdAndCustomerId(orderId, customer.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        final List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        return toResponse(order, items);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /** Generates "ORD-YYYYMMDD-{UUID8}" order number. */
    private static String generateOrderNumber() {
        final String date  = LocalDate.now(VN_ZONE).format(DATE_FMT);
        final String uuid8 = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase();
        return "ORD-" + date + "-" + uuid8;
    }

    private static OrderResponse toResponse(final Order order, final List<OrderItem> items) {
        final List<OrderItemResponse> itemResponses = items.stream()
                .map(i -> new OrderItemResponse(
                        i.getOrderItemId(),
                        i.getProductId(),
                        i.getProductName(),
                        i.getQuantity(),
                        i.getUnitPrice(),
                        i.getSubtotal()))
                .toList();

        return new OrderResponse(
                order.getOrderId(),
                order.getOrderNumber(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getShippingAddress(),
                order.getNote(),
                order.getCreatedAt(),
                itemResponses);
    }
}
