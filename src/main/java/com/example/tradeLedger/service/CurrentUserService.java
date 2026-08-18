package com.example.tradeLedger.service;

import com.example.tradeLedger.entity.User;

/**
 * Bridges the authenticated principal to the control-plane {@code users} row.
 *
 * The existing authentication flow is untouched: it keeps issuing JWTs whose
 * subject is the user's email and keeps storing its own state in
 * {@code google_auth_tokens}. This service only answers the question the strategy
 * module needs answered - "which users.id does this caller correspond to" -
 * because trading_accounts, subscriptions and user_risk_limits all key off
 * {@code users(id)}, which is a uuid the auth tables do not carry.
 */
public interface CurrentUserService {

    /**
     * The control-plane user for an authenticated email, provisioning the row on
     * first use.
     *
     * @throws com.example.tradeLedger.exception.ResourceConflictException if the
     *         account is suspended or closed
     */
    User require(String email);
}
