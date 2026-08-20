package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.BrokerRequest;
import com.example.tradeLedger.dto.BrokerResponse;

import java.util.List;
import java.util.UUID;

/**
 * The {@code brokers} catalog: who the platform can route orders through.
 *
 * <p><b>This is shared master data, not one user's data.</b> Every
 * {@code user_brokers} row points at a row here, so a change is visible to every
 * user at once. That is why {@code code} is immutable and why delete is refused
 * while any setup references it - deactivation is the safe way to retire one.
 *
 * <p>The catalog is deliberately separate from {@code exchanges}. An exchange is
 * the venue an instrument trades on and is what {@code symbols} hangs off; a
 * broker is the API the platform authenticates against to reach it.
 */
public interface BrokerService {

    /** @param activeOnly hides brokers the platform has retired */
    List<BrokerResponse> list(boolean activeOnly);

    BrokerResponse get(UUID id);

    /** Codes are unique, so this is a second natural key for the adapters. */
    BrokerResponse getByCode(String code);

    BrokerResponse create(BrokerRequest request);

    /**
     * Add several at once, all or nothing.
     *
     * The batch is checked for duplicate codes before anything is written: a
     * clash caught by the unique index halfway through would otherwise leave the
     * earlier rows committed and the caller guessing which landed.
     */
    List<BrokerResponse> createAll(List<BrokerRequest> requests);

    /** Partial. {@code code} cannot change - adapters and setups are keyed on it. */
    BrokerResponse update(UUID id, BrokerRequest request);

    /** Refused while any user's setup still points at it; deactivate instead. */
    void delete(UUID id);
}
