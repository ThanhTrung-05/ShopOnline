package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.config.AuditingConfig;
import com.example.banhangtructuyen.domain.model.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code @DataJpaTest} for {@link CustomerRepository} against the H2 test profile.
 * {@link AuditingConfig} is imported explicitly because the DataJpaTest slice excludes
 * regular {@code @Configuration} classes, and {@code @EnableJpaAuditing} on the main
 * application class needs its {@code auditorAware} bean to resolve.
 */
@DataJpaTest
@Import(AuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("CustomerRepository Tests")
class CustomerRepositoryTest {

    @org.springframework.beans.factory.annotation.Autowired
    private CustomerRepository customerRepository;

    private static Customer sampleCustomer(final String email) {
        return Customer.builder()
                .email(email)
                .fullName("Nguyễn Văn A")
                .phone("0987654321")
                .passwordHash("$2a$12$hashedvalue")
                .status(Customer.CustomerStatus.ACTIVE)
                .role(Customer.CustomerRole.USER)
                .build();
    }

    @Test
    @DisplayName("existsByEmail — returns true when email is registered")
    void existsByEmail_shouldReturnTrue_whenEmailExists() {
        customerRepository.save(sampleCustomer("existing@example.com"));

        assertThat(customerRepository.existsByEmail("existing@example.com")).isTrue();
    }

    @Test
    @DisplayName("existsByEmail — returns false when email is not registered")
    void existsByEmail_shouldReturnFalse_whenEmailDoesNotExist() {
        assertThat(customerRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    @DisplayName("findByEmail — returns customer when email exists")
    void findByEmail_shouldReturnCustomer_whenEmailExists() {
        customerRepository.save(sampleCustomer("findme@example.com"));

        assertThat(customerRepository.findByEmail("findme@example.com"))
                .isPresent()
                .get()
                .satisfies(c -> assertThat(c.getFullName()).isEqualTo("Nguyễn Văn A"));
    }

    @Test
    @DisplayName("save — rejects duplicate email via unique constraint")
    void save_shouldThrow_whenEmailDuplicate() {
        customerRepository.save(sampleCustomer("dup@example.com"));
        customerRepository.flush();

        assertThatThrownBy(() -> {
            customerRepository.save(sampleCustomer("dup@example.com"));
            customerRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("save — persists default ROLE=USER and STATUS=ACTIVE")
    void save_shouldPersistDefaults() {
        final Customer saved = customerRepository.save(sampleCustomer("defaults@example.com"));

        assertThat(saved.getCustomerId()).isNotNull();
        assertThat(saved.getRole()).isEqualTo(Customer.CustomerRole.USER);
        assertThat(saved.getStatus()).isEqualTo(Customer.CustomerStatus.ACTIVE);
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$12$hashedvalue");
    }

    @Test
    @DisplayName("findByKeycloakUserId — returns customer when keycloakUserId exists")
    void findByKeycloakUserId_shouldReturnCustomer_whenExists() {
        final Customer customer = sampleCustomer("kc-linked@example.com");
        customer.setKeycloakUserId("f47ac10b-58cc-4372-a567-0e02b2c3d479");
        customerRepository.save(customer);

        assertThat(customerRepository.findByKeycloakUserId("f47ac10b-58cc-4372-a567-0e02b2c3d479"))
                .isPresent()
                .get()
                .satisfies(c -> assertThat(c.getEmail()).isEqualTo("kc-linked@example.com"));
    }

    @Test
    @DisplayName("findByKeycloakUserId — returns empty when keycloakUserId does not exist")
    void findByKeycloakUserId_shouldReturnEmpty_whenNotFound() {
        assertThat(customerRepository.findByKeycloakUserId("no-such-subject")).isEmpty();
    }
}
