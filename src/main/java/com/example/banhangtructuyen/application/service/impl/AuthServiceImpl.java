package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.auth.RegisterRequest;
import com.example.banhangtructuyen.application.dto.auth.RegisterResponse;
import com.example.banhangtructuyen.application.service.AuthService;
import com.example.banhangtructuyen.application.service.KeycloakAdminService;
import com.example.banhangtructuyen.domain.exception.EmailAlreadyExistsException;
import com.example.banhangtructuyen.domain.exception.KeycloakProvisioningException;
import com.example.banhangtructuyen.domain.exception.RegistrationProvisioningException;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final CustomerRepository customerRepository;
    private final CustomerRegistrationPersistenceService registrationPersistenceService;
    private final KeycloakAdminService keycloakAdminService;

    @Override
    public RegisterResponse register(final RegisterRequest request) {
        String keycloakUserId = null;
        try {
            if (customerRepository.existsByEmail(request.email())) {
                throw new EmailAlreadyExistsException(request.email());
            }

            keycloakUserId = keycloakAdminService.createUser(request.email(), request.fullName());
            keycloakAdminService.setPassword(keycloakUserId, request.password());
            keycloakAdminService.assignCustomerRole(keycloakUserId);

            return registrationPersistenceService.createCustomerWithLifetimeCart(
                    request.email(), request.fullName(), request.phone(), keycloakUserId);
        } catch (final RuntimeException primaryFailure) {
            if (keycloakUserId != null) {
                compensateKeycloakUser(keycloakUserId, request.email(), primaryFailure);
            } else if (!(primaryFailure instanceof EmailAlreadyExistsException)) {
                log.error("Registration failed before a Keycloak user id was obtained. "
                                + "email={}, failure={}",
                        request.email(), primaryFailure.toString(), primaryFailure);
            }
            throw classifyFailure(primaryFailure);
        }
    }

    private void compensateKeycloakUser(
            final String keycloakUserId,
            final String email,
            final RuntimeException primaryFailure) {
        try {
            keycloakAdminService.deleteUser(keycloakUserId);
            log.warn("Registration failed after Keycloak user creation; compensation deleted user. "
                    + "email={}, keycloakUserId={}", email, keycloakUserId, primaryFailure);
        } catch (final RuntimeException cleanupFailure) {
            primaryFailure.addSuppressed(cleanupFailure);
            log.error("Registration and Keycloak compensation both failed; manual cleanup is required. "
                            + "email={}, keycloakUserId={}, primaryFailure={}, cleanupFailure={}",
                    email,
                    keycloakUserId,
                    primaryFailure.toString(),
                    cleanupFailure.toString(),
                    primaryFailure);
        }
    }

    private RuntimeException classifyFailure(final RuntimeException failure) {
        if (failure instanceof EmailAlreadyExistsException
                || failure instanceof KeycloakProvisioningException
                || failure instanceof DataIntegrityViolationException
                || failure instanceof RegistrationProvisioningException) {
            return failure;
        }
        return new RegistrationProvisioningException(
                "Registration failed during internal customer provisioning", failure);
    }
}
