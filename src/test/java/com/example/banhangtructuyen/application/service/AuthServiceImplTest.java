package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.auth.RegisterRequest;
import com.example.banhangtructuyen.application.dto.auth.RegisterResponse;
import com.example.banhangtructuyen.application.service.impl.AuthServiceImpl;
import com.example.banhangtructuyen.domain.exception.EmailAlreadyExistsException;
import com.example.banhangtructuyen.domain.exception.KeycloakProvisioningException;
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
 * Unit tests for {@link AuthServiceImpl}. Repository, PasswordEncoder and
 * KeycloakAdminService are mocked.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private KeycloakAdminService keycloakAdminService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(customerRepository, passwordEncoder, keycloakAdminService);
    }

    private static RegisterRequest validRequest() {
        return new RegisterRequest("customer@example.com", "SecurePass123", "Nguyễn Văn A", "0987654321");
    }

    private void stubSuccessfulSave() {
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            final Customer c = invocation.getArgument(0);
            c.setCustomerId(1L);
            c.setCreatedAt(Instant.now());
            return c;
        });
    }

    @Nested
    @DisplayName("register — success")
    class RegisterSuccess {

        @Test
        @DisplayName("provisions Keycloak user, sets password, assigns CUSTOMER role, then saves Customer with keycloakUserId")
        void register_shouldProvisionKeycloakAndSaveCustomer() {
            // Arrange
            when(customerRepository.existsByEmail("customer@example.com")).thenReturn(false);
            when(keycloakAdminService.createUser("customer@example.com", "Nguyễn Văn A")).thenReturn("kc-user-1");
            when(passwordEncoder.encode("SecurePass123")).thenReturn("$2a$12$hashedvalue");
            stubSuccessfulSave();

            // Act
            authService.register(validRequest());

            // Assert — Keycloak provisioning steps happened in order
            final var inOrder = inOrder(keycloakAdminService, customerRepository);
            inOrder.verify(keycloakAdminService).createUser("customer@example.com", "Nguyễn Văn A");
            inOrder.verify(keycloakAdminService).setPassword("kc-user-1", "SecurePass123");
            inOrder.verify(keycloakAdminService).assignCustomerRole("kc-user-1");
            inOrder.verify(customerRepository).save(any(Customer.class));

            final ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository).save(captor.capture());
            final Customer saved = captor.getValue();

            assertThat(saved.getKeycloakUserId()).isEqualTo("kc-user-1");
            assertThat(saved.getPasswordHash()).isEqualTo("$2a$12$hashedvalue");
            assertThat(saved.getPasswordHash()).isNotEqualTo("SecurePass123");

            verify(keycloakAdminService, never()).deleteUser(any());
        }

        @Test
        @DisplayName("defaults ROLE=USER and STATUS=ACTIVE")
        void register_shouldDefaultRoleAndStatus() {
            // Arrange
            when(customerRepository.existsByEmail(anyString())).thenReturn(false);
            when(keycloakAdminService.createUser(anyString(), anyString())).thenReturn("kc-user-1");
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashedvalue");
            stubSuccessfulSave();

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
            when(keycloakAdminService.createUser(anyString(), anyString())).thenReturn("kc-user-1");
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashedvalue");
            stubSuccessfulSave();

            // Act
            final RegisterResponse response = authService.register(validRequest());

            // Assert
            assertThat(response.toString()).doesNotContain("SecurePass123", "hashedvalue");
        }
    }

    @Nested
    @DisplayName("register — duplicate email")
    class RegisterDuplicateEmail {

        @Test
        @DisplayName("throws EmailAlreadyExistsException when Oracle email already registered, never touches Keycloak")
        void register_shouldThrow_whenOracleEmailExists() {
            // Arrange
            when(customerRepository.existsByEmail("customer@example.com")).thenReturn(true);

            // Act + Assert
            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(EmailAlreadyExistsException.class)
                    .hasMessageContaining("customer@example.com");

            verify(customerRepository, never()).save(any());
            verify(keycloakAdminService, never()).createUser(any(), any());
        }

        @Test
        @DisplayName("maps Keycloak 409 conflict to EmailAlreadyExistsException and never saves Oracle customer")
        void register_shouldThrow_whenKeycloakUserExists() {
            // Arrange
            when(customerRepository.existsByEmail("customer@example.com")).thenReturn(false);
            when(keycloakAdminService.createUser("customer@example.com", "Nguyễn Văn A"))
                    .thenThrow(new EmailAlreadyExistsException("customer@example.com"));

            // Act + Assert
            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(EmailAlreadyExistsException.class)
                    .hasMessageContaining("customer@example.com");

            verify(customerRepository, never()).save(any());
            // no keycloakUserId was ever obtained, so no compensation delete should fire
            verify(keycloakAdminService, never()).deleteUser(any());
        }
    }

    @Nested
    @DisplayName("register — Keycloak provisioning failures")
    class RegisterKeycloakFailures {

        @Test
        @DisplayName("Keycloak create failure: Oracle save is never called")
        void register_shouldNotSaveCustomer_whenKeycloakCreateFails() {
            // Arrange
            when(customerRepository.existsByEmail(anyString())).thenReturn(false);
            when(keycloakAdminService.createUser(anyString(), anyString()))
                    .thenThrow(new KeycloakProvisioningException("Keycloak unreachable"));

            // Act + Assert
            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(KeycloakProvisioningException.class);

            verify(customerRepository, never()).save(any());
            verify(keycloakAdminService, never()).deleteUser(any());
        }

        @Test
        @DisplayName("password set failure: created Keycloak user is deleted (best-effort compensation)")
        void register_shouldDeleteKeycloakUser_whenSetPasswordFails() {
            // Arrange
            when(customerRepository.existsByEmail(anyString())).thenReturn(false);
            when(keycloakAdminService.createUser(anyString(), anyString())).thenReturn("kc-user-1");
            doThrow(new KeycloakProvisioningException("failed to set password"))
                    .when(keycloakAdminService).setPassword("kc-user-1", "SecurePass123");

            // Act + Assert
            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(KeycloakProvisioningException.class);

            verify(customerRepository, never()).save(any());
            verify(keycloakAdminService).deleteUser("kc-user-1");
        }

        @Test
        @DisplayName("role assignment failure: created Keycloak user is deleted (best-effort compensation)")
        void register_shouldDeleteKeycloakUser_whenAssignRoleFails() {
            // Arrange
            when(customerRepository.existsByEmail(anyString())).thenReturn(false);
            when(keycloakAdminService.createUser(anyString(), anyString())).thenReturn("kc-user-1");
            doThrow(new KeycloakProvisioningException("failed to assign role"))
                    .when(keycloakAdminService).assignCustomerRole("kc-user-1");

            // Act + Assert
            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(KeycloakProvisioningException.class);

            verify(customerRepository, never()).save(any());
            verify(keycloakAdminService).deleteUser("kc-user-1");
        }

        @Test
        @DisplayName("Oracle save failure: created Keycloak user is deleted (best-effort compensation)")
        void register_shouldDeleteKeycloakUser_whenOracleSaveFails() {
            // Arrange
            when(customerRepository.existsByEmail(anyString())).thenReturn(false);
            when(keycloakAdminService.createUser(anyString(), anyString())).thenReturn("kc-user-1");
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashedvalue");
            when(customerRepository.save(any(Customer.class)))
                    .thenThrow(new org.springframework.dao.DataIntegrityViolationException("constraint violation"));

            // Act + Assert
            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

            verify(keycloakAdminService).deleteUser("kc-user-1");
        }
    }
}
