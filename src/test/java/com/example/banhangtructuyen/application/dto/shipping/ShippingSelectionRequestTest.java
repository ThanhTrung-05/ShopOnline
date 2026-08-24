package com.example.banhangtructuyen.application.dto.shipping;

import com.example.banhangtructuyen.domain.model.ShippingMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ShippingSelectionRequest contract")
class ShippingSelectionRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @EnumSource(ShippingMethod.class)
    @DisplayName("STANDARD and EXPRESS are valid request values")
    void validate_shouldAcceptEveryMvpShippingMethod(final ShippingMethod shippingMethod) {
        final Set<ConstraintViolation<ShippingSelectionRequest>> violations = validator.validate(
                new ShippingSelectionRequest(42L, shippingMethod));

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    @DisplayName("addressId is required and must be greater than zero")
    void validate_shouldRejectInvalidAddressId(final Long addressId) {
        final Set<ConstraintViolation<ShippingSelectionRequest>> violations = validator.validate(
                new ShippingSelectionRequest(addressId, ShippingMethod.STANDARD));

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("addressId");
    }

    @Test
    @DisplayName("shippingMethod is required")
    void validate_shouldRejectNullShippingMethod() {
        final Set<ConstraintViolation<ShippingSelectionRequest>> violations = validator.validate(
                new ShippingSelectionRequest(42L, null));

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("Shipping method is required");
    }

    @Test
    @DisplayName("unknown shipping method string is rejected during JSON deserialization")
    void deserialize_shouldRejectUnknownShippingMethod() {
        final String json = "{\"addressId\":42,\"shippingMethod\":\"SAME_DAY\"}";

        assertThatThrownBy(() -> objectMapper.readValue(json, ShippingSelectionRequest.class))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("SAME_DAY");
    }

    @Test
    @DisplayName("public request contains only addressId and shippingMethod")
    void serialize_shouldContainOnlyClientSelectableFields() {
        final var json = objectMapper.valueToTree(
                new ShippingSelectionRequest(42L, ShippingMethod.EXPRESS));

        assertThat(json.size()).isEqualTo(2);
        assertThat(json.get("addressId").asLong()).isEqualTo(42L);
        assertThat(json.get("shippingMethod").asText()).isEqualTo("EXPRESS");
        assertThat(json.has("customerId")).isFalse();
        assertThat(json.has("province")).isFalse();
        assertThat(json.has("shippingFee")).isFalse();
    }
}
