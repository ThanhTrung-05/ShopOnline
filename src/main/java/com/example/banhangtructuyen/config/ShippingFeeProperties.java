package com.example.banhangtructuyen.config;

import com.example.banhangtructuyen.domain.model.ShippingMethod;
import com.example.banhangtructuyen.domain.model.ShippingRegion;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Configured shipping fees keyed by region and shipping method.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.shipping")
public class ShippingFeeProperties {

    private Map<ShippingRegion, Map<ShippingMethod, BigDecimal>> fees =
            new EnumMap<>(ShippingRegion.class);

    @PostConstruct
    public void validateConfiguration() {
        requireConfiguredFee(ShippingRegion.LOCAL, ShippingMethod.STANDARD);
        requireConfiguredFee(ShippingRegion.LOCAL, ShippingMethod.EXPRESS);
        requireConfiguredFee(ShippingRegion.NEARBY, ShippingMethod.STANDARD);
        requireConfiguredFee(ShippingRegion.NEARBY, ShippingMethod.EXPRESS);
        requireConfiguredFee(ShippingRegion.OTHER, ShippingMethod.STANDARD);

        final BigDecimal unsupportedFee = configuredFee(ShippingRegion.OTHER, ShippingMethod.EXPRESS);
        if (unsupportedFee != null) {
            throw new IllegalStateException("Shipping fee must not be configured for unsupported combination: "
                    + ShippingRegion.OTHER + " + " + ShippingMethod.EXPRESS);
        }
    }

    public BigDecimal requireConfiguredFee(
            final ShippingRegion region,
            final ShippingMethod shippingMethod) {
        final BigDecimal fee = configuredFee(region, shippingMethod);
        if (fee == null) {
            throw new IllegalStateException("Shipping fee is not configured for region "
                    + region + " and method " + shippingMethod);
        }
        if (fee.signum() < 0) {
            throw new IllegalStateException("Shipping fee must not be negative for region "
                    + region + " and method " + shippingMethod);
        }
        return fee;
    }

    private BigDecimal configuredFee(
            final ShippingRegion region,
            final ShippingMethod shippingMethod) {
        if (fees == null) {
            return null;
        }
        final Map<ShippingMethod, BigDecimal> regionFees = fees.get(region);
        return regionFees == null ? null : regionFees.get(shippingMethod);
    }
}
