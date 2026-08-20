package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.auth.RegisterRequest;
import com.example.banhangtructuyen.application.dto.auth.RegisterResponse;
import com.example.banhangtructuyen.application.service.impl.AuthServiceImpl;
import com.example.banhangtructuyen.application.service.impl.CustomerRegistrationPersistenceService;
import com.example.banhangtructuyen.domain.exception.EmailAlreadyExistsException;
import com.example.banhangtructuyen.domain.exception.KeycloakProvisioningException;
import com.example.banhangtructuyen.domain.exception.RegistrationProvisioningException;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AuthServiceImplTest {

    private static final String EMAIL = "customer@example.com";
    private static final String FULL_NAME = "Nguyen Van A";
    private static final String PASSWORD = "SecurePass123";

    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerRegistrationPersistenceService registrationPersistenceService;
    @Mock private KeycloakAdminService keycloakAdminService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                customerRepository, registrationPersistenceService, keycloakAdminService);
    }

    private static RegisterRequest validRequest() {
        return new RegisterRequest(EMAIL, PASSWORD, FULL_NAME, "0987654321");
    }

    private static RegisterResponse successfulResponse() {
        return new RegisterResponse(
                1L, EMAIL, FULL_NAME, "0987654321", "USER", "ACTIVE",
                Instant.parse("2026-08-20T00:00:00Z"));
    }

    private void stubKeycloakCreate(final String keycloakUserId) {
        when(customerRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(keycloakAdminService.createUser(EMAIL, FULL_NAME)).thenReturn(keycloakUserId);
    }

    @Nested
    @DisplayName("successful registration")
    class SuccessfulRegistration {

        @Test
        @DisplayName("provisions Keycloak before atomically creating Customer and lifetime Cart")
        void register_shouldProvisionKeycloakThenPersistOracleState() {
            stubKeycloakCreate("kc-user-1");
            when(registrationPersistenceService.createCustomerWithLifetimeCart(
                    EMAIL, FULL_NAME, "0987654321", "kc-user-1"))
                    .thenReturn(successfulResponse());

            final RegisterResponse response = authService.register(validRequest());

            assertThat(response).isEqualTo(successfulResponse());
            final InOrder order = inOrder(keycloakAdminService, registrationPersistenceService);
            order.verify(keycloakAdminService).createUser(EMAIL, FULL_NAME);
            order.verify(keycloakAdminService).setPassword("kc-user-1", PASSWORD);
            order.verify(keycloakAdminService).assignCustomerRole("kc-user-1");
            order.verify(registrationPersistenceService).createCustomerWithLifetimeCart(
                    EMAIL, FULL_NAME, "0987654321", "kc-user-1");
            verify(keycloakAdminService, never()).deleteUser(anyString());
        }

        @Test
        @DisplayName("response never exposes the Keycloak password")
        void register_responseShouldNotExposePassword() {
            stubKeycloakCreate("kc-user-1");
            when(registrationPersistenceService.createCustomerWithLifetimeCart(
                    EMAIL, FULL_NAME, "0987654321", "kc-user-1"))
                    .thenReturn(successfulResponse());

            final RegisterResponse response = authService.register(validRequest());

            assertThat(response.toString()).doesNotContain(PASSWORD);
        }
    }

    @Nested
    @DisplayName("duplicate and retry behavior")
    class DuplicateAndRetryBehavior {

        @Test
        @DisplayName("Oracle-only state fails closed without touching Keycloak")
        void register_shouldFailClosed_whenOracleCustomerAlreadyExists() {
            when(customerRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(EmailAlreadyExistsException.class)
                    .hasMessageContaining(EMAIL);

            verify(keycloakAdminService, never()).createUser(anyString(), anyString());
            verify(registrationPersistenceService, never())
                    .createCustomerWithLifetimeCart(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Keycloak-only state fails closed without writing Oracle or deleting the existing identity")
        void register_shouldFailClosed_whenKeycloakIdentityAlreadyExists() {
            when(customerRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(keycloakAdminService.createUser(EMAIL, FULL_NAME))
                    .thenThrow(new EmailAlreadyExistsException(EMAIL));

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(EmailAlreadyExistsException.class)
                    .hasMessageContaining(EMAIL);

            verify(registrationPersistenceService, never())
                    .createCustomerWithLifetimeCart(anyString(), anyString(), anyString(), anyString());
            verify(keycloakAdminService, never()).deleteUser(anyString());
        }

        @Test
        @DisplayName("retry succeeds after the first Oracle failure was compensated")
        void register_retryShouldSucceed_afterSuccessfulCompensation() {
            when(customerRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(keycloakAdminService.createUser(EMAIL, FULL_NAME))
                    .thenReturn("kc-user-1", "kc-user-2");
            when(registrationPersistenceService.createCustomerWithLifetimeCart(
                    EMAIL, FULL_NAME, "0987654321", "kc-user-1"))
                    .thenThrow(new DataIntegrityViolationException("first attempt failed"));
            when(registrationPersistenceService.createCustomerWithLifetimeCart(
                    EMAIL, FULL_NAME, "0987654321", "kc-user-2"))
                    .thenReturn(successfulResponse());

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(authService.register(validRequest())).isEqualTo(successfulResponse());

            verify(keycloakAdminService).deleteUser("kc-user-1");
            verify(keycloakAdminService, never()).deleteUser("kc-user-2");
            verify(keycloakAdminService, times(2)).createUser(EMAIL, FULL_NAME);
        }
    }

    @Nested
    @DisplayName("failure and compensation")
    class FailureAndCompensation {

        @Test
        @DisplayName("Keycloak create failure performs no Oracle write and no compensation")
        void register_shouldStop_whenKeycloakCreateFails() {
            when(customerRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(keycloakAdminService.createUser(EMAIL, FULL_NAME))
                    .thenThrow(new KeycloakProvisioningException("Keycloak unavailable"));

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(KeycloakProvisioningException.class);

            verify(registrationPersistenceService, never())
                    .createCustomerWithLifetimeCart(anyString(), anyString(), anyString(), anyString());
            verify(keycloakAdminService, never()).deleteUser(anyString());
        }

        @Test
        @DisplayName("password assignment failure deletes the just-created Keycloak user")
        void register_shouldCompensate_whenPasswordAssignmentFails() {
            stubKeycloakCreate("kc-user-1");
            doThrow(new KeycloakProvisioningException("password assignment failed"))
                    .when(keycloakAdminService).setPassword("kc-user-1", PASSWORD);

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(KeycloakProvisioningException.class);

            verify(keycloakAdminService).deleteUser("kc-user-1");
            verify(registrationPersistenceService, never())
                    .createCustomerWithLifetimeCart(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("role assignment failure deletes the just-created Keycloak user")
        void register_shouldCompensate_whenRoleAssignmentFails() {
            stubKeycloakCreate("kc-user-1");
            doThrow(new KeycloakProvisioningException("role assignment failed"))
                    .when(keycloakAdminService).assignCustomerRole("kc-user-1");

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(KeycloakProvisioningException.class);

            verify(keycloakAdminService).deleteUser("kc-user-1");
        }

        @Test
        @DisplayName("Customer persistence failure triggers Keycloak compensation")
        void register_shouldCompensate_whenCustomerPersistenceFails() {
            stubKeycloakCreate("kc-user-1");
            when(registrationPersistenceService.createCustomerWithLifetimeCart(
                    EMAIL, FULL_NAME, "0987654321", "kc-user-1"))
                    .thenThrow(new DataIntegrityViolationException("customer constraint"));

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("customer constraint");

            verify(keycloakAdminService).deleteUser("kc-user-1");
        }

        @Test
        @DisplayName("lifetime Cart transaction failure triggers Keycloak compensation")
        void register_shouldCompensate_whenLifetimeCartCreationFails() {
            stubKeycloakCreate("kc-user-1");
            when(registrationPersistenceService.createCustomerWithLifetimeCart(
                    EMAIL, FULL_NAME, "0987654321", "kc-user-1"))
                    .thenThrow(new DataIntegrityViolationException("cart constraint"));

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("cart constraint");

            verify(keycloakAdminService).deleteUser("kc-user-1");
        }

        @Test
        @DisplayName("cleanup failure never replaces the primary registration failure")
        void register_shouldPreservePrimaryFailure_whenCompensationFails(final CapturedOutput output) {
            final DataIntegrityViolationException primaryFailure =
                    new DataIntegrityViolationException("Oracle customer conflict");
            final KeycloakProvisioningException cleanupFailure =
                    new KeycloakProvisioningException("Keycloak delete failed");
            stubKeycloakCreate("kc-user-1");
            when(registrationPersistenceService.createCustomerWithLifetimeCart(
                    EMAIL, FULL_NAME, "0987654321", "kc-user-1"))
                    .thenThrow(primaryFailure);
            doThrow(cleanupFailure).when(keycloakAdminService).deleteUser("kc-user-1");

            final Throwable thrown = catchThrowable(() -> authService.register(validRequest()));

            assertThat(thrown).isSameAs(primaryFailure);
            assertThat(primaryFailure.getSuppressed()).containsExactly(cleanupFailure);
            assertThat(output.getOut())
                    .contains(EMAIL, "kc-user-1", "manual cleanup", "Keycloak delete failed");
        }

        @Test
        @DisplayName("unexpected Oracle failure is exposed as internal provisioning failure with root cause")
        void register_shouldClassifyUnexpectedOracleFailure() {
            final IllegalStateException primaryFailure = new IllegalStateException("unexpected persistence failure");
            stubKeycloakCreate("kc-user-1");
            when(registrationPersistenceService.createCustomerWithLifetimeCart(
                    EMAIL, FULL_NAME, "0987654321", "kc-user-1"))
                    .thenThrow(primaryFailure);

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(RegistrationProvisioningException.class)
                    .hasCause(primaryFailure);

            verify(keycloakAdminService).deleteUser("kc-user-1");
        }

        @Test
        @DisplayName("Oracle availability failure before Keycloak creation is an internal provisioning failure")
        void register_shouldClassifyPrecheckFailure_withoutCompensation(final CapturedOutput output) {
            final IllegalStateException primaryFailure = new IllegalStateException("Oracle unavailable");
            when(customerRepository.existsByEmail(EMAIL)).thenThrow(primaryFailure);

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(RegistrationProvisioningException.class)
                    .hasCause(primaryFailure);

            verify(keycloakAdminService, never()).createUser(anyString(), anyString());
            verify(keycloakAdminService, never()).deleteUser(anyString());
            assertThat(output.getOut()).contains(EMAIL, "Oracle unavailable");
        }
    }
}
