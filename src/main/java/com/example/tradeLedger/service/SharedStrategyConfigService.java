package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.IndicatorPlanResponse;
import com.example.tradeLedger.dto.SharedStrategyConfigResponse;
import com.example.tradeLedger.entity.StrategyTemplate;
import com.example.tradeLedger.entity.SharedStrategyConfig;
import com.example.tradeLedger.entity.Symbol;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The dedup unit: {@code shared_strategy_configs} rows are immutable and
 * content-addressed, so identical math always lands on one row no matter how
 * many users ask for it.
 *
 * Instances are never created directly through the API. They appear as a side
 * effect of a subscription being created or repointed, which is what keeps them
 * shared rather than owned - and why there is no create/update/delete here.
 */
public interface SharedStrategyConfigService {

    /** An instance plus whether this call is what brought it into existence. */
    record Resolution(SharedStrategyConfig instance, boolean created) {
    }

    /**
     * Finds the instance for this exact configuration, creating it only if nobody
     * already runs that math. Concurrent callers converge on one row.
     */
    Resolution resolveOrCreate(StrategyTemplate strategy, Symbol symbol, String timeframe,
                               Map<String, Object> signalParams);

    /** Refcounting: an instance with no active subscribers stops being computed. */
    void retireIfOrphaned(UUID instanceId);

    void reviveIfRetired(SharedStrategyConfig instance);

    /** @param status 'active' / 'retired', or null for every instance */
    List<SharedStrategyConfigResponse> list(String status);

    SharedStrategyConfigResponse get(UUID id);

    /**
     * Platform-wide dedup report: active subscriptions vs the distinct indicator
     * computations they actually cost. Carries counts and indicator fingerprints
     * only - no user, account or subscription identifiers.
     */
    IndicatorPlanResponse indicatorPlan();
}
