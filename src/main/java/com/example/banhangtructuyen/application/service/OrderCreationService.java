package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.order.CreateOrderResponse;
import com.example.banhangtructuyen.application.dto.shipping.ShippingSelectionRequest;

public interface OrderCreationService {

    CreateOrderResponse createOrder(
            String keycloakSubject,
            ShippingSelectionRequest shippingSelection);
}
