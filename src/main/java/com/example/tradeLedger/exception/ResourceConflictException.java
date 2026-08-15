package com.example.tradeLedger.exception;

/**
 * Raised when a request collides with existing state: a duplicate unique key, an
 * attempt to edit a system-owned row, or a delete blocked by live references.
 */
public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }
}
