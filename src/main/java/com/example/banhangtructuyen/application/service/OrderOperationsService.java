package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.order.OperationsOrderResponse;
import com.example.banhangtructuyen.domain.model.OrderStatus;

import java.util.List;

public interface OrderOperationsService {

    List<OperationsOrderResponse> getOrders();

    OperationsOrderResponse updateStatus(String orderNumber, OrderStatus status);
}
