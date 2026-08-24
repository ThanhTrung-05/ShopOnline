package com.example.banhangtructuyen.domain.exception;

import com.example.banhangtructuyen.domain.model.Customer;

public class CustomerAccountNotActiveException extends RuntimeException {

    public CustomerAccountNotActiveException(final Customer.CustomerStatus status) {
        super("Customer account is not active (status: " + status + ")");
    }
}
