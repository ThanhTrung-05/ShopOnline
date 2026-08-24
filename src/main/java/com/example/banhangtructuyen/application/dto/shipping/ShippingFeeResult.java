package com.example.banhangtructuyen.application.dto.shipping;

import com.example.banhangtructuyen.domain.model.ShippingMethod;
import com.example.banhangtructuyen.domain.model.ShippingRegion;

import java.math.BigDecimal;

/**
 * Transient shipping fee calculated from a server-validated shipping selection.
 */
public record ShippingFeeResult(
        ShippingRegion region,
        ShippingMethod shippingMethod,
        BigDecimal shippingFee
) {}
