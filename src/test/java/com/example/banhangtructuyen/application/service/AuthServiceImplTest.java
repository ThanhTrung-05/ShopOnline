package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.auth.RegisterRequest;
import com.example.banhangtructuyen.application.dto.auth.RegisterResponse;
import com.example.banhangtructuyen.application.service.impl.AuthServiceImpl;
import com.example.banhangtructuyen.domain.exception.EmailAlreadyExistsException;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthServiceImpl}. Repository and PasswordEncoder are mocked.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(customerRepository, passwordEncoder);
    }

    private static RegisterRequest validRequest() {
        return new RegisterRequest("customer@example.com", "SecurePass123", "Nguyễn Văn A", "0987654321");
    }

    @Nested
    @DisplayName("register — success")
    class RegisterSuccess {

        @Test
        @DisplayName("hashes password with BCrypt and never stores plaintext")
        void register_shouldHashPassword() {
            // Arrange
            when(customerRepository.existsByEmail("customer@example.com")).thenReturn(false);
            when(passwordEncoder.encode("SecurePass123")).thenReturn("$2a$12$hashedvalue");
            when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
                final Customer c = invocation.getArgument(0);
                c.setCustomerId(1L);
                c.setCreatedAt(Instant.now());
                return c;
            });

            // Act
            authService.register(validRequest());

            // Assert
            final ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository).save(captor.capture());
            final Customer saved = captor.getValue();

            assertThat(saved.getPasswordHash()).isEqualTo("$2a$12$hashedvalue");
            assertThat(saved.getPasswordHash()).isNotEqualTo("SecurePass123");
            verify(passwordEncoder).encode("SecurePass123");
        }

        @Test
        @DisplayName("defaults ROLE=USER and STATUS=ACTIVE")
        void register_shouldDefaultRoleAndStatus() {
            // Arrange
            when(customerRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashedvalue");
            when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
                final Customer c = invocation.getArgument(0);
                c.setCustomerId(1L);
                c.setCreatedAt(Instant.now());
                return c;
            });

            // Act
            final RegisterResponse response = authService.register(validRequest());

            // Assert
            assertThat(response.role()).isEqualTo("USER");
            assertThat(response.status()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("response does not expose password or password hash")
        void register_responseShouldNotContainPassword() {
            // Arrange
            when(customerRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashedvalue");
            when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
                final Customer c = invocation.getArgument(0);
                c.setCustomerId(1L);
                c.setCreatedAt(Instant.now());
                return c;
            });

            // Act
            final RegisterResponse response = authService.register(validRequest());

            // Assert — RegisterResponse has no password/passwordHash field at all (compile-time guarantee),
            // this test additionally verifies toString() never leaks it via reflection-free field check
            assertThat(response.toString()).doesNotContain("SecurePass123", "hashedvalue");
        }
    }

    @Nested
    @DisplayName("register — duplicate email")
    class RegisterDuplicateEmail {

        @Test
        @DisplayName("throws EmailAlreadyExistsException when email already registered")
        void register_shouldThrow_whenEmailExists() {
            // Arrange
            when(customerRepository.existsByEmail("customer@example.com")).thenReturn(true);

            // Act + Assert
            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(EmailAlreadyExistsException.class)
                    .hasMessageContaining("customer@example.com");

            verify(customerRepository, never()).save(any());
            verify(passwordEncoder, never()).encode(any());
        }
    }
}
