package com.example.tradeLedger.dto;

/**
 * What the one-call wizard built: everything the UI needs to move on, without a
 * follow-up GET.
 *
 * {@code account} is null when none was requested, {@code credentials} when no
 * key was sent - so a caller can tell "not asked for" from "failed", which a flat
 * response would blur.
 */
public record BrokerSetupResponse(
        UserBrokerResponse broker,
        TradingAccountResponse account,
        BrokerCredentialResponse credentials) {
}
