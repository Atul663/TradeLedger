package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.ParameterResponse;
import com.example.tradeLedger.dto.ParameterUsageResponse;

import java.util.List;
import java.util.UUID;

/**
 * Read access to the parameter catalog and the two link tables that hang off it.
 *
 * Between this and {@code GET /api/v1/strategies/{id}}, every direction of the
 * hierarchy is reachable by id: down from a strategy, down from an indicator, and
 * back up from a parameter to everything using it.
 */
public interface ParameterCatalogService {

    /** The whole catalog, or one scope of it. */
    List<ParameterResponse> list(String scope);

    ParameterResponse get(Long id);

    ParameterResponse getByCode(String code);

    /** Parameters of one indicator, with per-indicator defaults and ranges applied. */
    List<ParameterResponse> forIndicator(UUID indicatorId);

    /** Parameters attached to one strategy directly - not its indicators'. */
    List<ParameterResponse> forStrategy(UUID strategyId);

    /** Every indicator and strategy this parameter is attached to. */
    ParameterUsageResponse usage(Long id);
}
