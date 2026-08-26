package com.example.tradeLedger.exception;

import com.example.tradeLedger.controller.BrokerController;
import com.example.tradeLedger.controller.IndicatorController;
import com.example.tradeLedger.controller.ReferenceDataController;
import com.example.tradeLedger.controller.StrategyTemplateController;
import com.example.tradeLedger.controller.SharedStrategyConfigController;
import com.example.tradeLedger.controller.StrategySubscriptionController;
import com.example.tradeLedger.controller.TradingAccountController;
import com.example.tradeLedger.controller.UserBrokerController;
import com.example.tradeLedger.controller.UserStrategyController;
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

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Error mapping for the strategy-module endpoints.
 *
 * Scoped by {@code assignableTypes} rather than applied globally on purpose: the
 * authentication controllers already build their own responses, and
 * quietly changing how their failures render would be a behaviour change to code
 * this work is not meant to touch.
 */
@RestControllerAdvice(assignableTypes = {
        StrategyTemplateController.class,
        IndicatorController.class,
        SharedStrategyConfigController.class,
        StrategySubscriptionController.class,
        TradingAccountController.class,
        ReferenceDataController.class,
        UserStrategyController.class,
        UserBrokerController.class,
        BrokerController.class
})
public class StrategyApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(StrategyApiExceptionHandler.class);

    private static final Pattern CONSTRAINT_QUOTED =
            Pattern.compile("constraint \"([A-Za-z0-9_]+)\"");

    /** Hibernate's own wrapper does not quote it: {@code could not execute statement [ck_...]}. */
    private static final Pattern CONSTRAINT_BARE =
            Pattern.compile("((?:uq|ck|fk)_[A-Za-z0-9_]+)");

    /**
     * Keyed by constraint name, lower-cased - the platform's own names, declared on
     * the entities and in control-plane-schema.sql. A constraint absent here is
     * reported by name rather than described, which is still more than "existing
     * data" and is what tells us to add a line.
     */
    private static final Map<String, String> CONFLICT_MESSAGES = Map.of(
            "uq_user_strategies_user_name",
            "You already have a strategy with that name - send a different name",

            "uq_user_strategy_indicators",
            "That indicator is already tuned on this strategy at the same slot",

            "uq_shared_configs_dedup",
            "Another request just created the same indicator computation - retry this one",

            "uq_user_strategy_subs_strategy_account",
            "This strategy is already deployed on that trading account - edit the deployment instead",

            "uq_symbols_exchange_symbol",
            "That symbol already exists on that exchange",

            "uq_taccounts_broker_account",
            "That account already exists for that broker",

            "uq_user_brokers_user_label",
            "You already have a broker connection with that label",

            "uq_fixed_parameters_name",
            "A fixed parameter with that name already exists",

            "ck_user_strategies_ce_strike",
            "ceMoneyness and ceStrikeOffset disagree - ATM takes offset 0, ITM and OTM take 1..15",

            "ck_user_strategies_pe_strike",
            "peMoneyness and peStrikeOffset disagree - ATM takes offset 0, ITM and OTM take 1..15");

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
     * A unique, foreign-key or CHECK violation that slipped past the service-level
     * check - a concurrent request, most often. It is the caller's collision either
     * way.
     *
     * The constraint name is carried into the response rather than kept in the log.
     * "Request conflicts with existing data" is true of every one of these, which
     * makes it useless to a caller holding a payload and to anyone reading a bug
     * report: the same sentence covers a duplicate strategy name, a redeployed
     * account and a strike depth the engine has no strike for. The names below are
     * the platform's own, not the database's internals, and each maps to something
     * the caller can act on.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException e) {
        String cause = e.getMostSpecificCause().getMessage();
        String constraint = constraintName(cause);
        log.warn("409 CONFLICT - database constraint violated: {}", cause);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(conflictMessage(constraint)));
    }

    /**
     * The constraint out of a driver message, whichever way the driver phrases it -
     * PostgreSQL quotes it ({@code violates unique constraint "uq_..."}), Hibernate's
     * own wrapper does not ({@code could not execute statement [ck_...]}).
     */
    private String constraintName(String cause) {
        if (cause == null) {
            return null;
        }
        Matcher quoted = CONSTRAINT_QUOTED.matcher(cause);
        if (quoted.find()) {
            return quoted.group(1);
        }
        Matcher bare = CONSTRAINT_BARE.matcher(cause);
        return bare.find() ? bare.group(1) : null;
    }

    private String conflictMessage(String constraint) {
        if (constraint == null) {
            return "Request conflicts with existing data";
        }
        String known = CONFLICT_MESSAGES.get(constraint.toLowerCase(Locale.ROOT));
        return known != null
                ? known
                // Unmapped: name it rather than swallow it, so the next report of
                // this says which rule fired instead of "something conflicted".
                : "Request conflicts with existing data (" + constraint + ")";
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception e) {
        log.warn("400 BAD REQUEST - {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiError.of(shortMessage(e)));
    }

    /**
     * The cipher could not run: no key configured, the wrong key, or a stored
     * value in an unexpected format. 503 rather than 500 because the request
     * itself was fine and will succeed once the server is configured - and
     * because the catch-all below would replace the message with "Unexpected
     * error", leaving an operator to find a missing environment variable by
     * reading stack traces.
     *
     * The message names the variable, never a key or a secret.
     */
    @ExceptionHandler(CredentialEncryptionException.class)
    public ResponseEntity<ApiError> handleCredentialEncryption(CredentialEncryptionException e) {
        log.error("503 SERVICE UNAVAILABLE - credential encryption: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiError.of(e.getMessage()));
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
