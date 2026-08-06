package com.example.banhangtructuyen.domain.exception;

public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(final String message) {
        super(message);
    }
}
