package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.inventory.InventoryAlertResponse;
import com.example.banhangtructuyen.application.dto.inventory.InventoryAlertResponse.AlertLevel;
import com.example.banhangtructuyen.application.service.impl.InventoryServiceImpl;
import com.example.banhangtructuyen.config.AppProperties;
import com.example.banhangtructuyen.domain.model.Category;
import com.example.banhangtructuyen.domain.model.Inventory;
import com.example.banhangtructuyen.domain.model.Product;
import com.example.banhangtructuyen.domain.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * ATS-18: Unit tests for InventoryServiceImpl.
 * Verifies alert classification (OUT_OF_STOCK / LOW_STOCK) and ordering.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ATS-18: InventoryService — inventory alert tests")
class InventoryServiceImplTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private AppProperties appProperties;
    @Mock private AppProperties.Inventory inventoryConfig;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private static final int THRESHOLD = 10;

    @BeforeEach
    void setUp() {
        given(appProperties.getInventory()).willReturn(inventoryConfig);
        given(inventoryConfig.getLowStockThreshold()).willReturn(THRESHOLD);
    }

    private static Inventory buildInventory(final Long id, final String name, final int qty, final int reserved) {
        final Category category = Category.builder()
                .categoryId(1L)
                .categoryName("Đồ uống")
                .build();
        final Product product = Product.builder()
                .productId(id)
                .productName(name)
                .productSlug("slug-" + id)
                .price(BigDecimal.valueOf(18000))
                .category(category)
                .build();
        return Inventory.builder()
                .inventoryId(id)
                .product(product)
                .quantity(qty)
                .reservedQuantity(reserved)
                .version(1L)
                .build();
    }

    @Nested
    @DisplayName("getAlerts()")
    class GetAlerts {

        @Test
        @DisplayName("should return OUT_OF_STOCK when availableQty = 0 (qty=0)")
        void outOfStock_zeroQuantity() {
            final Inventory inv = buildInventory(1L, "Product A", 0, 0);
            given(inventoryRepository.findOutOfStock()).willReturn(List.of(inv));
            given(inventoryRepository.findLowStock(THRESHOLD)).willReturn(List.of());

            final List<InventoryAlertResponse> alerts = inventoryService.getAlerts();

            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).alertLevel()).isEqualTo(AlertLevel.OUT_OF_STOCK);
            assertThat(alerts.get(0).availableQuantity()).isZero();
        }

        @Test
        @DisplayName("should return OUT_OF_STOCK when all stock is reserved (qty=5, reserved=5)")
        void outOfStock_allReserved() {
            final Inventory inv = buildInventory(2L, "Product B", 5, 5);
            given(inventoryRepository.findOutOfStock()).willReturn(List.of(inv));
            given(inventoryRepository.findLowStock(THRESHOLD)).willReturn(List.of());

            final List<InventoryAlertResponse> alerts = inventoryService.getAlerts();

            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).alertLevel()).isEqualTo(AlertLevel.OUT_OF_STOCK);
        }

        @Test
        @DisplayName("should return LOW_STOCK when 0 < availableQty <= threshold")
        void lowStock_withinThreshold() {
            final Inventory inv = buildInventory(3L, "Product C", 5, 0); // availableQty=5 <= 10
            given(inventoryRepository.findOutOfStock()).willReturn(List.of());
            given(inventoryRepository.findLowStock(THRESHOLD)).willReturn(List.of(inv));

            final List<InventoryAlertResponse> alerts = inventoryService.getAlerts();

            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).alertLevel()).isEqualTo(AlertLevel.LOW_STOCK);
            assertThat(alerts.get(0).availableQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("should return no alert when availableQty > threshold (normal stock)")
        void normalStock_noAlert() {
            given(inventoryRepository.findOutOfStock()).willReturn(List.of());
            given(inventoryRepository.findLowStock(THRESHOLD)).willReturn(List.of());

            final List<InventoryAlertResponse> alerts = inventoryService.getAlerts();

            assertThat(alerts).isEmpty();
        }

        @Test
        @DisplayName("should return OUT_OF_STOCK first, then LOW_STOCK in combined results")
        void combinedAlerts_outOfStockBeforeLowStock() {
            final Inventory oos = buildInventory(1L, "Out of Stock", 0, 0);
            final Inventory low = buildInventory(2L, "Low Stock", 3, 0);
            given(inventoryRepository.findOutOfStock()).willReturn(List.of(oos));
            given(inventoryRepository.findLowStock(THRESHOLD)).willReturn(List.of(low));

            final List<InventoryAlertResponse> alerts = inventoryService.getAlerts();

            assertThat(alerts).hasSize(2);
            assertThat(alerts.get(0).alertLevel()).isEqualTo(AlertLevel.OUT_OF_STOCK);
            assertThat(alerts.get(1).alertLevel()).isEqualTo(AlertLevel.LOW_STOCK);
        }

        @Test
        @DisplayName("should map product fields correctly in the response")
        void responseMapping_isCorrect() {
            final Inventory inv = buildInventory(4L, "Bia Tiger lon 330ml", 3, 1); // available=2
            given(inventoryRepository.findOutOfStock()).willReturn(List.of());
            given(inventoryRepository.findLowStock(THRESHOLD)).willReturn(List.of(inv));

            final InventoryAlertResponse alert = inventoryService.getAlerts().get(0);

            assertThat(alert.productId()).isEqualTo(4L);
            assertThat(alert.productName()).isEqualTo("Bia Tiger lon 330ml");
            assertThat(alert.categoryName()).isEqualTo("Đồ uống");
            assertThat(alert.quantity()).isEqualTo(3);
            assertThat(alert.reservedQuantity()).isEqualTo(1);
            assertThat(alert.availableQuantity()).isEqualTo(2);
            assertThat(alert.alertLevel()).isEqualTo(AlertLevel.LOW_STOCK);
        }

        @Test
        @DisplayName("should use threshold from appProperties")
        void threshold_fromConfiguration() {
            given(inventoryConfig.getLowStockThreshold()).willReturn(5);
            given(inventoryRepository.findOutOfStock()).willReturn(List.of());
            given(inventoryRepository.findLowStock(5)).willReturn(List.of());

            inventoryService.getAlerts();

            // Verify the repository was called with the configured threshold
            org.mockito.Mockito.verify(inventoryRepository).findLowStock(5);
        }
    }
}
