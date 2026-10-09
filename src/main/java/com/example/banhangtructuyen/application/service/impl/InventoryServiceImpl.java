package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.inventory.InventoryAlertResponse;
import com.example.banhangtructuyen.application.dto.inventory.InventoryAlertResponse.AlertLevel;
import com.example.banhangtructuyen.application.service.InventoryService;
import com.example.banhangtructuyen.config.AppProperties;
import com.example.banhangtructuyen.domain.model.Inventory;
import com.example.banhangtructuyen.domain.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * ATS-18: Implementation of inventory alerting for Admin.
 *
 * <p>Source of Truth: INVENTORY table, using getAvailableQuantity()
 * which computes (QUANTITY - RESERVED_QUANTITY).
 * Out-of-stock = availableQty <= 0.
 * Low-stock    = 0 < availableQty <= app.inventory.low-stock-threshold.
 *
 * <p>Only ACTIVE products are included.
 * Read-only transaction — no side effects.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final AppProperties appProperties;

    @Override
    public List<InventoryAlertResponse> getAlerts() {
        final int threshold = appProperties.getInventory().getLowStockThreshold();

        final List<InventoryAlertResponse> alerts = new ArrayList<>();

        // OUT_OF_STOCK: availableQty <= 0
        inventoryRepository.findOutOfStock().stream()
                .map(inv -> toResponse(inv, AlertLevel.OUT_OF_STOCK))
                .forEach(alerts::add);

        // LOW_STOCK: 0 < availableQty <= threshold
        inventoryRepository.findLowStock(threshold).stream()
                .map(inv -> toResponse(inv, AlertLevel.LOW_STOCK))
                .forEach(alerts::add);

        return alerts;
    }

    private static InventoryAlertResponse toResponse(final Inventory inv, final AlertLevel level) {
        final var product = inv.getProduct();
        return new InventoryAlertResponse(
                product.getProductId(),
                product.getProductName(),
                product.getProductSlug(),
                product.getCategory().getCategoryName(),
                product.getPrice(),
                inv.getQuantity(),
                inv.getReservedQuantity(),
                inv.getAvailableQuantity(),
                level
        );
    }
}
