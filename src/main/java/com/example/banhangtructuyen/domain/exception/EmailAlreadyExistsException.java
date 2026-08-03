package com.example.banhangtructuyen.domain.exception;

public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(final String email) {
        super("Email already registered: " + email);
    }
}
