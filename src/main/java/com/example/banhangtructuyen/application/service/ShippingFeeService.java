package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.shipping.ShippingFeeResult;
import com.example.banhangtructuyen.application.dto.shipping.ValidatedShippingSelection;

/**
 * Calculates a transient shipping fee from ATS-32 validated shipping data.
 */
public interface ShippingFeeService {

    ShippingFeeResult calculate(ValidatedShippingSelection selection);
}
