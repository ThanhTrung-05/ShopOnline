package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.customer.CustomerResponse;
import com.example.banhangtructuyen.application.dto.customer.UpdateProfileRequest;

public interface CustomerProfileService {

    CustomerResponse getProfile(String keycloakSubject);

    CustomerResponse updateProfile(String keycloakSubject, UpdateProfileRequest request);
}
