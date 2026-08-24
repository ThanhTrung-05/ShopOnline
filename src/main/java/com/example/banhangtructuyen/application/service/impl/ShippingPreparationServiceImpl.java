package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.shipping.ShippingCheckoutInfo;
import com.example.banhangtructuyen.application.dto.shipping.ShippingFeeResult;
import com.example.banhangtructuyen.application.dto.shipping.ShippingSelectionRequest;
import com.example.banhangtructuyen.application.dto.shipping.ValidatedShippingSelection;
import com.example.banhangtructuyen.application.service.ShippingFeeService;
import com.example.banhangtructuyen.application.service.ShippingPreparationService;
import com.example.banhangtructuyen.application.service.ShippingSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShippingPreparationServiceImpl implements ShippingPreparationService {

    private final ShippingSelectionService shippingSelectionService;
    private final ShippingFeeService shippingFeeService;

    @Override
    public ShippingCheckoutInfo prepareShipping(
            final String keycloakSubject,
            final ShippingSelectionRequest request) {
        final ValidatedShippingSelection selection =
                shippingSelectionService.validateSelection(keycloakSubject, request);
        final ShippingFeeResult feeResult = shippingFeeService.calculate(selection);

        return new ShippingCheckoutInfo(
                selection.customerId(),
                selection.addressId(),
                selection.recipientName(),
                selection.phone(),
                selection.line1(),
                selection.ward(),
                selection.district(),
                selection.province(),
                feeResult.shippingMethod(),
                feeResult.region(),
                feeResult.shippingFee()
        );
    }
}
