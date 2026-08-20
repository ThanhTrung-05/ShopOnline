package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.auth.RegisterResponse;
import com.example.banhangtructuyen.domain.model.Cart;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.repository.CartRepository;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the Oracle-owned portion of registration in one local transaction.
 *
 * <p>This bean is deliberately separate from the Keycloak orchestration so the caller sees
 * failures raised while the transaction commits and can compensate the already-created
 * Keycloak user.
 */
@Service
@RequiredArgsConstructor
public class CustomerRegistrationPersistenceService {

    private final CustomerRepository customerRepository;
    private final CartRepository cartRepository;

    @Transactional
    public RegisterResponse createCustomerWithLifetimeCart(
            final String email,
            final String fullName,
            final String phone,
            final String keycloakUserId) {
        final Customer customer = Customer.builder()
                .email(email)
                .fullName(fullName)
                .phone(phone)
                .keycloakUserId(keycloakUserId)
                .status(Customer.CustomerStatus.ACTIVE)
                .role(Customer.CustomerRole.USER)
                .build();

        final Customer saved = customerRepository.saveAndFlush(customer);
        cartRepository.saveAndFlush(Cart.builder()
                .customerId(saved.getCustomerId())
                .build());

        return new RegisterResponse(
                saved.getCustomerId(),
                saved.getEmail(),
                saved.getFullName(),
                saved.getPhone(),
                saved.getRole().name(),
                saved.getStatus().name(),
                saved.getCreatedAt()
        );
    }
}
