package com.example.tradeLedger.indicator;

import java.util.Map;
import java.util.TreeMap;

/**
 * Stable identity for one indicator computation, e.g. {@code EMA(period=9)}.
 *
 * Fingerprints come from RESOLVED indicator params ($fast already substituted
 * with 9), so dedup works across strategies, not just within one: an EMA(9)
 * requested by EMA Crossover and by some future MACD strategy is one computation.
 */
public final class IndicatorFingerprint {

    private IndicatorFingerprint() {
    }

    public static String of(String indicator, String paramKey, Object paramValue) {
        return of(indicator, Map.of(paramKey, paramValue));
    }

    public static String of(String indicator, Map<String, Object> params) {
        TreeMap<String, Object> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder(indicator).append('(');
        boolean first = true;
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(normalize(e.getValue()));
            first = false;
        }
        return sb.append(')').toString();
    }

    /** 9, 9.0 and "9" must all render as 9 or the fingerprint splits. */
    private static Object normalize(Object value) {
        if (value instanceof Number n) {
            return new java.math.BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
        }
        if (value instanceof String s) {
            try {
                return new java.math.BigDecimal(s).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException ignored) {
                return s;
            }
        }
        return value;
    }
}
