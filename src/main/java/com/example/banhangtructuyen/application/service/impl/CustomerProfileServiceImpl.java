package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.customer.CustomerResponse;
import com.example.banhangtructuyen.application.dto.customer.UpdateProfileRequest;
import com.example.banhangtructuyen.application.service.CustomerProfileService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerProfileServiceImpl implements CustomerProfileService {

    private static final String PHONE_PATTERN = "^(0|\\+84)\\d{9,10}$";

    private final CustomerRepository customerRepository;

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getProfile(final String keycloakSubject) {
        return toResponse(findBySubject(keycloakSubject));
    }

    @Override
    public CustomerResponse updateProfile(final String keycloakSubject, final UpdateProfileRequest request) {
        final Customer customer = findBySubject(keycloakSubject);

        if (request.fullName() != null) {
            if (request.fullName().isBlank()) {
                throw new ConstraintViolationException("Full name must not be blank", null);
            }
            customer.setFullName(request.fullName());
        }

        if (request.phone() != null) {
            if (!request.phone().matches(PHONE_PATTERN)) {
                throw new ConstraintViolationException("Phone must be a valid Vietnamese phone number", null);
            }
            customer.setPhone(request.phone());
        }

        return toResponse(customerRepository.save(customer));
    }

    private Customer findBySubject(final String keycloakSubject) {
        return customerRepository.findByKeycloakUserId(keycloakSubject)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", keycloakSubject));
    }

    private CustomerResponse toResponse(final Customer customer) {
        return new CustomerResponse(
                customer.getCustomerId(),
                customer.getEmail(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getRole().name(),
                customer.getStatus().name(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
