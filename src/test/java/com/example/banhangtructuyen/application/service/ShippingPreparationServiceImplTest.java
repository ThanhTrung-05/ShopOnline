package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.shipping.ShippingCheckoutInfo;
import com.example.banhangtructuyen.application.dto.shipping.ShippingSelectionRequest;
import com.example.banhangtructuyen.application.service.impl.ShippingFeeServiceImpl;
import com.example.banhangtructuyen.application.service.impl.ShippingPreparationServiceImpl;
import com.example.banhangtructuyen.application.service.impl.ShippingSelectionServiceImpl;
import com.example.banhangtructuyen.config.ShippingFeeProperties;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Address;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.ShippingMethod;
import com.example.banhangtructuyen.domain.model.ShippingRegion;
import com.example.banhangtructuyen.domain.repository.AddressRepository;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingPreparationService")
class ShippingPreparationServiceImplTest {

    private static final String SUBJECT = "customer-subject";
    private static final Long CUSTOMER_ID = 1L;
    private static final Long ADDRESS_ID = 42L;

    @Mock private AddressRepository addressRepository;
    @Mock private CustomerRepository customerRepository;

    private ShippingFeeService shippingFeeService;
    private ShippingPreparationService service;

    @BeforeEach
    void setUp() {
        final ShippingSelectionService shippingSelectionService = new ShippingSelectionServiceImpl(
                addressRepository,
                new AuthenticatedCustomerResolver(customerRepository));
        shippingFeeService = spy(new ShippingFeeServiceImpl(configuredProperties()));
        service = new ShippingPreparationServiceImpl(shippingSelectionService, shippingFeeService);
    }

    @Test
    @DisplayName("Hà Nội STANDARD produces server-derived address data and LOCAL fee")
    void prepareShipping_shouldReturnLocalStandardInfo_forOwnedHaNoiAddress() {
        stubActiveCustomer();
        stubOwnedAddress(sampleAddress("Hà Nội"));

        final ShippingCheckoutInfo result = service.prepareShipping(
                SUBJECT,
                new ShippingSelectionRequest(ADDRESS_ID, ShippingMethod.STANDARD));

        assertThat(result.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(result.addressId()).isEqualTo(ADDRESS_ID);
        assertThat(result.recipientName()).isEqualTo("Nguyễn Văn A");
        assertThat(result.phone()).isEqualTo("0987654321");
        assertThat(result.line1()).isEqualTo("123 Lê Lợi");
        assertThat(result.ward()).isEqualTo("Phường Bến Nghé");
        assertThat(result.district()).isEqualTo("Quận 1");
        assertThat(result.province()).isEqualTo("Hà Nội");
        assertThat(result.shippingMethod()).isEqualTo(ShippingMethod.STANDARD);
        assertThat(result.region()).isEqualTo(ShippingRegion.LOCAL);
        assertThat(result.shippingFee()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("Bắc Ninh EXPRESS produces NEARBY fee")
    void prepareShipping_shouldReturnNearbyExpressInfo_forOwnedBacNinhAddress() {
        stubActiveCustomer();
        stubOwnedAddress(sampleAddress("Bắc Ninh"));

        final ShippingCheckoutInfo result = service.prepareShipping(
                SUBJECT,
                new ShippingSelectionRequest(ADDRESS_ID, ShippingMethod.EXPRESS));

        assertThat(result.addressId()).isEqualTo(ADDRESS_ID);
        assertThat(result.province()).isEqualTo("Bắc Ninh");
        assertThat(result.shippingMethod()).isEqualTo(ShippingMethod.EXPRESS);
        assertThat(result.region()).isEqualTo(ShippingRegion.NEARBY);
        assertThat(result.shippingFee()).isEqualByComparingTo("40000");
    }

    @Test
    @DisplayName("another customer's address is rejected before fee calculation")
    void prepareShipping_shouldRejectAddressNotOwnedByAuthenticatedCustomer() {
        stubActiveCustomer();
        when(addressRepository.findByAddressIdAndCustomerId(ADDRESS_ID, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepareShipping(
                SUBJECT,
                new ShippingSelectionRequest(ADDRESS_ID, ShippingMethod.STANDARD)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Address not found with id: " + ADDRESS_ID);

        verify(addressRepository).findByAddressIdAndCustomerId(ADDRESS_ID, CUSTOMER_ID);
        verify(addressRepository, never()).findById(ADDRESS_ID);
        verify(shippingFeeService, never()).calculate(any());
    }

    @Test
    @DisplayName("unsupported province is rejected by ATS-33")
    void prepareShipping_shouldRejectUnsupportedProvince() {
        stubActiveCustomer();
        stubOwnedAddress(sampleAddress("Atlantis"));

        assertThatThrownBy(() -> service.prepareShipping(
                SUBJECT,
                new ShippingSelectionRequest(ADDRESS_ID, ShippingMethod.STANDARD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Province is not supported: Atlantis");
    }

    @Test
    @DisplayName("EXPRESS is rejected for an OTHER region")
    void prepareShipping_shouldRejectExpressForOtherRegion() {
        stubActiveCustomer();
        stubOwnedAddress(sampleAddress("Đà Nẵng"));

        assertThatThrownBy(() -> service.prepareShipping(
                SUBJECT,
                new ShippingSelectionRequest(ADDRESS_ID, ShippingMethod.EXPRESS)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EXPRESS shipping is not supported for region OTHER");
    }

    private void stubActiveCustomer() {
        when(customerRepository.findByKeycloakUserId(SUBJECT))
                .thenReturn(Optional.of(Customer.builder()
                        .customerId(CUSTOMER_ID)
                        .keycloakUserId(SUBJECT)
                        .status(Customer.CustomerStatus.ACTIVE)
                        .build()));
    }

    private void stubOwnedAddress(final Address address) {
        when(addressRepository.findByAddressIdAndCustomerId(ADDRESS_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(address));
    }

    private static Address sampleAddress(final String province) {
        return Address.builder()
                .addressId(ADDRESS_ID)
                .customerId(CUSTOMER_ID)
                .recipientName("Nguyễn Văn A")
                .phone("0987654321")
                .line1("123 Lê Lợi")
                .ward("Phường Bến Nghé")
                .district("Quận 1")
                .province(province)
                .defaultAddress(true)
                .build();
    }

    private static ShippingFeeProperties configuredProperties() {
        final ShippingFeeProperties properties = new ShippingFeeProperties();
        final Map<ShippingRegion, Map<ShippingMethod, BigDecimal>> fees =
                new EnumMap<>(ShippingRegion.class);

        fees.put(ShippingRegion.LOCAL, fees(
                Map.entry(ShippingMethod.STANDARD, new BigDecimal("10000")),
                Map.entry(ShippingMethod.EXPRESS, new BigDecimal("25000"))));
        fees.put(ShippingRegion.NEARBY, fees(
                Map.entry(ShippingMethod.STANDARD, new BigDecimal("20000")),
                Map.entry(ShippingMethod.EXPRESS, new BigDecimal("40000"))));
        fees.put(ShippingRegion.OTHER, fees(
                Map.entry(ShippingMethod.STANDARD, new BigDecimal("30000"))));

        properties.setFees(fees);
        return properties;
    }

    @SafeVarargs
    private static Map<ShippingMethod, BigDecimal> fees(
            final Map.Entry<ShippingMethod, BigDecimal>... entries) {
        final Map<ShippingMethod, BigDecimal> fees = new EnumMap<>(ShippingMethod.class);
        for (Map.Entry<ShippingMethod, BigDecimal> entry : entries) {
            fees.put(entry.getKey(), entry.getValue());
        }
        return fees;
    }
}
