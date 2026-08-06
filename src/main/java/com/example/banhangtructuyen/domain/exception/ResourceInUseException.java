package com.example.banhangtructuyen.domain.exception;

public class ResourceInUseException extends RuntimeException {

    public ResourceInUseException(final String message) {
        super(message);
    }
}
