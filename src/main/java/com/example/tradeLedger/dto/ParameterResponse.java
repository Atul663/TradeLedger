package com.example.tradeLedger.dto;

import java.util.Map;

/**
 * One parameter as it appears in a hierarchy.
 *
 * {@link #id} is the catalog id - stable, and the same value wherever the
 * parameter appears, so a client can key on it. An attachment is addressed by the
 * pair it belongs to ({@code strategyId}/{@code indicatorId} plus this id), which
 * the link tables' unique constraints make unambiguous; the link row's own id is
 * deliberately not exposed.
 *
 * {@code defaultValue}, {@code validation}, {@code displayOrder} and
 * {@code required} are already resolved for this usage: the per-usage override
 * when one is set, the catalog value otherwise.
 */
public record ParameterResponse(
        Long id,
        String code,
        String name,
        String dataType,
        String scope,
        String defaultValue,
        Map<String, Object> validation,
        String description,
        boolean universal,
        int displayOrder,
        boolean required) {
}
