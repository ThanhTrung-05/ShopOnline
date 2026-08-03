package com.example.banhangtructuyen.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        Instant timestamp,
        String traceId
) {

    public static <T> ApiResponse<T> success(final T data) {
        return new ApiResponse<>(true, data, null, Instant.now(), UUID.randomUUID().toString());
    }

    public static <T> ApiResponse<T> error(final String message) {
        return new ApiResponse<>(false, null, message, Instant.now(), UUID.randomUUID().toString());
    }
}
