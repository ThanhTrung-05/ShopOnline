package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.shipping.ShippingSelectionRequest;
import com.example.banhangtructuyen.application.dto.shipping.ValidatedShippingSelection;

/**
 * Resolves a transient shipping selection for a future Checkout/Order caller.
 */
public interface ShippingSelectionService {

    ValidatedShippingSelection validateSelection(
            String keycloakSubject,
            ShippingSelectionRequest request);
}
