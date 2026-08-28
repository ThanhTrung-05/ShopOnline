package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.order.OrderStatusResponse;
import com.example.banhangtructuyen.application.service.OrderStatusService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderStatusServiceImpl implements OrderStatusService {

    private final OrderRepository orderRepository;

    @Override
    public OrderStatusResponse getOrderStatus(final String orderNumber, final Long customerId) {
        final Order order = orderRepository.findByOrderNumberAndCustomerId(orderNumber, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));

        return new OrderStatusResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
