package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.customer.AddressRequest;
import com.example.banhangtructuyen.application.dto.customer.AddressResponse;
import com.example.banhangtructuyen.application.service.AddressService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Address;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.repository.AddressRepository;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final CustomerRepository customerRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses(final String keycloakSubject) {
        final Long customerId = resolveCustomerId(keycloakSubject);
        return addressRepository.findByCustomerIdOrderByAddressIdAsc(customerId).stream()
                .map(AddressServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse getAddress(final String keycloakSubject, final Long addressId) {
        final Long customerId = resolveCustomerId(keycloakSubject);
        return toResponse(findOwnedAddress(addressId, customerId));
    }

    @Override
    public AddressResponse createAddress(final String keycloakSubject, final AddressRequest request) {
        final Long customerId = resolveCustomerId(keycloakSubject);
        final boolean isFirstAddress = addressRepository.countByCustomerId(customerId) == 0;

        final Address address = Address.builder()
                .customerId(customerId)
                .recipientName(request.recipientName())
                .phone(request.phone())
                .line1(request.line1())
                .ward(request.ward())
                .district(request.district())
                .province(request.province())
                .defaultAddress(isFirstAddress)
                .build();

        return toResponse(addressRepository.save(address));
    }

    @Override
    public AddressResponse updateAddress(final String keycloakSubject, final Long addressId, final AddressRequest request) {
        final Long customerId = resolveCustomerId(keycloakSubject);
        final Address address = findOwnedAddress(addressId, customerId);

        address.setRecipientName(request.recipientName());
        address.setPhone(request.phone());
        address.setLine1(request.line1());
        address.setWard(request.ward());
        address.setDistrict(request.district());
        address.setProvince(request.province());

        return toResponse(addressRepository.save(address));
    }

    @Override
    public void deleteAddress(final String keycloakSubject, final Long addressId) {
        final Long customerId = resolveCustomerId(keycloakSubject);
        final Address address = findOwnedAddress(addressId, customerId);
        final boolean wasDefault = address.isDefaultAddress();

        addressRepository.delete(address);

        if (wasDefault) {
            addressRepository.findMinAddressIdByCustomerId(customerId)
                    .flatMap(minId -> addressRepository.findByAddressIdAndCustomerId(minId, customerId))
                    .ifPresent(next -> {
                        next.setDefaultAddress(true);
                        addressRepository.save(next);
                    });
        }
    }

    @Override
    public AddressResponse setDefaultAddress(final String keycloakSubject, final Long addressId) {
        final Long customerId = resolveCustomerId(keycloakSubject);
        final Address address = findOwnedAddress(addressId, customerId);

        if (!address.isDefaultAddress()) {
            addressRepository.clearDefaultForCustomer(customerId);
            address.setDefaultAddress(true);
            addressRepository.save(address);
        }

        return toResponse(address);
    }

    private Long resolveCustomerId(final String keycloakSubject) {
        return customerRepository.findByKeycloakUserId(keycloakSubject)
                .map(Customer::getCustomerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", keycloakSubject));
    }

    private Address findOwnedAddress(final Long addressId, final Long customerId) {
        return addressRepository.findByAddressIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
    }

    private static AddressResponse toResponse(final Address address) {
        return new AddressResponse(
                address.getAddressId(),
                address.getRecipientName(),
                address.getPhone(),
                address.getLine1(),
                address.getWard(),
                address.getDistrict(),
                address.getProvince(),
                address.isDefaultAddress()
        );
    }
}
