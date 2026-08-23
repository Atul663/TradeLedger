package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.StrategyTemplateDetailResponse;
import com.example.tradeLedger.dto.StrategyTemplateRequest;

import java.util.List;
import java.util.UUID;

/**
 * CRUD over {@code strategy_templates} - the platform catalog of strategy logic.
 *
 * Templates are platform-level rows, not per-user ones, and deliberately so: two
 * users running the same logic must share the template for their computations to
 * dedup. What a user owns is a {@link UserStrategyService strategy} built from
 * one. The protection here is therefore not row ownership but the
 * {@code is_system} flag - seeded templates cannot be edited or deleted through
 * the API.
 *
 * A template carries logic only. It has no knob definitions to manage: the
 * indicators its rule tree names declare their own parameters, and every other
 * setting is a fixed column on {@code user_strategies}.
 */
public interface StrategyTemplateService {

    /**
     * @param active null for all, true/false to filter on is_active
     * @param search optional case-insensitive fragment of the template name
     */
    List<StrategyTemplateDetailResponse> list(Boolean active, String search);

    StrategyTemplateDetailResponse get(UUID id);

    StrategyTemplateDetailResponse getByName(String name);

    StrategyTemplateDetailResponse create(StrategyTemplateRequest request);

    StrategyTemplateDetailResponse update(UUID id, StrategyTemplateRequest request);

    /**
     * Hard delete. Refused while any shared computation or any user strategy still
     * references the template - deactivate it instead
     * ({@code PUT /api/v1/strategy-templates/{id}} with active=false).
     */
    void delete(UUID id);
}
