package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.shipping.ShippingCheckoutInfo;
import com.example.banhangtructuyen.application.dto.shipping.ShippingSelectionRequest;

/**
 * Connects ATS-32 shipping selection validation with ATS-33 shipping fee calculation.
 */
public interface ShippingPreparationService {

    ShippingCheckoutInfo prepareShipping(
            String keycloakSubject,
            ShippingSelectionRequest request);
}
