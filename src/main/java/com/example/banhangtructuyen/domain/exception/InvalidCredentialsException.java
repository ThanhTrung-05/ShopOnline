package com.example.banhangtructuyen.domain.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }

    public InvalidCredentialsException(final String message) {
        super(message);
    }
}
