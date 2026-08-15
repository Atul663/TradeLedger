package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.SubscriptionRequest;
import com.example.tradeLedger.dto.SubscriptionResponse;
import com.example.tradeLedger.dto.SubscriptionUpdateRequest;

import java.util.List;
import java.util.UUID;

/**
 * The user-facing half of the strategy module: a user's own configurations.
 *
 * Every method takes the authenticated caller's email and scopes its work to
 * that user's rows. A subscription belonging to someone else is not "forbidden",
 * it is simply not found - the ownership filter is part of the query, not a
 * check applied after loading.
 */
public interface SubscriptionService {

    /** Only the caller's own subscriptions. */
    List<SubscriptionResponse> list(String email);

    SubscriptionResponse get(String email, UUID id);

    /**
     * Subscribe the caller to a strategy with their own parameters. Signal-scope
     * params resolve to a shared strategy instance; execution-scope params stay
     * on this row.
     */
    SubscriptionResponse create(String email, SubscriptionRequest request);

    /**
     * Partial update. A signal-param change never mutates an instance: it
     * repoints this subscription at the instance for the resulting config and
     * retires the previous one once its last active subscriber leaves.
     */
    SubscriptionResponse update(String email, UUID id, SubscriptionUpdateRequest request);

    void delete(String email, UUID id);
}
