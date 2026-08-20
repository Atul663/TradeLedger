package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.BrokerCredentialRequest;
import com.example.tradeLedger.dto.BrokerCredentialResponse;

import java.util.UUID;

/**
 * The credentials behind a broker setup, and any per-account override of them.
 *
 * Split out of the setup and account services on purpose. Everything here touches
 * a plaintext secret at some point in its body, and keeping that in one small
 * class is what makes "where can a secret be read?" a question with a short
 * answer.
 */
public interface BrokerCredentialService {

    // ------------------------------------------------------------ setup level

    /** Masked. No secret is returned, including to the owner. */
    BrokerCredentialResponse getForSetup(String email, UUID userBrokerId);

    /**
     * Create or update the setup's credentials - the ones every account under it
     * inherits. Absent and null fields are left unchanged; an empty string clears
     * one. Stamps {@code rotated_at} when a secret changes.
     */
    BrokerCredentialResponse upsertForSetup(String email, UUID userBrokerId,
                                            BrokerCredentialRequest request);

    /** Removes the setup's credentials. Accounts with their own keep working. */
    void deleteForSetup(String email, UUID userBrokerId);

    // ---------------------------------------------------------- account level

    /**
     * One account's effective view: the setup's values, with this account's
     * overrides applied field by field, and {@code overriddenFields} saying which
     * were its own.
     */
    BrokerCredentialResponse getForAccount(String email, UUID tradingAccountId);

    /**
     * Write an override for one account. Only the fields sent become its own;
     * everything else keeps inheriting. Clearing the last one deletes the
     * override row, so the account goes back to inheriting everything.
     */
    BrokerCredentialResponse upsertForAccount(String email, UUID tradingAccountId,
                                              BrokerCredentialRequest request);

    /** Drops the override; the account falls back to the setup's credentials. */
    void deleteForAccount(String email, UUID tradingAccountId);

    // --------------------------------------------------------------- internal

    /**
     * Decrypted and merged, for broker adapters and the execution path only -
     * never for a controller. Not scoped by user: by the time an order is being
     * placed the ownership check has already happened upstream, and the engine
     * has no email to pass.
     */
    BrokerCredentials resolve(UUID tradingAccountId);
}
