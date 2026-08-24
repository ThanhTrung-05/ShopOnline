package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.domain.exception.CustomerAccountNotActiveException;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticatedCustomerResolver")
class AuthenticatedCustomerResolverTest {

    private static final String SUBJECT = "customer-subject";

    @Mock private CustomerRepository customerRepository;

    private AuthenticatedCustomerResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AuthenticatedCustomerResolver(customerRepository);
    }

    @Test
    @DisplayName("ACTIVE customer is returned when JWT subject is mapped")
    void resolveActiveCustomer_shouldReturnActiveCustomer() {
        final Customer customer = sampleCustomer(Customer.CustomerStatus.ACTIVE);
        when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(customer));

        assertThat(resolver.resolveActiveCustomer(SUBJECT)).isSameAs(customer);
    }

    @ParameterizedTest
    @EnumSource(value = Customer.CustomerStatus.class, names = {"BANNED", "INACTIVE"})
    @DisplayName("non-ACTIVE customer is forbidden")
    void resolveActiveCustomer_shouldRejectNonActiveCustomer(final Customer.CustomerStatus status) {
        when(customerRepository.findByKeycloakUserId(SUBJECT))
                .thenReturn(Optional.of(sampleCustomer(status)));

        assertThatThrownBy(() -> resolver.resolveActiveCustomer(SUBJECT))
                .isInstanceOf(CustomerAccountNotActiveException.class)
                .hasMessage("Customer account is not active (status: " + status + ")");
    }

    @Test
    @DisplayName("unmapped JWT subject remains not found")
    void resolveActiveCustomer_shouldThrowNotFound_whenSubjectIsNotMapped() {
        when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveActiveCustomer(SUBJECT))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer not found with id: " + SUBJECT);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("missing JWT subject cannot match a legacy customer with null Keycloak id")
    void resolveActiveCustomer_shouldRejectMissingSubject(final String subject) {
        assertThatThrownBy(() -> resolver.resolveActiveCustomer(subject))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(customerRepository);
    }

    private static Customer sampleCustomer(final Customer.CustomerStatus status) {
        return Customer.builder()
                .customerId(1L)
                .keycloakUserId(SUBJECT)
                .status(status)
                .build();
    }
}
