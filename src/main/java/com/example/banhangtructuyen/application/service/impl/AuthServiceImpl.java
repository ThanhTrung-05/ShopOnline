package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.auth.LoginRequest;
import com.example.banhangtructuyen.application.dto.auth.LoginResponse;
import com.example.banhangtructuyen.application.dto.auth.RegisterRequest;
import com.example.banhangtructuyen.application.dto.auth.RegisterResponse;
import com.example.banhangtructuyen.application.service.AuthService;
import com.example.banhangtructuyen.application.service.KeycloakAdminService;
import com.example.banhangtructuyen.domain.exception.EmailAlreadyExistsException;
<<<<<<< HEAD
import com.example.banhangtructuyen.domain.exception.InvalidCredentialsException;
import com.example.banhangtructuyen.domain.model.Customer;
=======
import com.example.banhangtructuyen.domain.exception.KeycloakProvisioningException;
import com.example.banhangtructuyen.domain.exception.RegistrationProvisioningException;
>>>>>>> 2e744a0ec2370b770aff69565021da8d47088517
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import com.example.banhangtructuyen.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final CustomerRepository customerRepository;
<<<<<<< HEAD
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
=======
    private final CustomerRegistrationPersistenceService registrationPersistenceService;
    private final KeycloakAdminService keycloakAdminService;
>>>>>>> 2e744a0ec2370b770aff69565021da8d47088517

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

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(final LoginRequest request) {
        final Customer customer = customerRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), customer.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (customer.getStatus() != Customer.CustomerStatus.ACTIVE) {
            throw new InvalidCredentialsException("Account is not active");
        }

        final String token = jwtService.generateToken(customer.getEmail(), customer.getRole().name());
        return new LoginResponse(token, customer.getEmail(), customer.getFullName(),
                customer.getRole().name());
    }
}
