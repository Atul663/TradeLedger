package com.example.tradeLedger.dto;

import java.util.Map;

/**
 * One knob, resolved: what the platform declares, what the user changed, and what
 * therefore applies.
 *
 * The three-level fallback behind {@link #effectiveValue()} is
 * {@code customValue} &rarr; the indicator's or template's link default &rarr;
 * {@code parameters.default_value}. {@link #defaultValue()} is what would apply
 * if the user cleared their override, so a UI can render "13 (default 9)" and an
 * {@code overridden} badge from one row.
 *
 * Everything except {@code customValue} is read live from the global catalog; the
 * user's own tables store none of it.
 */
public record EffectiveParameterResponse(
        Long parameterId,

        /** Business key: 'k', 'd', 'sl'. Stable, and what the engine's flat map is keyed by. */
        String code,

        /** Human label: 'K', 'Stop loss'. */
        String label,

        /** int | decimal | bool | enum | timeframe | text */
        String dataType,

        /** signal (shared, hashed on subscribe) | execution (personal) */
        String scope,

        /** What applies if the user clears their override. */
        String defaultValue,

        /** The user's value, or null when they never changed this knob. */
        String customValue,

        /** customValue when set, else defaultValue. What the bot should use. */
        String effectiveValue,

        boolean overridden,

        /** {"min":2,"max":300} - the narrowest rules in force for this usage. */
        Map<String, Object> validation,

        boolean required,
        int displayOrder,

        /** The link row whose default this knob overrides; null at strategy level. */
        Long indicatorParameterLinkId) {
}
