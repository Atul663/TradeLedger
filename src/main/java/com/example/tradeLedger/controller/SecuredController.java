package com.example.tradeLedger.controller;

import com.example.tradeLedger.exception.UnauthenticatedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Shared access to the authenticated caller for the strategy-module controllers.
 *
 * The authentication mechanism is untouched: {@code JwtFilter} still validates
 * the Bearer token and puts the email in the security context as the principal.
 * This only reads it, in one place, instead of each controller repeating the
 * same null-and-anonymous dance.
 */
public abstract class SecuredController {

    private static final String ANONYMOUS = "anonymousUser";

    /**
     * The caller's email - the JWT subject.
     *
     * @throws UnauthenticatedException when no valid token was presented
     */
    protected String currentEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthenticatedException();
        }
        Object principal = auth.getPrincipal();
        if (principal == null || ANONYMOUS.equals(principal)) {
            throw new UnauthenticatedException();
        }
        String email = principal.toString();
        if (email.isBlank()) {
            throw new UnauthenticatedException();
        }
        return email;
    }
}
