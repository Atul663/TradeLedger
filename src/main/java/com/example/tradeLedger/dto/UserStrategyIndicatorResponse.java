package com.example.tradeLedger.dto;

import java.util.List;
import java.util.UUID;

/**
 * One indicator usage inside a user strategy, with every knob it contributes
 * already resolved.
 *
 * The indicator's identity and parameter set come from the global catalog; only
 * {@code enabled}, {@code slot} and the {@code customValue} inside each parameter
 * belong to the user.
 */
public record UserStrategyIndicatorResponse(
        UUID id,
        UUID indicatorId,
        String indicatorName,

        /** Set only when one template uses this indicator more than once. */
        String slot,

        boolean enabled,
        int displayOrder,
        List<EffectiveParameterResponse> parameters) {
}
