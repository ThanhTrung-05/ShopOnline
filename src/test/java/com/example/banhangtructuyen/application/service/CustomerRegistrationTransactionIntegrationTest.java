package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.service.impl.CustomerRegistrationPersistenceService;
import com.example.banhangtructuyen.domain.model.Cart;
import com.example.banhangtructuyen.domain.repository.CartRepository;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:registration-transaction;MODE=Oracle;DB_CLOSE_DELAY=-1")
class CustomerRegistrationTransactionIntegrationTest {

    @Autowired
    private CustomerRegistrationPersistenceService persistenceService;

    @Autowired
    private CustomerRepository customerRepository;

    @MockBean
    private CartRepository cartRepository;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
        reset(cartRepository);
    }

    @Test
    @DisplayName("lifetime Cart failure rolls back the Customer insert")
    void createCustomerWithLifetimeCart_shouldRollbackCustomer_whenCartCreationFails() {
        when(cartRepository.saveAndFlush(any(Cart.class)))
                .thenThrow(new DataIntegrityViolationException("cart insert failed"));

        assertThatThrownBy(() -> persistenceService.createCustomerWithLifetimeCart(
                "rollback@example.com", "Rollback Customer", null, "kc-rollback-user"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(customerRepository.existsByEmail("rollback@example.com")).isFalse();
    }
}
