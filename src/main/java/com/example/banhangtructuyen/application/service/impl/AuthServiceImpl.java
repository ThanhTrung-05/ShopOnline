package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.auth.RegisterRequest;
import com.example.banhangtructuyen.application.dto.auth.RegisterResponse;
import com.example.banhangtructuyen.application.service.AuthService;
import com.example.banhangtructuyen.application.service.KeycloakAdminService;
import com.example.banhangtructuyen.domain.exception.EmailAlreadyExistsException;
import com.example.banhangtructuyen.domain.model.Cart;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.repository.CartRepository;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final CustomerRepository customerRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final KeycloakAdminService keycloakAdminService;

    @Override
    public RegisterResponse register(final RegisterRequest request) {
        if (customerRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        String keycloakUserId = null;
        try {
            keycloakUserId = keycloakAdminService.createUser(request.email(), request.fullName());
            keycloakAdminService.setPassword(keycloakUserId, request.password());
            keycloakAdminService.assignCustomerRole(keycloakUserId);

            final Customer customer = Customer.builder()
                    .email(request.email())
                    .fullName(request.fullName())
                    .phone(request.phone())
                    .passwordHash(passwordEncoder.encode(request.password()))
                    .keycloakUserId(keycloakUserId)
                    .status(Customer.CustomerStatus.ACTIVE)
                    .role(Customer.CustomerRole.USER)
                    .build();

            final Customer saved = customerRepository.save(customer);
            cartRepository.save(Cart.builder()
                    .customerId(saved.getCustomerId())
                    .build());
            cartRepository.flush();

            return new RegisterResponse(
                    saved.getCustomerId(),
                    saved.getEmail(),
                    saved.getFullName(),
                    saved.getPhone(),
                    saved.getRole().name(),
                    saved.getStatus().name(),
                    saved.getCreatedAt()
            );
        } catch (final RuntimeException ex) {
            if (keycloakUserId != null) {
                keycloakAdminService.deleteUser(keycloakUserId);
            }
            throw ex;
        }
    }
}
