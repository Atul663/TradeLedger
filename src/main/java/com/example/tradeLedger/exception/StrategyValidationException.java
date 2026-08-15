package com.example.tradeLedger.exception;

import java.util.List;

/** Raised when submitted strategy parameters violate their strategy_param_def rules. */
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
