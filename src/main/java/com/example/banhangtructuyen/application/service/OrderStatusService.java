package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.order.OrderStatusResponse;

public interface OrderStatusService {

    OrderStatusResponse getOrderStatus(String orderNumber, Long customerId);
}
