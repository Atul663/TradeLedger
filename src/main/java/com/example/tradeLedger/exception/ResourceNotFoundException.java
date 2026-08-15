package com.example.tradeLedger.exception;

/** Raised when a control-plane row does not exist, or is not visible to the caller. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String type, Object id) {
        return new ResourceNotFoundException(type + " not found: " + id);
    }
}
