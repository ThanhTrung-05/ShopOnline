package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.auth.RegisterResponse;
import com.example.banhangtructuyen.application.service.impl.CustomerRegistrationPersistenceService;
import com.example.banhangtructuyen.domain.model.Cart;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.repository.CartRepository;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerRegistrationPersistenceServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private CartRepository cartRepository;

    private CustomerRegistrationPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        persistenceService = new CustomerRegistrationPersistenceService(customerRepository, cartRepository);
    }

    @Test
    @DisplayName("creates an ACTIVE Customer and one lifetime Cart without storing a password hash")
    void createCustomerWithLifetimeCart_shouldPersistRequiredOracleState() {
        when(customerRepository.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> {
            final Customer customer = invocation.getArgument(0);
            customer.setCustomerId(11L);
            customer.setCreatedAt(Instant.parse("2026-08-20T00:00:00Z"));
            return customer;
        });
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final RegisterResponse response = persistenceService.createCustomerWithLifetimeCart(
                "customer@example.com", "Nguyen Van A", "0987654321", "kc-user-1");

        final ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        final ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);
        verify(customerRepository).saveAndFlush(customerCaptor.capture());
        verify(cartRepository).saveAndFlush(cartCaptor.capture());

        final Customer customer = customerCaptor.getValue();
        assertThat(customer.getKeycloakUserId()).isEqualTo("kc-user-1");
        assertThat(customer.getPasswordHash()).isNull();
        assertThat(customer.getStatus()).isEqualTo(Customer.CustomerStatus.ACTIVE);
        assertThat(customer.getRole()).isEqualTo(Customer.CustomerRole.USER);
        assertThat(cartCaptor.getValue().getCustomerId()).isEqualTo(11L);
        assertThat(response.customerId()).isEqualTo(11L);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.role()).isEqualTo("USER");

        final InOrder order = inOrder(customerRepository, cartRepository);
        order.verify(customerRepository).saveAndFlush(any(Customer.class));
        order.verify(cartRepository).saveAndFlush(any(Cart.class));
    }

    @Test
    @DisplayName("Customer save failure stops before lifetime Cart creation")
    void createCustomerWithLifetimeCart_shouldStop_whenCustomerSaveFails() {
        final DataIntegrityViolationException failure =
                new DataIntegrityViolationException("customer constraint");
        when(customerRepository.saveAndFlush(any(Customer.class))).thenThrow(failure);

        assertThatThrownBy(() -> persistenceService.createCustomerWithLifetimeCart(
                "customer@example.com", "Nguyen Van A", null, "kc-user-1"))
                .isSameAs(failure);

        verify(cartRepository, never()).saveAndFlush(any(Cart.class));
    }

    @Test
    @DisplayName("lifetime Cart failure is propagated to the transaction boundary")
    void createCustomerWithLifetimeCart_shouldPropagate_whenCartSaveFails() {
        when(customerRepository.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> {
            final Customer customer = invocation.getArgument(0);
            customer.setCustomerId(11L);
            return customer;
        });
        final DataIntegrityViolationException failure =
                new DataIntegrityViolationException("cart constraint");
        when(cartRepository.saveAndFlush(any(Cart.class))).thenThrow(failure);

        assertThatThrownBy(() -> persistenceService.createCustomerWithLifetimeCart(
                "customer@example.com", "Nguyen Van A", null, "kc-user-1"))
                .isSameAs(failure);

        verify(customerRepository).saveAndFlush(any(Customer.class));
        verify(cartRepository).saveAndFlush(any(Cart.class));
    }
}
