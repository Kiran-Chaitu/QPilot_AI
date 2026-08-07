package com.testforge.backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base unchecked exception carrying an HTTP status, thrown anywhere in the
 * service layer and translated to a JSON ApiResponse by {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
