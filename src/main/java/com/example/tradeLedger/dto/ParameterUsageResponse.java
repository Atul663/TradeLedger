package com.example.tradeLedger.dto;

import java.util.List;
import java.util.UUID;

/**
 * Where a catalog parameter is used - the reverse of the hierarchy.
 *
 * Answers "given a parameter id, determine where it is used" as two index
 * lookups rather than a scan, which is the point of giving parameters their own
 * identity in the first place.
 */
public record ParameterUsageResponse(
        Long parameterId,
        String code,
        String name,
        boolean universal,
        List<Usage> indicators,
        List<Usage> strategies) {

    /** One attachment: the indicator or strategy this parameter is attached to. */
    public record Usage(UUID id, String name) {
    }
}
