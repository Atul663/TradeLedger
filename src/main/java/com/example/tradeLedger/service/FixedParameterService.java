package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.FixedParameterGroupResponse;
import com.example.tradeLedger.dto.FixedParameterRequest;
import com.example.tradeLedger.dto.FixedParameterResponse;

import java.util.List;
import java.util.UUID;

/**
 * CRUD over {@code fixed_parameters} - the catalog describing the platform's
 * FIXED knobs.
 *
 * <b>Descriptors, not values.</b> The value of every knob described here is a
 * typed column on {@code user_strategies} or {@code user_strategy_subscriptions}
 * and is written through {@link UserStrategyService} and
 * {@link StrategySubscriptionService}. Nothing in this module changes what a
 * strategy runs with; it changes what a form shows. That boundary is what keeps
 * this from being the key/value catalog the schema's changelog records as
 * removed - there is no join from a user row to these, and no read path resolves
 * a value through them.
 *
 * The rows are SHARED platform master data with no owner column, so reads are
 * unfiltered and writes are not scoped to the caller - the same stance
 * {@link ReferenceDataService} documents. The blast radius is smaller here:
 * nothing references a descriptor, so the worst a bad write does is mislabel a
 * field.
 *
 * The catalog is bootstrapped by {@code ControlPlaneSeeder} with a descriptor per
 * fixed column, and this API is how those are retuned afterwards.
 */
public interface FixedParameterService {

    /**
     * Ordered by group, then position, then name.
     *
     * @param paramGroup optional group filter, case-insensitive
     * @param scope      optional 'signal' / 'execution' filter
     * @param active     optional active-flag filter; null returns both
     */
    List<FixedParameterResponse> list(String paramGroup, String scope, Boolean active);

    /**
     * The same rows as {@link #list}, arranged one group per {@code paramGroup} -
     * the sections a form renders.
     *
     * The grouping is the only thing the flat list cannot express cheaply, since a
     * client would otherwise have to know that consecutive rows share a section.
     * The same filters apply and mean the same things; a group filter narrows it
     * to that one group rather than changing the shape. Descriptors with no group
     * collect in a single trailing entry whose {@code paramGroup} is null.
     *
     * @return groups in catalog order, each holding its rows in displayOrder-then-name order
     */
    List<FixedParameterGroupResponse> listGrouped(String paramGroup, String scope, Boolean active);

    FixedParameterResponse get(UUID id);

    /** By the business key - the way a form looks up the field it is rendering. */
    FixedParameterResponse getByName(String name);

    FixedParameterResponse create(FixedParameterRequest request);

    /** Partial. The default and the bounds are re-validated together, not in isolation. */
    FixedParameterResponse update(UUID id, FixedParameterRequest request);

    /**
     * Hard delete. Nothing points at a descriptor, so this is always allowed -
     * but deactivating is the reversible path, and the one that keeps a knob's
     * history of labels and defaults.
     */
    void delete(UUID id);
}
