package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.exception.StrategyValidationException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The one place that decides what a timeframe looks like.
 *
 * Subscriptions and saved strategies both carry one, and the moment two copies
 * of this rule exist they can disagree - a saved strategy that stores "5M" and a
 * subscription that stores "5m" would hash into two different shared configs and
 * split the dedup silently.
 */
final class Timeframes {

    /** '30s', '5m', '15m', '1h', '1d', '1w' - the shape the design's timeframes take. */
    private static final Pattern PATTERN = Pattern.compile("^[0-9]{1,4}[smhdw]$");

    private Timeframes() {
    }

    /** @throws StrategyValidationException when absent or malformed */
    static String normalize(String timeframe) {
        String normalized = normalizeOrNull(timeframe);
        if (normalized == null) {
            throw new StrategyValidationException("timeframe is required");
        }
        return normalized;
    }

    /** Null in, null out - for the callers where a timeframe is genuinely optional. */
    static String normalizeOrNull(String timeframe) {
        if (timeframe == null || timeframe.isBlank()) {
            return null;
        }
        String normalized = timeframe.trim().toLowerCase(Locale.ROOT);
        if (!PATTERN.matcher(normalized).matches()) {
            throw new StrategyValidationException(
                    "timeframe must look like 30s / 5m / 15m / 1h / 1d / 1w, got '" + timeframe + "'");
        }
        return normalized;
    }
}
