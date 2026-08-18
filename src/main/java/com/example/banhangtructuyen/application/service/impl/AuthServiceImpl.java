package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.auth.LoginRequest;
import com.example.banhangtructuyen.application.dto.auth.LoginResponse;
import com.example.banhangtructuyen.application.dto.auth.RegisterRequest;
import com.example.banhangtructuyen.application.dto.auth.RegisterResponse;
import com.example.banhangtructuyen.application.service.AuthService;
import com.example.banhangtructuyen.domain.exception.EmailAlreadyExistsException;
import com.example.banhangtructuyen.domain.exception.InvalidCredentialsException;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import com.example.banhangtructuyen.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    public RegisterResponse register(final RegisterRequest request) {
        if (customerRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        final Customer customer = Customer.builder()
                .email(request.email())
                .fullName(request.fullName())
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(Customer.CustomerStatus.ACTIVE)
                .role(Customer.CustomerRole.USER)
                .build();

        final Customer saved = customerRepository.save(customer);

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
