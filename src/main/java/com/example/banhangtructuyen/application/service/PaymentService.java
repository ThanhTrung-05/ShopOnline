package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.order.PaymentCallbackRequest;

public interface PaymentService {
    void processCallback(PaymentCallbackRequest request);
}
