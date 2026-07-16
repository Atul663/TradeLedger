package com.example.tradeLedger.exception;

public class TokenRenewalException extends RuntimeException {
    public TokenRenewalException(String message, Throwable cause) { super(message, cause); }
    public TokenRenewalException(String message) { super(message); }
}