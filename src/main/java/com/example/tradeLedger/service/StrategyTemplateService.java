package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.StrategyTemplateDetailResponse;
import com.example.tradeLedger.dto.StrategyParamDefinitionRequest;
import com.example.tradeLedger.dto.StrategyParamDefinitionResponse;
import com.example.tradeLedger.dto.StrategyTemplateRequest;

import java.util.List;
import java.util.UUID;

/**
 * CRUD over {@code strategy_templates} and their {@code strategy_param_definitions}.
 *
 * Strategies are platform-level rows, not per-user ones - the schema gives
 * {@code strategy_templates} no owner column, and that is deliberate: two users running
 * the same strategy must share the template so their instances can dedup. What
 * a user owns is a {@link StrategySubscriptionService subscription}. The protection here
 * is therefore not row ownership but the {@code is_system} flag: seeded
 * strategies cannot be edited or deleted through the API.
 */
public interface StrategyTemplateService {

    /**
     * @param active null for all, true/false to filter on is_active
     * @param search optional case-insensitive fragment of the strategy name
     */
    List<StrategyTemplateDetailResponse> list(Boolean active, String search);

    StrategyTemplateDetailResponse get(UUID id);

    StrategyTemplateDetailResponse getByName(String name);

    StrategyTemplateDetailResponse create(StrategyTemplateRequest request);

    /** Full update; {@code params}, when present, replaces the knob set. */
    StrategyTemplateDetailResponse update(UUID id, StrategyTemplateRequest request);

    /**
     * Hard delete, cascading to the strategy's knob definitions.
     * Refused while any strategy instance still references the strategy -
     * deactivate it instead ({@code PUT /api/v1/strategy-templates/{id}} with active=false).
     */
    void delete(UUID id);

    // ------------------------------------------------- indicator parameters

    List<StrategyParamDefinitionResponse> listParams(UUID strategyId);

    StrategyParamDefinitionResponse addParam(UUID strategyId, StrategyParamDefinitionRequest request);

    StrategyParamDefinitionResponse updateParam(UUID strategyId, Long paramId, StrategyParamDefinitionRequest request);

    void deleteParam(UUID strategyId, Long paramId);
}
