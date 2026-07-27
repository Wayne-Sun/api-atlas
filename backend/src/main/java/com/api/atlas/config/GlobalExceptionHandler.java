package com.api.atlas.config;

import com.api.atlas.model.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        logger.error("Validation failed: {}", ex.getMessage(), ex);
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(
                e -> errors.put(e.getField(), e.getDefaultMessage())
        );
        return R.error(400, "Validation failed", errors);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public R<Void> handleNotFound(NoSuchElementException ex) {
        logger.error("Resource not found: {}", ex.getMessage(), ex);
        return R.error(404, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> handleBadRequest(IllegalArgumentException ex) {
        logger.error("Bad request: {}", ex.getMessage(), ex);
        return R.error(400, ex.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public R<Void> handleDuplicateKey(DuplicateKeyException ex) {
        logger.error("Duplicate key: {}", ex.getMessage(), ex);
        return R.error(409, "Resource already exists");
    }

    @ExceptionHandler(IllegalStateException.class)
    public R<Void> handleConflict(IllegalStateException ex) {
        logger.error("Conflict: {}", ex.getMessage(), ex);
        return R.error(409, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleGeneral(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        R<Void> body = R.error(500, "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
