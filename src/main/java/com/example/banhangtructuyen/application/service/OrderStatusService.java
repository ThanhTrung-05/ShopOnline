package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.order.OrderDetailResponse;
import com.example.banhangtructuyen.application.dto.order.OrderStatusResponse;

import java.util.List;

public interface OrderStatusService {

    List<OrderStatusResponse> getOrderStatuses(Long customerId);

    OrderStatusResponse getOrderStatus(String orderNumber, Long customerId);

    OrderDetailResponse getOrderDetails(String orderNumber, Long customerId);
}
