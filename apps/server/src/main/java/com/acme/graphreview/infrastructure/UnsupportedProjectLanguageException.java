package com.acme.graphreview.infrastructure;

public class UnsupportedProjectLanguageException extends ProjectValidationException {

    public UnsupportedProjectLanguageException(String message) {
        super(message);
    }
}
