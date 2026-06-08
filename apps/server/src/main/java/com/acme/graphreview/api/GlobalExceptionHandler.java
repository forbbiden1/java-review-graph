package com.acme.graphreview.api;

import com.acme.graphreview.infrastructure.ProjectNotFoundException;
import com.acme.graphreview.infrastructure.SnapshotNotFoundException;
import com.acme.graphreview.infrastructure.ProjectValidationException;
import com.acme.graphreview.infrastructure.UnsupportedProjectLanguageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProjectValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(ProjectValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("project_validation_error", exception.getMessage()));
    }

    @ExceptionHandler(UnsupportedProjectLanguageException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedProjectLanguage(UnsupportedProjectLanguageException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("unsupported_project_language", exception.getMessage()));
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ProjectNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("project_not_found", exception.getMessage()));
    }

    @ExceptionHandler(SnapshotNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleSnapshotNotFound(SnapshotNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("snapshot_not_found", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
                .orElse("Request validation failed.");
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("request_validation_error", message));
    }
}
