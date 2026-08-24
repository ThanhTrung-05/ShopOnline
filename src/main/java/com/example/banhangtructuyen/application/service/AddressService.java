package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.customer.AddressRequest;
import com.example.banhangtructuyen.application.dto.customer.AddressResponse;

import java.util.List;

public interface AddressService {

    List<AddressResponse> listAddresses(String keycloakSubject);

    AddressResponse getAddress(String keycloakSubject, Long addressId);

    AddressResponse createAddress(String keycloakSubject, AddressRequest request);

    AddressResponse updateAddress(String keycloakSubject, Long addressId, AddressRequest request);

    void deleteAddress(String keycloakSubject, Long addressId);

    AddressResponse setDefaultAddress(String keycloakSubject, Long addressId);
}
