package com.example.tradeLedger.exception;

/**
 * Raised when a control-plane endpoint is reached without an authenticated
 * principal in the security context.
 *
 * The security configuration permits {@code /api/v1/**} and lets each controller
 * decide, which is the pattern the existing controllers already follow; this
 * exception is that decision expressed once instead of per method.
 */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("Unauthorized: valid Bearer token required");
    }
}
