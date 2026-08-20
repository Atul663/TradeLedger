package com.example.tradeLedger.exception;

/**
 * The credential cipher could not do its job: no key configured, the wrong key,
 * or a stored value that is not in the expected format.
 *
 * Distinct from a validation error because none of it is the caller's fault and
 * none of it is fixable by changing the request - it is server configuration.
 * Distinct from a plain {@code IllegalStateException} because that one lands in
 * the catch-all handler, which replaces the message with "Unexpected error
 * processing the request" and leaves the operator reading stack traces to find
 * out that an environment variable is missing.
 *
 * The message is safe to return: it names the variable, never a key or a secret.
 */
public class CredentialEncryptionException extends RuntimeException {

    public CredentialEncryptionException(String message) {
        super(message);
    }

    public CredentialEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
