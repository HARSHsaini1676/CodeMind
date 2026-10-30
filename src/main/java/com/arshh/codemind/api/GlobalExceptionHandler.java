package com.arshh.codemind.api;

import com.arshh.codemind.common.GitCloneException;
import com.arshh.codemind.common.InvalidRepositoryUrlException;
import com.arshh.codemind.common.RepositoryNotFoundException;
import com.arshh.codemind.common.RepositoryNotReadyException;
import com.arshh.codemind.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized exception handler providing clean, student-friendly HTTP status responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 400 Bad Request: URL format or validation failure
    @ExceptionHandler(InvalidRepositoryUrlException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUrl(InvalidRepositoryUrlException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage()));
    }

    // 400 Bad Request: Spring validation constraint violations
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Validation Error", message));
    }

    // 404 Not Found: Repository ID does not exist in store
    @ExceptionHandler(RepositoryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(RepositoryNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage()));
    }

    // 409 Conflict: Repository is still indexing or cloning
    @ExceptionHandler(RepositoryNotReadyException.class)
    public ResponseEntity<ErrorResponse> handleNotReady(RepositoryNotReadyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage()));
    }

    // 500 Internal Server Error: Git clone failure
    @ExceptionHandler(GitCloneException.class)
    public ResponseEntity<ErrorResponse> handleGitCloneError(GitCloneException ex) {
        log.error("Git operation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Git Operation Failed", ex.getMessage()));
    }

    // 500 Internal Server Error: Generic unexpected exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled server exception: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", "An unexpected error occurred"));
    }
}
