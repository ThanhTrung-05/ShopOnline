package com.example.banhangtructuyen.domain.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(final String resourceName, final Object identifier) {
        super(resourceName + " not found with id: " + identifier);
    }
}
