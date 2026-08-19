package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.config.AuditingConfig;
import com.example.banhangtructuyen.domain.model.Cart;
import com.example.banhangtructuyen.domain.model.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(AuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("CartRepository Tests")
class CartRepositoryTest {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    @DisplayName("findByCustomerId returns customer's lifetime cart")
    void findByCustomerId_shouldReturnCart() {
        final Customer customer = customerRepository.save(sampleCustomer("cart-owner@example.com"));
        final Cart cart = cartRepository.save(Cart.builder()
                .customerId(customer.getCustomerId())
                .build());

        assertThat(cartRepository.findByCustomerId(customer.getCustomerId()))
                .isPresent()
                .get()
                .satisfies(found -> assertThat(found.getCartId()).isEqualTo(cart.getCartId()));
    }

    @Test
    @DisplayName("one Customer cannot have multiple Carts")
    void save_shouldRejectDuplicateCartForCustomer() {
        final Customer customer = customerRepository.save(sampleCustomer("one-cart@example.com"));
        cartRepository.save(Cart.builder().customerId(customer.getCustomerId()).build());
        cartRepository.flush();

        assertThatThrownBy(() -> {
            cartRepository.save(Cart.builder().customerId(customer.getCustomerId()).build());
            cartRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Customer sampleCustomer(final String email) {
        return Customer.builder()
                .email(email)
                .fullName("Nguyen Van A")
                .phone("0987654321")
                .passwordHash("$2a$12$hashedvalue")
                .status(Customer.CustomerStatus.ACTIVE)
                .role(Customer.CustomerRole.USER)
                .build();
    }
}
