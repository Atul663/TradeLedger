package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.IndicatorPlanResponse;
import com.example.tradeLedger.dto.StrategyInstanceResponse;
import com.example.tradeLedger.entity.Strategy;
import com.example.tradeLedger.entity.StrategyInstance;
import com.example.tradeLedger.entity.Symbol;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The dedup unit: {@code strategy_instances} rows are immutable and
 * content-addressed, so identical math always lands on one row no matter how
 * many users ask for it.
 *
 * Instances are never created directly through the API. They appear as a side
 * effect of a subscription being created or repointed, which is what keeps them
 * shared rather than owned - and why there is no create/update/delete here.
 */
public interface StrategyInstanceService {

    /** An instance plus whether this call is what brought it into existence. */
    record Resolution(StrategyInstance instance, boolean created) {
    }

    /**
     * Finds the instance for this exact configuration, creating it only if nobody
     * already runs that math. Concurrent callers converge on one row.
     */
    Resolution resolveOrCreate(Strategy strategy, Symbol symbol, String timeframe,
                               Map<String, Object> signalParams);

    /** Refcounting: an instance with no active subscribers stops being computed. */
    void retireIfOrphaned(UUID instanceId);

    void reviveIfRetired(StrategyInstance instance);

    /** @param status 'active' / 'retired', or null for every instance */
    List<StrategyInstanceResponse> list(String status);

    StrategyInstanceResponse get(UUID id);

    /**
     * Platform-wide dedup report: active subscriptions vs the distinct indicator
     * computations they actually cost. Carries counts and indicator fingerprints
     * only - no user, account or subscription identifiers.
     */
    IndicatorPlanResponse indicatorPlan();
}
