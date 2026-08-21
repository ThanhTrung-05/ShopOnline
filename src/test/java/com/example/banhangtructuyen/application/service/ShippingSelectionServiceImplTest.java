package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.shipping.ShippingSelectionRequest;
import com.example.banhangtructuyen.application.dto.shipping.ValidatedShippingSelection;
import com.example.banhangtructuyen.application.service.impl.ShippingSelectionServiceImpl;
import com.example.banhangtructuyen.domain.exception.CustomerAccountNotActiveException;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Address;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.ShippingMethod;
import com.example.banhangtructuyen.domain.repository.AddressRepository;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingSelectionService")
class ShippingSelectionServiceImplTest {

    private static final String SUBJECT = "customer-subject";
    private static final Long CUSTOMER_ID = 1L;
    private static final Long OTHER_CUSTOMER_ID = 2L;
    private static final Long ADDRESS_ID = 42L;

    @Mock private AddressRepository addressRepository;
    @Mock private CustomerRepository customerRepository;

    private ShippingSelectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShippingSelectionServiceImpl(
                addressRepository,
                new AuthenticatedCustomerResolver(customerRepository));
    }

    @ParameterizedTest
    @EnumSource(ShippingMethod.class)
    @DisplayName("ACTIVE customer can select an owned address with every MVP shipping method")
    void validateSelection_shouldReturnServerDerivedData_whenCustomerAndAddressAreValid(
            final ShippingMethod shippingMethod) {
        final Address address = sampleAddress(ADDRESS_ID, CUSTOMER_ID);
        stubCustomer(Customer.CustomerStatus.ACTIVE);
        when(addressRepository.findByAddressIdAndCustomerId(ADDRESS_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(address));

        final ValidatedShippingSelection selection = service.validateSelection(
                SUBJECT,
                new ShippingSelectionRequest(ADDRESS_ID, shippingMethod));

        assertThat(selection.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(selection.addressId()).isEqualTo(ADDRESS_ID);
        assertThat(selection.recipientName()).isEqualTo("Nguyen Van A");
        assertThat(selection.phone()).isEqualTo("0987654321");
        assertThat(selection.line1()).isEqualTo("123 Le Loi");
        assertThat(selection.ward()).isEqualTo("Ben Nghe");
        assertThat(selection.district()).isEqualTo("District 1");
        assertThat(selection.province()).isEqualTo("Ho Chi Minh City");
        assertThat(selection.shippingMethod()).isEqualTo(shippingMethod);
    }

    @Test
    @DisplayName("unmapped JWT subject is rejected before querying an address")
    void validateSelection_shouldReject_whenSubjectIsNotMapped() {
        when(customerRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateSelection(
                SUBJECT,
                new ShippingSelectionRequest(ADDRESS_ID, ShippingMethod.STANDARD)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer not found with id: " + SUBJECT);

        verifyNoInteractions(addressRepository);
    }

    @ParameterizedTest
    @EnumSource(value = Customer.CustomerStatus.class, names = {"BANNED", "INACTIVE"})
    @DisplayName("non-ACTIVE customer is rejected before querying an address")
    void validateSelection_shouldReject_whenCustomerIsNotActive(final Customer.CustomerStatus status) {
        stubCustomer(status);

        assertThatThrownBy(() -> service.validateSelection(
                SUBJECT,
                new ShippingSelectionRequest(ADDRESS_ID, ShippingMethod.STANDARD)))
                .isInstanceOf(CustomerAccountNotActiveException.class)
                .hasMessage("Customer account is not active (status: " + status + ")");

        verifyNoInteractions(addressRepository);
    }

    @Test
    @DisplayName("another customer's address is hidden as not found")
    void validateSelection_shouldReject_withoutLoadingOtherCustomersAddress() {
        stubCustomer(Customer.CustomerStatus.ACTIVE);
        final Address otherCustomersAddress = sampleAddress(ADDRESS_ID, OTHER_CUSTOMER_ID);
        when(addressRepository.findByAddressIdAndCustomerId(
                otherCustomersAddress.getAddressId(), CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateSelection(
                SUBJECT,
                new ShippingSelectionRequest(ADDRESS_ID, ShippingMethod.EXPRESS)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Address not found with id: " + ADDRESS_ID);

        verify(addressRepository).findByAddressIdAndCustomerId(ADDRESS_ID, CUSTOMER_ID);
        verify(addressRepository, never()).findById(ADDRESS_ID);
    }

    @Test
    @DisplayName("nonexistent address is rejected with the existing ownership-safe behavior")
    void validateSelection_shouldReject_whenAddressDoesNotExist() {
        final long nonexistentAddressId = 999L;
        stubCustomer(Customer.CustomerStatus.ACTIVE);
        when(addressRepository.findByAddressIdAndCustomerId(nonexistentAddressId, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateSelection(
                SUBJECT,
                new ShippingSelectionRequest(nonexistentAddressId, ShippingMethod.STANDARD)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Address not found with id: " + nonexistentAddressId);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    @DisplayName("null or non-positive address id is rejected before customer/address lookup")
    void validateSelection_shouldReject_whenAddressIdIsInvalid(final Long addressId) {
        assertThatThrownBy(() -> service.validateSelection(
                SUBJECT,
                new ShippingSelectionRequest(addressId, ShippingMethod.STANDARD)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(customerRepository, addressRepository);
    }

    @Test
    @DisplayName("null shipping method is rejected before customer/address lookup")
    void validateSelection_shouldReject_whenShippingMethodIsNull() {
        assertThatThrownBy(() -> service.validateSelection(
                SUBJECT,
                new ShippingSelectionRequest(ADDRESS_ID, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Shipping method is required");

        verifyNoInteractions(customerRepository, addressRepository);
    }

    private void stubCustomer(final Customer.CustomerStatus status) {
        when(customerRepository.findByKeycloakUserId(SUBJECT))
                .thenReturn(Optional.of(Customer.builder()
                        .customerId(CUSTOMER_ID)
                        .keycloakUserId(SUBJECT)
                        .status(status)
                        .build()));
    }

    private static Address sampleAddress(final Long addressId, final Long customerId) {
        return Address.builder()
                .addressId(addressId)
                .customerId(customerId)
                .recipientName("Nguyen Van A")
                .phone("0987654321")
                .line1("123 Le Loi")
                .ward("Ben Nghe")
                .district("District 1")
                .province("Ho Chi Minh City")
                .defaultAddress(true)
                .build();
    }
}
