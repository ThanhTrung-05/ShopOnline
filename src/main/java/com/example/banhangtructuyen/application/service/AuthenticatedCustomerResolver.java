package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.domain.exception.CustomerAccountNotActiveException;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthenticatedCustomerResolver {

    private final CustomerRepository customerRepository;

    public Customer resolveActiveCustomer(final String keycloakSubject) {
        if (keycloakSubject == null || keycloakSubject.isBlank()) {
            throw new ResourceNotFoundException("Customer", keycloakSubject);
        }

        final Customer customer = customerRepository.findByKeycloakUserId(keycloakSubject)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", keycloakSubject));

        if (customer.getStatus() != Customer.CustomerStatus.ACTIVE) {
            throw new CustomerAccountNotActiveException(customer.getStatus());
        }

        return customer;
    }
}
