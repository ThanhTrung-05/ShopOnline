package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.customer.CustomerResponse;
import com.example.banhangtructuyen.application.dto.customer.UpdateProfileRequest;
import com.example.banhangtructuyen.application.service.impl.CustomerProfileServiceImpl;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CustomerProfileServiceImpl}. {@link CustomerRepository} is mocked.
 */
@ExtendWith(MockitoExtension.class)
class CustomerProfileServiceImplTest {

    private static final String SUBJECT = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

    @Mock private CustomerRepository customerRepository;

    private CustomerProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CustomerProfileServiceImpl(customerRepository);
    }

    private static Customer sampleCustomer() {
        return Customer.builder()
                .customerId(1L)
                .email("customer@example.com")
                .fullName("Nguyễn Văn A")
                .phone("0987654321")
                .passwordHash("$2a$12$hashedvalue")
                .keycloakUserId(SUBJECT)
                .status(Customer.CustomerStatus.ACTIVE)
                .role(Customer.CustomerRole.USER)
                .createdAt(Instant.parse("2026-08-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-08-01T00:00:00Z"))
                .build();
    }

    @Nested
    @DisplayName("getProfile")
    class GetProfile {

        @Test
        @DisplayName("returns profile when customer is found by keycloak subject")
        void getProfile_shouldReturnProfile_whenFound() {
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));

            final CustomerResponse response = service.getProfile(SUBJECT);

            assertThat(response.customerId()).isEqualTo(1L);
            assertThat(response.email()).isEqualTo("customer@example.com");
            assertThat(response.fullName()).isEqualTo("Nguyễn Văn A");
            assertThat(response.phone()).isEqualTo("0987654321");
            assertThat(response.role()).isEqualTo("USER");
            assertThat(response.status()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("does not expose passwordHash or keycloakUserId")
        void getProfile_shouldNotExposeSecrets() {
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));

            final CustomerResponse response = service.getProfile(SUBJECT);

            assertThat(response.toString()).doesNotContain("hashedvalue", SUBJECT);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when no customer matches the subject")
        void getProfile_shouldThrow_whenCustomerNotFound() {
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getProfile(SUBJECT))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateProfile — partial update semantics")
    class UpdateProfile {

        @Test
        @DisplayName("updates fullName only when phone is null")
        void updateProfile_shouldUpdateFullNameOnly() {
            final Customer existing = sampleCustomer();
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(existing));
            when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            final CustomerResponse response = service.updateProfile(SUBJECT, new UpdateProfileRequest("Nguyễn Văn B", null));

            assertThat(response.fullName()).isEqualTo("Nguyễn Văn B");
            assertThat(response.phone()).isEqualTo("0987654321");
        }

        @Test
        @DisplayName("updates phone only when fullName is null")
        void updateProfile_shouldUpdatePhoneOnly() {
            final Customer existing = sampleCustomer();
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(existing));
            when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            final CustomerResponse response = service.updateProfile(SUBJECT, new UpdateProfileRequest(null, "0912345678"));

            assertThat(response.fullName()).isEqualTo("Nguyễn Văn A");
            assertThat(response.phone()).isEqualTo("0912345678");
        }

        @Test
        @DisplayName("updates both fullName and phone when both are present")
        void updateProfile_shouldUpdateBoth() {
            final Customer existing = sampleCustomer();
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(existing));
            when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            final CustomerResponse response = service.updateProfile(
                    SUBJECT, new UpdateProfileRequest("Nguyễn Văn B", "0912345678"));

            assertThat(response.fullName()).isEqualTo("Nguyễn Văn B");
            assertThat(response.phone()).isEqualTo("0912345678");
        }

        @Test
        @DisplayName("leaves both fields unchanged when both are null")
        void updateProfile_shouldLeaveUnchanged_whenBothNull() {
            final Customer existing = sampleCustomer();
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(existing));
            when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            final CustomerResponse response = service.updateProfile(SUBJECT, new UpdateProfileRequest(null, null));

            assertThat(response.fullName()).isEqualTo("Nguyễn Văn A");
            assertThat(response.phone()).isEqualTo("0987654321");
        }

        @Test
        @DisplayName("throws ConstraintViolationException when fullName is blank, and never saves")
        void updateProfile_shouldThrow_whenFullNameBlank() {
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));

            assertThatThrownBy(() -> service.updateProfile(SUBJECT, new UpdateProfileRequest("   ", null)))
                    .isInstanceOf(ConstraintViolationException.class);

            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ConstraintViolationException when phone format is invalid, and never saves")
        void updateProfile_shouldThrow_whenPhoneInvalid() {
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));

            assertThatThrownBy(() -> service.updateProfile(SUBJECT, new UpdateProfileRequest(null, "123")))
                    .isInstanceOf(ConstraintViolationException.class);

            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when no customer matches the subject")
        void updateProfile_shouldThrow_whenCustomerNotFound() {
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateProfile(SUBJECT, new UpdateProfileRequest("Name", null)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("never calls Keycloak Admin API — service has no such dependency")
        void updateProfile_shouldNotTouchKeycloak() {
            // CustomerProfileServiceImpl only depends on CustomerRepository — no KeycloakAdminService
            // field exists, so there is no call surface to Keycloak in this service by construction.
            final Customer existing = sampleCustomer();
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(existing));
            when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            service.updateProfile(SUBJECT, new UpdateProfileRequest("Nguyễn Văn B", null));
        }
    }
}
