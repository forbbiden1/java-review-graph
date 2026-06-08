package com.acme.graphreview.infrastructure;

public class ProjectValidationException extends RuntimeException {

    public ProjectValidationException(String message) {
        super(message);
    }

    public ProjectValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
