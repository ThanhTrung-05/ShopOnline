package com.example.banhangtructuyen.presentation.exception;

import com.example.banhangtructuyen.application.dto.ApiResponse;
<<<<<<< HEAD
import com.example.banhangtructuyen.domain.exception.DuplicateResourceException;
import com.example.banhangtructuyen.domain.exception.EmailAlreadyExistsException;
import com.example.banhangtructuyen.domain.exception.InvalidCredentialsException;
=======
import com.example.banhangtructuyen.domain.exception.CustomerAccountNotActiveException;
import com.example.banhangtructuyen.domain.exception.DuplicateResourceException;
import com.example.banhangtructuyen.domain.exception.EmailAlreadyExistsException;
import com.example.banhangtructuyen.domain.exception.KeycloakProvisioningException;
import com.example.banhangtructuyen.domain.exception.RegistrationProvisioningException;
import com.example.banhangtructuyen.domain.exception.ResourceInUseException;
>>>>>>> 2e744a0ec2370b770aff69565021da8d47088517
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class                                            GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(final ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(CustomerAccountNotActiveException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomerAccountNotActive(
            final CustomerAccountNotActiveException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailAlreadyExists(final EmailAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

<<<<<<< HEAD
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(final InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

=======
>>>>>>> 2e744a0ec2370b770aff69565021da8d47088517
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(final DuplicateResourceException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

<<<<<<< HEAD
=======
    @ExceptionHandler(ResourceInUseException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceInUse(final ResourceInUseException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(KeycloakProvisioningException.class)
    public ResponseEntity<ApiResponse<Void>> handleKeycloakProvisioning(final KeycloakProvisioningException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("Unable to complete registration. Please try again later."));
    }

    @ExceptionHandler(RegistrationProvisioningException.class)
    public ResponseEntity<ApiResponse<Void>> handleRegistrationProvisioning(
            final RegistrationProvisioningException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Registration failed due to an internal provisioning error"));
    }

>>>>>>> 2e744a0ec2370b770aff69565021da8d47088517
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(final DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Request conflicts with existing data"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(final MethodArgumentNotValidException ex) {
        final String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(final ConstraintViolationException ex) {
        final String fallbackMessage = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Validation failed"
                : ex.getMessage();
        final String message = ex.getConstraintViolations() == null
                ? fallbackMessage
                : ex.getConstraintViolations().stream()
                        .findFirst()
                        .map(violation -> violation.getMessage())
                        .orElse(fallbackMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message));
    }

<<<<<<< HEAD
    /**
     * ATS-6: Handles invalid VAT rate and other business-rule violations
     * (e.g. category has VAT rate other than 5% or 10%).
     */
=======
>>>>>>> 2e744a0ec2370b770aff69565021da8d47088517
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(final IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }
}
