package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.ApiResponse;
import com.example.banhangtructuyen.application.dto.inventory.InventoryAlertResponse;
import com.example.banhangtructuyen.application.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ATS-18: Admin inventory alerting endpoints.
 *
 * <p>All endpoints under /api/v1/admin/** require ROLE_ADMIN
 * as configured in SecurityConfig (line 120: ".requestMatchers("/api/v1/admin/**").hasRole("ADMIN")").
 * No additional security annotation needed.
 */
@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
@Tag(name = "Admin — Inventory", description = "ATS-18: Admin inventory alerting — low stock and out-of-stock")
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(
        summary = "[Admin] Get inventory alerts",
        description = "Returns all ACTIVE products that are out-of-stock (availableQty <= 0) or low-stock "
                    + "(0 < availableQty <= configured threshold). "
                    + "Out-of-stock products appear first, then low-stock in ascending quantity order. "
                    + "Threshold is configured via app.inventory.low-stock-threshold (default: 10). "
                    + "Requires ROLE_ADMIN. (ATS-18)"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Alert list returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — ROLE_ADMIN required")
    })
    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<List<InventoryAlertResponse>>> getAlerts() {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getAlerts()));
    }
}
