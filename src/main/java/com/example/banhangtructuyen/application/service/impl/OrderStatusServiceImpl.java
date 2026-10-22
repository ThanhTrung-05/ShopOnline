package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.order.OrderDetailResponse;
import com.example.banhangtructuyen.application.dto.order.OrderItemResponse;
import com.example.banhangtructuyen.application.dto.order.OrderStatusResponse;
import com.example.banhangtructuyen.application.service.OrderStatusService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderItem;
import com.example.banhangtructuyen.domain.model.Payment;
import com.example.banhangtructuyen.domain.repository.OrderItemRepository;
import com.example.banhangtructuyen.domain.repository.OrderRepository;
import com.example.banhangtructuyen.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderStatusServiceImpl implements OrderStatusService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;

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

    /**
     * ATS-39/ATS-41: Returns full order detail including VAT breakdown, shipping address
     * and current payment status. Used by both the order status page and the
     * success confirmation page (ATS-41).
     */
    @Override
    public OrderDetailResponse getOrderDetails(final String orderNumber, final Long customerId) {
        final Order order = orderRepository.findByOrderNumberAndCustomerId(orderNumber, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));
        final List<OrderItemResponse> items = orderItemRepository
                .findAllByOrder_OrderIdOrderByOrderItemIdAsc(order.getOrderId())
                .stream()
                .map(OrderStatusServiceImpl::toItemResponse)
                .toList();

        // ATS-41: Look up current payment status so the success page can verify
        // that payment truly succeeded (not just rely on URL/state from the frontend).
        final Payment.PaymentStatus paymentStatus = paymentRepository
                .findByOrder_OrderId(order.getOrderId())
                .map(Payment::getStatus)
                .orElse(null);

        // ATS-39: Derive shippingFee = totalAmount - totalBeforeVat - totalVatAmount
        // The fee is not stored as a separate column but can be reconstructed exactly.
        final BigDecimal shippingFee = order.getTotalAmount()
                .subtract(order.getTotalBeforeVat())
                .subtract(order.getTotalVatAmount());

        return new OrderDetailResponse(
                order.getOrderId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getTotalBeforeVat(),
                order.getTotalVatAmount(),
                shippingFee,
                order.getTotalAmount(),
                order.getShippingAddress(),
                paymentStatus,
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
        return OrderItemResponse.from(item);
    }
}
