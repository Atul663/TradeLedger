package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.StrategySubscriptionRequest;
import com.example.tradeLedger.dto.StrategySubscriptionResponse;
import com.example.tradeLedger.service.StrategySubscriptionService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs one deployment in a transaction of its own.
 *
 * Deploying to five brokers must not be all-or-nothing. One account that already
 * runs this strategy is a 409 for that account and nothing at all for the other
 * four - but a failed flush inside a shared transaction marks the WHOLE
 * transaction rollback-only, so catching the exception in a loop would only move
 * the failure to commit time and take the successes down with it.
 *
 * {@code REQUIRES_NEW} suspends the caller transaction and gives each account a
 * clean one. When it fails, only that one rolls back, and the exception reaches
 * the loop with the caller transaction still healthy.
 *
 * A separate bean rather than a method on the service because Spring transaction
 * advice is a proxy: a self-call would bypass the annotation and quietly restore
 * the behaviour this class exists to avoid.
 */
@Component
public class SubscriptionFanOut {

    private final StrategySubscriptionService subscriptions;

    public SubscriptionFanOut(StrategySubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StrategySubscriptionResponse deployOne(String email, StrategySubscriptionRequest request) {
        return subscriptions.create(email, request);
    }
}
