package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.StrategyDetailResponse;
import com.example.tradeLedger.dto.StrategyParamDefRequest;
import com.example.tradeLedger.dto.StrategyParamDefResponse;
import com.example.tradeLedger.dto.StrategyRequest;

import java.util.List;
import java.util.UUID;

/**
 * CRUD over {@code strategies} and their {@code strategy_param_defs}.
 *
 * Strategies are platform-level rows, not per-user ones - the schema gives
 * {@code strategies} no owner column, and that is deliberate: two users running
 * the same strategy must share the template so their instances can dedup. What
 * a user owns is a {@link SubscriptionService subscription}. The protection here
 * is therefore not row ownership but the {@code is_system} flag: seeded
 * strategies cannot be edited or deleted through the API.
 */
public interface StrategyDefinitionService {

    /**
     * @param active null for all, true/false to filter on is_active
     * @param search optional case-insensitive fragment of the strategy name
     */
    List<StrategyDetailResponse> list(Boolean active, String search);

    StrategyDetailResponse get(UUID id);

    StrategyDetailResponse getByName(String name);

    StrategyDetailResponse create(StrategyRequest request);

    /** Full update; {@code params}, when present, replaces the knob set. */
    StrategyDetailResponse update(UUID id, StrategyRequest request);

    /**
     * Hard delete, cascading to the strategy's knob definitions.
     * Refused while any strategy instance still references the strategy -
     * deactivate it instead ({@code PUT /api/v1/strategies/{id}} with active=false).
     */
    void delete(UUID id);

    // ------------------------------------------------- indicator parameters

    List<StrategyParamDefResponse> listParams(UUID strategyId);

    StrategyParamDefResponse addParam(UUID strategyId, StrategyParamDefRequest request);

    StrategyParamDefResponse updateParam(UUID strategyId, Long paramId, StrategyParamDefRequest request);

    void deleteParam(UUID strategyId, Long paramId);
}
