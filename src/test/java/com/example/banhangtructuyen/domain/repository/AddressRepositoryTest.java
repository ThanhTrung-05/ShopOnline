package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.config.AuditingConfig;
import com.example.banhangtructuyen.domain.model.Address;
import com.example.banhangtructuyen.domain.model.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(AuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("AddressRepository Tests")
class AddressRepositoryTest {

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private Long createCustomer(final String email) {
        final Customer customer = Customer.builder()
                .email(email)
                .fullName("Nguyễn Văn A")
                .phone("0987654321")
                .passwordHash("$2a$12$hashedvalue")
                .keycloakUserId("kc-" + email)
                .status(Customer.CustomerStatus.ACTIVE)
                .role(Customer.CustomerRole.USER)
                .build();
        return customerRepository.save(customer).getCustomerId();
    }

    private static Address sampleAddress(final Long customerId, final boolean isDefault) {
        return Address.builder()
                .customerId(customerId)
                .recipientName("Nguyễn Văn A")
                .phone("0987654321")
                .line1("123 Đường Lê Lợi")
                .ward("Phường Bến Nghé")
                .district("Quận 1")
                .province("TP. Hồ Chí Minh")
                .defaultAddress(isDefault)
                .build();
    }

    @Test
    @DisplayName("findByCustomerIdOrderByAddressIdAsc — returns addresses ordered by addressId")
    void findByCustomerId_shouldReturnOrderedAddresses() {
        final Long customerId = createCustomer("c1@example.com");
        addressRepository.save(sampleAddress(customerId, true));
        addressRepository.save(sampleAddress(customerId, false));

        final List<Address> result = addressRepository.findByCustomerIdOrderByAddressIdAsc(customerId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAddressId()).isLessThan(result.get(1).getAddressId());
    }

    @Test
    @DisplayName("findByAddressIdAndCustomerId — returns empty for another customer's address")
    void findByAddressIdAndCustomerId_shouldReturnEmpty_forOtherCustomer() {
        final Long customerId = createCustomer("c2@example.com");
        final Long otherCustomerId = createCustomer("c3@example.com");
        final Address saved = addressRepository.save(sampleAddress(customerId, true));

        assertThat(addressRepository.findByAddressIdAndCustomerId(saved.getAddressId(), otherCustomerId)).isEmpty();
        assertThat(addressRepository.findByAddressIdAndCustomerId(saved.getAddressId(), customerId)).isPresent();
    }

    @Test
    @DisplayName("countByCustomerId — counts only addresses owned by that customer")
    void countByCustomerId_shouldCountOwnAddressesOnly() {
        final Long customerId = createCustomer("c4@example.com");
        final Long otherCustomerId = createCustomer("c5@example.com");
        addressRepository.save(sampleAddress(customerId, true));
        addressRepository.save(sampleAddress(customerId, false));
        addressRepository.save(sampleAddress(otherCustomerId, true));

        assertThat(addressRepository.countByCustomerId(customerId)).isEqualTo(2);
    }

    @Test
    @DisplayName("clearDefaultForCustomer — clears default flag for that customer's addresses")
    void clearDefaultForCustomer_shouldClearDefaultFlag() {
        final Long customerId = createCustomer("c6@example.com");
        final Address defaultAddr = addressRepository.save(sampleAddress(customerId, true));

        addressRepository.clearDefaultForCustomer(customerId);
        addressRepository.flush();

        final Address reloaded = addressRepository.findById(defaultAddr.getAddressId()).orElseThrow();
        assertThat(reloaded.isDefaultAddress()).isFalse();
    }

    @Test
    @DisplayName("findMinAddressIdByCustomerId — returns the smallest addressId for that customer")
    void findMinAddressIdByCustomerId_shouldReturnSmallestId() {
        final Long customerId = createCustomer("c7@example.com");
        final Address first = addressRepository.save(sampleAddress(customerId, true));
        addressRepository.save(sampleAddress(customerId, false));

        final Optional<Long> minId = addressRepository.findMinAddressIdByCustomerId(customerId);

        assertThat(minId).contains(first.getAddressId());
    }

    @Test
    @DisplayName("findMinAddressIdByCustomerId — returns empty when customer has no addresses")
    void findMinAddressIdByCustomerId_shouldReturnEmpty_whenNoAddresses() {
        final Long customerId = createCustomer("c8@example.com");

        assertThat(addressRepository.findMinAddressIdByCustomerId(customerId)).isEmpty();
    }

    @Test
    @DisplayName("delete — removing a non-default address does not affect the existing default")
    void delete_nonDefault_shouldNotAffectDefault() {
        final Long customerId = createCustomer("c9@example.com");
        final Address defaultAddr = addressRepository.save(sampleAddress(customerId, true));
        final Address nonDefault = addressRepository.save(sampleAddress(customerId, false));

        addressRepository.delete(nonDefault);
        addressRepository.flush();

        final Address reloaded = addressRepository.findById(defaultAddr.getAddressId()).orElseThrow();
        assertThat(reloaded.isDefaultAddress()).isTrue();
    }
}
