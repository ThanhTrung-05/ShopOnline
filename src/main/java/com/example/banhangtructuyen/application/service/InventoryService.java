package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.inventory.InventoryAlertResponse;

import java.util.List;

/**
 * ATS-18: Service for inventory management and alerting.
 */
public interface InventoryService {

    /**
     * Returns a combined list of out-of-stock and low-stock products for Admin.
     * Out-of-stock products (availableQty <= 0) appear first,
     * followed by low-stock products (0 < availableQty <= threshold).
     */
    List<InventoryAlertResponse> getAlerts();
}
