package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.order.OperationsOrderResponse;
import com.example.banhangtructuyen.application.service.NotificationService;
import com.example.banhangtructuyen.application.service.OrderOperationsService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import com.example.banhangtructuyen.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderOperationsServiceImpl implements OrderOperationsService {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.SHIPPING, OrderStatus.CANCELLED),
            OrderStatus.SHIPPING, Set.of(OrderStatus.DELIVERED)
    );

    private final OrderRepository orderRepository;
    private final NotificationService notificationService;

    @Override
    public List<OperationsOrderResponse> getOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(OrderOperationsServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public OperationsOrderResponse updateStatus(
            final String orderNumber,
            final OrderStatus targetStatus) {
        if (targetStatus == null) {
            throw new IllegalArgumentException("Order status is required");
        }

        final Order order = orderRepository.findByOrderNumberForUpdate(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));
        final OrderStatus currentStatus = order.getStatus();
        if (!ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(targetStatus)) {
            throw new IllegalArgumentException(
                    "Order status transition from " + currentStatus + " to " + targetStatus + " is not allowed");
        }

        order.setStatus(targetStatus);
        final Order savedOrder = orderRepository.saveAndFlush(order);
        notificationService.createOrderStatusChangedNotification(savedOrder, currentStatus, targetStatus);
        return toResponse(savedOrder);
    }

    private static OperationsOrderResponse toResponse(final Order order) {
        return new OperationsOrderResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getUpdatedAt()
        );
    }
}
