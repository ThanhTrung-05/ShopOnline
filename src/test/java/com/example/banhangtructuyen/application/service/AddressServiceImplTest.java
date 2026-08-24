package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.customer.AddressRequest;
import com.example.banhangtructuyen.application.dto.customer.AddressResponse;
import com.example.banhangtructuyen.application.service.impl.AddressServiceImpl;
import com.example.banhangtructuyen.domain.exception.CustomerAccountNotActiveException;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Address;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.repository.AddressRepository;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AddressServiceImpl}. {@link AddressRepository} and
 * {@link CustomerRepository} are mocked.
 */
@ExtendWith(MockitoExtension.class)
class AddressServiceImplTest {

    private static final String SUBJECT = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final Long CUSTOMER_ID = 1L;

    @Mock private AddressRepository addressRepository;
    @Mock private CustomerRepository customerRepository;

    private AddressServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AddressServiceImpl(
                addressRepository,
                new AuthenticatedCustomerResolver(customerRepository));
    }

    private static Customer sampleCustomer() {
        return sampleCustomer(Customer.CustomerStatus.ACTIVE);
    }

    private static Customer sampleCustomer(final Customer.CustomerStatus status) {
        return Customer.builder()
                .customerId(CUSTOMER_ID)
                .email("customer@example.com")
                .fullName("Nguyễn Văn A")
                .keycloakUserId(SUBJECT)
                .status(status)
                .role(Customer.CustomerRole.USER)
                .build();
    }

    private static Address sampleAddress(final Long addressId, final boolean isDefault) {
        return Address.builder()
                .addressId(addressId)
                .customerId(CUSTOMER_ID)
                .recipientName("Nguyễn Văn A")
                .phone("0987654321")
                .line1("123 Đường Lê Lợi")
                .ward("Phường Bến Nghé")
                .district("Quận 1")
                .province("TP. Hồ Chí Minh")
                .defaultAddress(isDefault)
                .build();
    }

    private static AddressRequest sampleRequest() {
        return new AddressRequest("Nguyễn Văn A", "0987654321", "123 Đường Lê Lợi",
                "Phường Bến Nghé", "Quận 1", "TP. Hồ Chí Minh");
    }

    private void stubCustomerFound() {
        when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(sampleCustomer()));
    }

    @Nested
    @DisplayName("listAddresses")
    class ListAddresses {

        @Test
        @DisplayName("returns addresses for the resolved customer")
        void listAddresses_shouldReturnAddresses() {
            stubCustomerFound();
            when(addressRepository.findByCustomerIdOrderByAddressIdAsc(CUSTOMER_ID))
                    .thenReturn(List.of(sampleAddress(1L, true), sampleAddress(2L, false)));

            final List<AddressResponse> result = service.listAddresses(SUBJECT);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).isDefault()).isTrue();
            assertThat(result.get(1).isDefault()).isFalse();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when customer is not found")
        void listAddresses_shouldThrow_whenCustomerNotFound() {
            when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listAddresses(SUBJECT))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @ParameterizedTest
        @EnumSource(value = Customer.CustomerStatus.class, names = {"BANNED", "INACTIVE"})
        @DisplayName("does not query addresses when customer is not ACTIVE")
        void listAddresses_shouldReject_whenCustomerIsNotActive(final Customer.CustomerStatus status) {
            when(customerRepository.findByKeycloakUserId(SUBJECT))
                    .thenReturn(Optional.of(sampleCustomer(status)));

            assertThatThrownBy(() -> service.listAddresses(SUBJECT))
                    .isInstanceOf(CustomerAccountNotActiveException.class);

            verify(addressRepository, never()).findByCustomerIdOrderByAddressIdAsc(any());
        }
    }

    @Nested
    @DisplayName("getAddress")
    class GetAddress {

        @Test
        @DisplayName("returns the address when it belongs to the resolved customer")
        void getAddress_shouldReturnAddress_whenOwned() {
            stubCustomerFound();
            when(addressRepository.findByAddressIdAndCustomerId(1L, CUSTOMER_ID))
                    .thenReturn(Optional.of(sampleAddress(1L, true)));

            final AddressResponse response = service.getAddress(SUBJECT, 1L);

            assertThat(response.addressId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException (404, not 403) when address belongs to another customer")
        void getAddress_shouldThrow_whenNotOwned() {
            stubCustomerFound();
            when(addressRepository.findByAddressIdAndCustomerId(99L, CUSTOMER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getAddress(SUBJECT, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("createAddress")
    class CreateAddress {

        @Test
        @DisplayName("first address for a customer becomes default")
        void createAddress_shouldBecomeDefault_whenFirst() {
            stubCustomerFound();
            when(addressRepository.countByCustomerId(CUSTOMER_ID)).thenReturn(0L);
            when(addressRepository.save(any(Address.class))).thenAnswer(inv -> {
                final Address a = inv.getArgument(0);
                a.setAddressId(1L);
                return a;
            });

            final AddressResponse response = service.createAddress(SUBJECT, sampleRequest());

            assertThat(response.isDefault()).isTrue();
        }

        @Test
        @DisplayName("second and later addresses are non-default")
        void createAddress_shouldBeNonDefault_whenNotFirst() {
            stubCustomerFound();
            when(addressRepository.countByCustomerId(CUSTOMER_ID)).thenReturn(1L);
            when(addressRepository.save(any(Address.class))).thenAnswer(inv -> {
                final Address a = inv.getArgument(0);
                a.setAddressId(2L);
                return a;
            });

            final AddressResponse response = service.createAddress(SUBJECT, sampleRequest());

            assertThat(response.isDefault()).isFalse();
        }

        @Test
        @DisplayName("persists address bound to the resolved customerId, never a client-supplied one")
        void createAddress_shouldBindToResolvedCustomerId() {
            stubCustomerFound();
            when(addressRepository.countByCustomerId(CUSTOMER_ID)).thenReturn(0L);
            final ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
            when(addressRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            service.createAddress(SUBJECT, sampleRequest());

            assertThat(captor.getValue().getCustomerId()).isEqualTo(CUSTOMER_ID);
        }
    }

    @Nested
    @DisplayName("updateAddress")
    class UpdateAddress {

        @Test
        @DisplayName("updates fields of an owned address")
        void updateAddress_shouldUpdateFields_whenOwned() {
            stubCustomerFound();
            final Address existing = sampleAddress(1L, false);
            when(addressRepository.findByAddressIdAndCustomerId(1L, CUSTOMER_ID)).thenReturn(Optional.of(existing));
            when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

            final AddressRequest request = new AddressRequest("Trần Thị B", "0912345678", "456 Đường Mới",
                    "Phường Mới", "Quận 2", "Hà Nội");
            final AddressResponse response = service.updateAddress(SUBJECT, 1L, request);

            assertThat(response.recipientName()).isEqualTo("Trần Thị B");
            assertThat(response.phone()).isEqualTo("0912345678");
            assertThat(response.line1()).isEqualTo("456 Đường Mới");
        }

        @Test
        @DisplayName("update does not change defaultAddress status")
        void updateAddress_shouldNotChangeDefaultStatus() {
            stubCustomerFound();
            final Address existing = sampleAddress(1L, true);
            when(addressRepository.findByAddressIdAndCustomerId(1L, CUSTOMER_ID)).thenReturn(Optional.of(existing));
            when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

            final AddressResponse response = service.updateAddress(SUBJECT, 1L, sampleRequest());

            assertThat(response.isDefault()).isTrue();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when address belongs to another customer")
        void updateAddress_shouldThrow_whenNotOwned() {
            stubCustomerFound();
            when(addressRepository.findByAddressIdAndCustomerId(99L, CUSTOMER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateAddress(SUBJECT, 99L, sampleRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(addressRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteAddress")
    class DeleteAddress {

        @Test
        @DisplayName("deleting a non-default address does not promote another address")
        void deleteAddress_shouldNotPromote_whenNonDefault() {
            stubCustomerFound();
            final Address nonDefault = sampleAddress(2L, false);
            when(addressRepository.findByAddressIdAndCustomerId(2L, CUSTOMER_ID)).thenReturn(Optional.of(nonDefault));

            service.deleteAddress(SUBJECT, 2L);

            verify(addressRepository).delete(nonDefault);
            verify(addressRepository, never()).findMinAddressIdByCustomerId(any());
        }

        @Test
        @DisplayName("deleting the default address promotes the address with the smallest remaining addressId")
        void deleteAddress_shouldPromoteSmallestId_whenDefaultDeletedAndOthersRemain() {
            stubCustomerFound();
            final Address defaultAddr = sampleAddress(1L, true);
            final Address remaining = sampleAddress(2L, false);
            when(addressRepository.findByAddressIdAndCustomerId(1L, CUSTOMER_ID)).thenReturn(Optional.of(defaultAddr));
            when(addressRepository.findMinAddressIdByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(2L));
            when(addressRepository.findByAddressIdAndCustomerId(2L, CUSTOMER_ID)).thenReturn(Optional.of(remaining));

            service.deleteAddress(SUBJECT, 1L);

            verify(addressRepository).delete(defaultAddr);
            verify(addressRepository).save(remaining);
            assertThat(remaining.isDefaultAddress()).isTrue();
        }

        @Test
        @DisplayName("deleting the only (default) address leaves no default — no promotion attempted")
        void deleteAddress_shouldNotPromote_whenNoAddressesRemain() {
            stubCustomerFound();
            final Address onlyAddress = sampleAddress(1L, true);
            when(addressRepository.findByAddressIdAndCustomerId(1L, CUSTOMER_ID)).thenReturn(Optional.of(onlyAddress));
            when(addressRepository.findMinAddressIdByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

            service.deleteAddress(SUBJECT, 1L);

            verify(addressRepository).delete(onlyAddress);
            verify(addressRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when address belongs to another customer")
        void deleteAddress_shouldThrow_whenNotOwned() {
            stubCustomerFound();
            when(addressRepository.findByAddressIdAndCustomerId(99L, CUSTOMER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteAddress(SUBJECT, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(addressRepository, never()).delete(any(Address.class));
        }
    }

    @Nested
    @DisplayName("setDefaultAddress")
    class SetDefaultAddress {

        @Test
        @DisplayName("clears old default and sets target as default in the same call")
        void setDefaultAddress_shouldClearOldAndSetNew() {
            stubCustomerFound();
            final Address target = sampleAddress(2L, false);
            when(addressRepository.findByAddressIdAndCustomerId(2L, CUSTOMER_ID)).thenReturn(Optional.of(target));
            when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

            final AddressResponse response = service.setDefaultAddress(SUBJECT, 2L);

            verify(addressRepository, times(1)).clearDefaultForCustomer(CUSTOMER_ID);
            assertThat(response.isDefault()).isTrue();
        }

        @Test
        @DisplayName("is a no-op (still clears+saves) when target is already default")
        void setDefaultAddress_shouldSkipWork_whenAlreadyDefault() {
            stubCustomerFound();
            final Address alreadyDefault = sampleAddress(1L, true);
            when(addressRepository.findByAddressIdAndCustomerId(1L, CUSTOMER_ID)).thenReturn(Optional.of(alreadyDefault));

            final AddressResponse response = service.setDefaultAddress(SUBJECT, 1L);

            verify(addressRepository, never()).clearDefaultForCustomer(any());
            verify(addressRepository, never()).save(any());
            assertThat(response.isDefault()).isTrue();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when address belongs to another customer")
        void setDefaultAddress_shouldThrow_whenNotOwned() {
            stubCustomerFound();
            when(addressRepository.findByAddressIdAndCustomerId(99L, CUSTOMER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.setDefaultAddress(SUBJECT, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(addressRepository, never()).clearDefaultForCustomer(any());
        }
    }
}
