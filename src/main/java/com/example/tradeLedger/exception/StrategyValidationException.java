package com.example.tradeLedger.exception;

import java.util.List;

/**
 * Raised when a submitted configuration breaks a rule the schema or the columns
 * declare - a strike depth that does not match its moneyness, an indicator value
 * out of range, a leg that contradicts the derivative.
 *
 * Carries every problem found rather than the first, so a form with three
 * mistakes reports three.
 */
public class StrategyValidationException extends RuntimeException {

    private final List<String> errors;

    public StrategyValidationException(String message) {
        super(message);
        this.errors = List.of(message);
    }

    public StrategyValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
