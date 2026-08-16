package com.example.tradeLedger.exception;

import com.example.tradeLedger.controller.IndicatorController;
import com.example.tradeLedger.controller.ParameterController;
import com.example.tradeLedger.controller.ReferenceDataController;
import com.example.tradeLedger.controller.StrategyDefinitionController;
import com.example.tradeLedger.controller.StrategyInstanceController;
import com.example.tradeLedger.controller.SubscriptionController;
import com.example.tradeLedger.controller.TradingAccountController;
import com.example.tradeLedger.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Error mapping for the strategy-module endpoints.
 *
 * Scoped by {@code assignableTypes} rather than applied globally on purpose: the
 * authentication and Dhan controllers already build their own responses, and
 * quietly changing how their failures render would be a behaviour change to code
 * this work is not meant to touch.
 */
@RestControllerAdvice(assignableTypes = {
        StrategyDefinitionController.class,
        IndicatorController.class,
        StrategyInstanceController.class,
        ParameterController.class,
        SubscriptionController.class,
        TradingAccountController.class,
        ReferenceDataController.class
})
public class StrategyApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(StrategyApiExceptionHandler.class);

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<ApiError> handleUnauthenticated(UnauthenticatedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of(e.getMessage()));
    }

    @ExceptionHandler(StrategyValidationException.class)
    public ResponseEntity<ApiError> handleValidation(StrategyValidationException e) {
        return ResponseEntity.badRequest().body(ApiError.of(e.getErrors()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(e.getMessage()));
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ResourceConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(e.getMessage()));
    }

    /**
     * A unique or foreign-key violation that slipped past the service-level check -
     * a concurrent request, most often. It is the caller's collision either way.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("409 CONFLICT - database constraint violated: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("Request conflicts with existing data"));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception e) {
        log.warn("400 BAD REQUEST - {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiError.of(shortMessage(e)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("500 INTERNAL SERVER ERROR", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("Unexpected error processing the request"));
    }

    /** Parser exceptions carry stack detail in getMessage(); only the first line is useful to a caller. */
    private String shortMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "Malformed request";
        }
        int newline = message.indexOf('\n');
        return newline > 0 ? message.substring(0, newline) : message;
    }
}
