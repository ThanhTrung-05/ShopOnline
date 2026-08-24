package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.shipping.ShippingFeeResult;
import com.example.banhangtructuyen.application.dto.shipping.ValidatedShippingSelection;
import com.example.banhangtructuyen.application.service.impl.ShippingFeeServiceImpl;
import com.example.banhangtructuyen.config.ShippingFeeProperties;
import com.example.banhangtructuyen.domain.model.ShippingMethod;
import com.example.banhangtructuyen.domain.model.ShippingRegion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ShippingFeeService")
class ShippingFeeServiceImplTest {

    private ShippingFeeProperties shippingFeeProperties;
    private ShippingFeeService service;

    @BeforeEach
    void setUp() {
        shippingFeeProperties = configuredProperties();
        service = new ShippingFeeServiceImpl(shippingFeeProperties);
    }

    @Test
    @DisplayName("Hà Nội STANDARD is LOCAL with fee 10000")
    void calculate_shouldReturnLocalStandardFee_forHaNoi() {
        final ShippingFeeResult result = service.calculate(selection("Hà Nội", ShippingMethod.STANDARD));

        assertThat(result.region()).isEqualTo(ShippingRegion.LOCAL);
        assertThat(result.shippingMethod()).isEqualTo(ShippingMethod.STANDARD);
        assertThat(result.shippingFee()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("normalized Hà Nội EXPRESS is LOCAL with fee 25000")
    void calculate_shouldNormalizeProvinceAndReturnLocalExpressFee_forHaNoi() {
        final ShippingFeeResult result = service.calculate(selection(
                "  Thành phố   hÀ nỘi  ", ShippingMethod.EXPRESS));

        assertThat(result.region()).isEqualTo(ShippingRegion.LOCAL);
        assertThat(result.shippingMethod()).isEqualTo(ShippingMethod.EXPRESS);
        assertThat(result.shippingFee()).isEqualByComparingTo("25000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bắc Ninh", "Hưng Yên", "Hải Dương"})
    @DisplayName("configured nearby provinces use the NEARBY STANDARD fee")
    void calculate_shouldReturnNearbyStandardFee_forNearbyProvince(final String province) {
        final ShippingFeeResult result = service.calculate(selection(province, ShippingMethod.STANDARD));

        assertThat(result.region()).isEqualTo(ShippingRegion.NEARBY);
        assertThat(result.shippingMethod()).isEqualTo(ShippingMethod.STANDARD);
        assertThat(result.shippingFee()).isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("Bắc Ninh EXPRESS is NEARBY with fee 40000")
    void calculate_shouldReturnNearbyExpressFee_forBacNinh() {
        final ShippingFeeResult result = service.calculate(selection("Bắc Ninh", ShippingMethod.EXPRESS));

        assertThat(result.region()).isEqualTo(ShippingRegion.NEARBY);
        assertThat(result.shippingMethod()).isEqualTo(ShippingMethod.EXPRESS);
        assertThat(result.shippingFee()).isEqualByComparingTo("40000");
    }

    @Test
    @DisplayName("another supported province uses the OTHER STANDARD fee")
    void calculate_shouldReturnOtherStandardFee_forAnotherSupportedProvince() {
        final ShippingFeeResult result = service.calculate(selection("TP. Hồ Chí Minh", ShippingMethod.STANDARD));

        assertThat(result.region()).isEqualTo(ShippingRegion.OTHER);
        assertThat(result.shippingMethod()).isEqualTo(ShippingMethod.STANDARD);
        assertThat(result.shippingFee()).isEqualByComparingTo("30000");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("null or blank province is rejected")
    void calculate_shouldRejectNullOrBlankProvince(final String province) {
        assertThatThrownBy(() -> service.calculate(selection(province, ShippingMethod.STANDARD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Province is required");
    }

    @Test
    @DisplayName("an unrecognized province is rejected")
    void calculate_shouldRejectUnsupportedProvince() {
        assertThatThrownBy(() -> service.calculate(selection("Atlantis", ShippingMethod.STANDARD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Province is not supported: Atlantis");
    }

    @Test
    @DisplayName("null shipping method is rejected")
    void calculate_shouldRejectNullShippingMethod() {
        assertThatThrownBy(() -> service.calculate(selection("Hà Nội", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Shipping method is required");
    }

    @Test
    @DisplayName("EXPRESS is rejected for OTHER region")
    void calculate_shouldRejectExpressForOtherRegion() {
        assertThatThrownBy(() -> service.calculate(selection("Đà Nẵng", ShippingMethod.EXPRESS)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EXPRESS shipping is not supported for region OTHER");
    }

    @Test
    @DisplayName("missing fee configuration fails instead of returning a default")
    void calculate_shouldFailWhenFeeConfigurationIsMissing() {
        shippingFeeProperties.getFees().get(ShippingRegion.LOCAL).remove(ShippingMethod.STANDARD);

        assertThatThrownBy(() -> service.calculate(selection("Hà Nội", ShippingMethod.STANDARD)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Shipping fee is not configured for region LOCAL and method STANDARD");
    }

    @Test
    @DisplayName("null validated selection is rejected")
    void calculate_shouldRejectNullSelection() {
        assertThatThrownBy(() -> service.calculate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Validated shipping selection is required");
    }

    private static ShippingFeeProperties configuredProperties() {
        final ShippingFeeProperties properties = new ShippingFeeProperties();
        final Map<ShippingRegion, Map<ShippingMethod, BigDecimal>> fees =
                new EnumMap<>(ShippingRegion.class);

        fees.put(ShippingRegion.LOCAL, fees(
                Map.entry(ShippingMethod.STANDARD, new BigDecimal("10000")),
                Map.entry(ShippingMethod.EXPRESS, new BigDecimal("25000"))));
        fees.put(ShippingRegion.NEARBY, fees(
                Map.entry(ShippingMethod.STANDARD, new BigDecimal("20000")),
                Map.entry(ShippingMethod.EXPRESS, new BigDecimal("40000"))));
        fees.put(ShippingRegion.OTHER, fees(
                Map.entry(ShippingMethod.STANDARD, new BigDecimal("30000"))));

        properties.setFees(fees);
        return properties;
    }

    @SafeVarargs
    private static Map<ShippingMethod, BigDecimal> fees(
            final Map.Entry<ShippingMethod, BigDecimal>... entries) {
        final Map<ShippingMethod, BigDecimal> fees = new EnumMap<>(ShippingMethod.class);
        for (Map.Entry<ShippingMethod, BigDecimal> entry : entries) {
            fees.put(entry.getKey(), entry.getValue());
        }
        return fees;
    }

    private static ValidatedShippingSelection selection(
            final String province,
            final ShippingMethod shippingMethod) {
        return new ValidatedShippingSelection(
                1L,
                42L,
                "Nguyễn Văn A",
                "0987654321",
                "123 Đường Lê Lợi",
                "Phường Bến Nghé",
                "Quận 1",
                province,
                shippingMethod
        );
    }
}
