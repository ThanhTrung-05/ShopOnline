package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.auth.RegisterRequest;
import com.example.banhangtructuyen.application.dto.auth.RegisterResponse;
import com.example.banhangtructuyen.application.service.impl.AuthServiceImpl;
import com.example.banhangtructuyen.domain.exception.EmailAlreadyExistsException;
import com.example.banhangtructuyen.domain.exception.KeycloakProvisioningException;
import com.example.banhangtructuyen.domain.model.Cart;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.repository.CartRepository;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthServiceImpl}. Repository, PasswordEncoder and
 * KeycloakAdminService are mocked.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private CartRepository cartRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private KeycloakAdminService keycloakAdminService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(customerRepository, cartRepository, passwordEncoder, keycloakAdminService);
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
    @DisplayName("register success")
    class RegisterSuccess {

        @Test
        @DisplayName("provisions Keycloak user, saves Customer, creates Cart, and flushes")
        void register_shouldProvisionKeycloakAndSaveCustomerAndCart() {
            when(customerRepository.existsByEmail("customer@example.com")).thenReturn(false);
            when(keycloakAdminService.createUser("customer@example.com", "Nguyễn Văn A")).thenReturn("kc-user-1");
            when(passwordEncoder.encode("SecurePass123")).thenReturn("$2a$12$hashedvalue");
            stubSuccessfulSave();

            authService.register(validRequest());

            final var inOrder = inOrder(keycloakAdminService, customerRepository, cartRepository);
            inOrder.verify(keycloakAdminService).createUser("customer@example.com", "Nguyễn Văn A");
            inOrder.verify(keycloakAdminService).setPassword("kc-user-1", "SecurePass123");
            inOrder.verify(keycloakAdminService).assignCustomerRole("kc-user-1");
            inOrder.verify(customerRepository).save(any(Customer.class));
            inOrder.verify(cartRepository).save(any(Cart.class));
            inOrder.verify(cartRepository).flush();

            final ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository).save(customerCaptor.capture());
            final Customer savedCustomer = customerCaptor.getValue();
            assertThat(savedCustomer.getKeycloakUserId()).isEqualTo("kc-user-1");
            assertThat(savedCustomer.getPasswordHash()).isEqualTo("$2a$12$hashedvalue");
            assertThat(savedCustomer.getPasswordHash()).isNotEqualTo("SecurePass123");

            final ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);
            verify(cartRepository, times(1)).save(cartCaptor.capture());
            assertThat(cartCaptor.getValue().getCustomerId()).isEqualTo(1L);
            verify(cartRepository, times(1)).flush();
            verify(keycloakAdminService, never()).deleteUser(any());
        }

        @Test
        @DisplayName("defaults ROLE=USER and STATUS=ACTIVE")
        void register_shouldDefaultRoleAndStatus() {
            when(customerRepository.existsByEmail(anyString())).thenReturn(false);
            when(keycloakAdminService.createUser(anyString(), anyString())).thenReturn("kc-user-1");
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashedvalue");
            stubSuccessfulSave();

            final RegisterResponse response = authService.register(validRequest());

            assertThat(response.role()).isEqualTo("USER");
            assertThat(response.status()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("response does not expose password or password hash")
        void register_responseShouldNotContainPassword() {
            when(customerRepository.existsByEmail(anyString())).thenReturn(false);
            when(keycloakAdminService.createUser(anyString(), anyString())).thenReturn("kc-user-1");
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashedvalue");
            stubSuccessfulSave();

            final RegisterResponse response = authService.register(validRequest());

            assertThat(response.toString()).doesNotContain("SecurePass123", "hashedvalue");
        }
    }

    @Nested
    @DisplayName("register duplicate email")
    class RegisterDuplicateEmail {

        @Test
        @DisplayName("Oracle duplicate email never touches Keycloak or Cart")
        void register_shouldThrow_whenOracleEmailExists() {
            when(customerRepository.existsByEmail("customer@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(EmailAlreadyExistsException.class)
                    .hasMessageContaining("customer@example.com");

            verify(customerRepository, never()).save(any());
            verify(cartRepository, never()).save(any());
            verify(keycloakAdminService, never()).createUser(any(), any());
        }

        @Test
        @DisplayName("Keycloak duplicate email never saves Oracle customer or Cart")
        void register_shouldThrow_whenKeycloakUserExists() {
            when(customerRepository.existsByEmail("customer@example.com")).thenReturn(false);
            when(keycloakAdminService.createUser("customer@example.com", "Nguyễn Văn A"))
                    .thenThrow(new EmailAlreadyExistsException("customer@example.com"));

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(EmailAlreadyExistsException.class)
                    .hasMessageContaining("customer@example.com");

            verify(customerRepository, never()).save(any());
            verify(cartRepository, never()).save(any());
            verify(keycloakAdminService, never()).deleteUser(any());
        }
    }

    @Nested
    @DisplayName("register Keycloak and DB failures")
    class RegisterFailures {

        @Test
        @DisplayName("Keycloak create failure never saves Oracle customer or Cart")
        void register_shouldNotSaveCustomer_whenKeycloakCreateFails() {
            when(customerRepository.existsByEmail(anyString())).thenReturn(false);
            when(keycloakAdminService.createUser(anyString(), anyString()))
                    .thenThrow(new KeycloakProvisioningException("Keycloak unreachable"));

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(KeycloakProvisioningException.class);

            verify(customerRepository, never()).save(any());
            verify(cartRepository, never()).save(any());
            verify(keycloakAdminService, never()).deleteUser(any());
        }

        @Test
        @DisplayName("password set failure deletes created Keycloak user")
        void register_shouldDeleteKeycloakUser_whenSetPasswordFails() {
            when(customerRepository.existsByEmail(anyString())).thenReturn(false);
            when(keycloakAdminService.createUser(anyString(), anyString())).thenReturn("kc-user-1");
            doThrow(new KeycloakProvisioningException("failed to set password"))
                    .when(keycloakAdminService).setPassword("kc-user-1", "SecurePass123");

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(KeycloakProvisioningException.class);

            verify(customerRepository, never()).save(any());
            verify(cartRepository, never()).save(any());
            verify(keycloakAdminService).deleteUser("kc-user-1");
        }

        @Test
        @DisplayName("role assignment failure deletes created Keycloak user")
        void register_shouldDeleteKeycloakUser_whenAssignRoleFails() {
            when(customerRepository.existsByEmail(anyString())).thenReturn(false);
            when(keycloakAdminService.createUser(anyString(), anyString())).thenReturn("kc-user-1");
            doThrow(new KeycloakProvisioningException("failed to assign role"))
                    .when(keycloakAdminService).assignCustomerRole("kc-user-1");

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(KeycloakProvisioningException.class);

            verify(customerRepository, never()).save(any());
            verify(cartRepository, never()).save(any());
            verify(keycloakAdminService).deleteUser("kc-user-1");
        }

        @Test
        @DisplayName("Customer save failure deletes created Keycloak user")
        void register_shouldDeleteKeycloakUser_whenCustomerSaveFails() {
            when(customerRepository.existsByEmail(anyString())).thenReturn(false);
            when(keycloakAdminService.createUser(anyString(), anyString())).thenReturn("kc-user-1");
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashedvalue");
            when(customerRepository.save(any(Customer.class)))
                    .thenThrow(new DataIntegrityViolationException("constraint violation"));

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(DataIntegrityViolationException.class);

            verify(cartRepository, never()).save(any());
            verify(keycloakAdminService).deleteUser("kc-user-1");
        }

        @Test
        @DisplayName("Cart save failure deletes created Keycloak user")
        void register_shouldDeleteKeycloakUser_whenCartSaveFails() {
            when(customerRepository.existsByEmail(anyString())).thenReturn(false);
            when(keycloakAdminService.createUser(anyString(), anyString())).thenReturn("kc-user-1");
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashedvalue");
            stubSuccessfulSave();
            when(cartRepository.save(any(Cart.class)))
                    .thenThrow(new DataIntegrityViolationException("cart constraint violation"));

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(DataIntegrityViolationException.class);

            verify(customerRepository).save(any(Customer.class));
            verify(cartRepository).save(any(Cart.class));
            verify(keycloakAdminService).deleteUser("kc-user-1");
        }

        @Test
        @DisplayName("Cart flush failure deletes created Keycloak user before returning")
        void register_shouldDeleteKeycloakUser_whenCartFlushFails() {
            when(customerRepository.existsByEmail(anyString())).thenReturn(false);
            when(keycloakAdminService.createUser(anyString(), anyString())).thenReturn("kc-user-1");
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashedvalue");
            stubSuccessfulSave();
            doThrow(new DataIntegrityViolationException("cart flush violation"))
                    .when(cartRepository).flush();

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(DataIntegrityViolationException.class);

            verify(customerRepository).save(any(Customer.class));
            verify(cartRepository).save(any(Cart.class));
            verify(cartRepository).flush();
            verify(keycloakAdminService).deleteUser("kc-user-1");
        }
    }
}
