package com.example.banhangtructuyen.application.dto.shipping;

import com.example.banhangtructuyen.domain.model.ShippingMethod;

/**
 * Immutable, transient shipping data produced only after customer status and address ownership
 * have been validated. All address values are copied from the database, never from client input.
 *
 * <p>This is an application contract for future Checkout/Order and ATS-33 integration. It is not
 * persisted and is not itself an Order address snapshot.
 */
public record ValidatedShippingSelection(
        Long customerId,
        Long addressId,
        String recipientName,
        String phone,
        String line1,
        String ward,
        String district,
        String province,
        ShippingMethod shippingMethod
) {}
