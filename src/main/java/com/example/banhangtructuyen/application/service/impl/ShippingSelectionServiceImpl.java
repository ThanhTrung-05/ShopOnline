package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.shipping.ShippingSelectionRequest;
import com.example.banhangtructuyen.application.dto.shipping.ValidatedShippingSelection;
import com.example.banhangtructuyen.application.service.AuthenticatedCustomerResolver;
import com.example.banhangtructuyen.application.service.ShippingSelectionService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Address;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShippingSelectionServiceImpl implements ShippingSelectionService {

    private final AddressRepository addressRepository;
    private final AuthenticatedCustomerResolver authenticatedCustomerResolver;

    @Override
    public ValidatedShippingSelection validateSelection(
            final String keycloakSubject,
            final ShippingSelectionRequest request) {
        validateRequest(request);

        final Customer customer = authenticatedCustomerResolver.resolveActiveCustomer(keycloakSubject);
        final Address address = addressRepository
                .findByAddressIdAndCustomerId(request.addressId(), customer.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Address", request.addressId()));

        return new ValidatedShippingSelection(
                customer.getCustomerId(),
                address.getAddressId(),
                address.getRecipientName(),
                address.getPhone(),
                address.getLine1(),
                address.getWard(),
                address.getDistrict(),
                address.getProvince(),
                request.shippingMethod()
        );
    }

    private static void validateRequest(final ShippingSelectionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Shipping selection is required");
        }
        if (request.addressId() == null) {
            throw new IllegalArgumentException("Address id is required");
        }
        if (request.addressId() <= 0) {
            throw new IllegalArgumentException("Address id must be greater than 0");
        }
        if (request.shippingMethod() == null) {
            throw new IllegalArgumentException("Shipping method is required");
        }
    }
}
