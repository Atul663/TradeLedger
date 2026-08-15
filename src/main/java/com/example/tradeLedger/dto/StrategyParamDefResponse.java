package com.example.tradeLedger.dto;

import java.util.Map;

/** One knob, with enough metadata for a form to render and validate itself. */
public record StrategyParamDefResponse(
        Long id,
        String parameterKey,
        String dataType,
        String scope,
        String defaultValue,
        Map<String, Object> validation,
        String displayLabel,
        int displayOrder,
        boolean required) {
}
