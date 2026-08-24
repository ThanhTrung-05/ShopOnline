package com.example.banhangtructuyen.application.dto.shipping;

import com.example.banhangtructuyen.domain.model.ShippingMethod;
import com.example.banhangtructuyen.domain.model.ShippingRegion;

import java.math.BigDecimal;

/**
 * Transient shipping information prepared for a future Checkout/Order caller.
 * Address values come from an ATS-32 validated selection and are sufficient for a later
 * Order address snapshot. This object is not persisted and is not itself an Order snapshot.
 */
public record ShippingCheckoutInfo(
        Long customerId,
        Long addressId,
        String recipientName,
        String phone,
        String line1,
        String ward,
        String district,
        String province,
        ShippingMethod shippingMethod,
        ShippingRegion region,
        BigDecimal shippingFee
) {}
