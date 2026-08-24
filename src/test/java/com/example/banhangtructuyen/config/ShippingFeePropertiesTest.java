package com.example.banhangtructuyen.config;

import com.example.banhangtructuyen.domain.model.ShippingMethod;
import com.example.banhangtructuyen.domain.model.ShippingRegion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ShippingFeeProperties")
class ShippingFeePropertiesTest {

    @Test
    @DisplayName("startup validation fails when a supported combination has no configured fee")
    void validateConfiguration_shouldFailWhenSupportedFeeIsMissing() {
        final ShippingFeeProperties properties = new ShippingFeeProperties();
        properties.setFees(Map.of(
                ShippingRegion.LOCAL, Map.of(
                        ShippingMethod.EXPRESS, new BigDecimal("25000")),
                ShippingRegion.NEARBY, Map.of(
                        ShippingMethod.STANDARD, new BigDecimal("20000"),
                        ShippingMethod.EXPRESS, new BigDecimal("40000")),
                ShippingRegion.OTHER, Map.of(
                        ShippingMethod.STANDARD, new BigDecimal("30000"))));

        assertThatThrownBy(properties::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Shipping fee is not configured for region LOCAL and method STANDARD");
    }

    @Test
    @DisplayName("startup validation rejects a fee for unsupported OTHER EXPRESS")
    void validateConfiguration_shouldRejectConfiguredFeeForUnsupportedCombination() {
        final ShippingFeeProperties properties = new ShippingFeeProperties();
        properties.setFees(Map.of(
                ShippingRegion.LOCAL, Map.of(
                        ShippingMethod.STANDARD, new BigDecimal("10000"),
                        ShippingMethod.EXPRESS, new BigDecimal("25000")),
                ShippingRegion.NEARBY, Map.of(
                        ShippingMethod.STANDARD, new BigDecimal("20000"),
                        ShippingMethod.EXPRESS, new BigDecimal("40000")),
                ShippingRegion.OTHER, Map.of(
                        ShippingMethod.STANDARD, new BigDecimal("30000"),
                        ShippingMethod.EXPRESS, new BigDecimal("50000"))));

        assertThatThrownBy(properties::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Shipping fee must not be configured for unsupported combination: OTHER + EXPRESS");
    }
}
