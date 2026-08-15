package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.IndicatorDefRequest;
import com.example.tradeLedger.dto.IndicatorDefResponse;

import java.util.List;
import java.util.UUID;

/**
 * CRUD over {@code indicator_defs} - the catalog of compute primitives.
 *
 * An indicator's parameters are its {@code param_schema} JSON, per the design:
 * there is no indicator_params table, and creating one would reintroduce exactly
 * the EAV shape the schema's changelog records as removed (gap #7). So parameter
 * CRUD for an indicator IS this module's create/update of {@code paramSchema},
 * while the per-strategy VALUES of those parameters are
 * {@code strategy_param_defs} rows managed through
 * {@link StrategyDefinitionService}.
 *
 * Like {@code strategies}, indicators are platform-level rows with no owner
 * column - they are shared primitives, and an EMA computed once serves every
 * subscriber that resolves to it.
 */
public interface IndicatorDefinitionService {

    List<IndicatorDefResponse> list(Boolean active);

    IndicatorDefResponse get(UUID id);

    IndicatorDefResponse getByName(String name);

    IndicatorDefResponse create(IndicatorDefRequest request);

    IndicatorDefResponse update(UUID id, IndicatorDefRequest request);

    /**
     * Hard delete. Refused while any strategy's rule tree references the
     * indicator by name - deactivate it instead.
     */
    void delete(UUID id);
}
