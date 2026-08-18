package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.StrategySubscriptionRequest;
import com.example.tradeLedger.dto.StrategySubscriptionResponse;
import com.example.tradeLedger.dto.StrategySubscriptionUpdateRequest;

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
public interface StrategySubscriptionService {

    /** Only the caller's own subscriptions. */
    List<StrategySubscriptionResponse> list(String email);

    StrategySubscriptionResponse get(String email, UUID id);

    /**
     * Subscribe the caller to a strategy with their own parameters. Signal-scope
     * params resolve to a shared strategy instance; execution-scope params stay
     * on this row.
     */
    StrategySubscriptionResponse create(String email, StrategySubscriptionRequest request);

    /**
     * Partial update. A signal-param change never mutates an instance: it
     * repoints this subscription at the instance for the resulting config and
     * retires the previous one once its last active subscriber leaves.
     */
    StrategySubscriptionResponse update(String email, UUID id, StrategySubscriptionUpdateRequest request);

    void delete(String email, UUID id);
}
