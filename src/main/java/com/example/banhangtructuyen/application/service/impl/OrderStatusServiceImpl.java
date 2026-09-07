package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.order.OrderDetailResponse;
import com.example.banhangtructuyen.application.dto.order.OrderItemResponse;
import com.example.banhangtructuyen.application.dto.order.OrderStatusResponse;
import com.example.banhangtructuyen.application.service.OrderStatusService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderItem;
import com.example.banhangtructuyen.domain.repository.OrderItemRepository;
import com.example.banhangtructuyen.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderStatusServiceImpl implements OrderStatusService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Override
    public List<OrderStatusResponse> getOrderStatuses(final Long customerId) {
        return orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(OrderStatusServiceImpl::toResponse)
                .toList();
    }

    @Override
    public OrderStatusResponse getOrderStatus(final String orderNumber, final Long customerId) {
        final Order order = orderRepository.findByOrderNumberAndCustomerId(orderNumber, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));

        return toResponse(order);
    }

    @Override
    public OrderDetailResponse getOrderDetails(final String orderNumber, final Long customerId) {
        final Order order = orderRepository.findByOrderNumberAndCustomerId(orderNumber, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));
        final List<OrderItemResponse> items = orderItemRepository
                .findAllByOrder_OrderIdOrderByOrderItemIdAsc(order.getOrderId())
                .stream()
                .map(OrderStatusServiceImpl::toItemResponse)
                .toList();

        return new OrderDetailResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getTotalAmount(),
                items
        );
    }

    private static OrderStatusResponse toResponse(final Order order) {
        return new OrderStatusResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private static OrderItemResponse toItemResponse(final OrderItem item) {
        return new OrderItemResponse(
                item.getProductId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getSubtotal()
        );
    }
}
