package com.example.tradeLedger.utils;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Canonical JSON serialization for config hashing.
 *
 * This must reproduce, byte for byte, what Postgres produces for
 * {@code canonical_jsonb(params)::text} - because {@code compute_config_hash()}
 * concatenates exactly that string before hashing, and the
 * {@code trg_instances_hash} trigger is the referee. If this drifts from the
 * database function, dedup splits silently: the same 9x21 config produces two
 * instances instead of one, and two users pay for the same EMA twice.
 *
 * The rules, from {@code canonical_jsonb()} plus jsonb's own text rendering:
 * <ul>
 *   <li>object keys sorted lexicographically (jsonb stores them sorted)</li>
 *   <li>array order preserved</li>
 *   <li>numeric scale trimmed via {@code trim_scale()}, so 9, 9.0 and 9.00
 *       render identically as {@code 9}</li>
 *   <li><b>jsonb spacing:</b> {@code ": "} after a key and {@code ", "} between
 *       members - {@code {"fast": 9, "slow": 21}}, NOT the compact
 *       {@code {"fast":9,"slow":21}}</li>
 * </ul>
 */
public final class CanonicalJson {

    private CanonicalJson() {
    }

    public static String canonicalize(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        write(node, sb);
        return sb.toString();
    }

    private static void write(JsonNode node, StringBuilder sb) {
        if (node == null || node.isNull()) {
            sb.append("null");
            return;
        }
        if (node.isObject()) {
            List<String> keys = new ArrayList<>();
            for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
                keys.add(it.next());
            }
            keys.sort(CanonicalJson::compareJsonbKeys);
            sb.append('{');
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) sb.append(", ");
                writeString(keys.get(i), sb);
                sb.append(": ");
                write(node.get(keys.get(i)), sb);
            }
            sb.append('}');
            return;
        }
        if (node.isArray()) {
            sb.append('[');
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) sb.append(", ");
                write(node.get(i), sb);
            }
            sb.append(']');
            return;
        }
        if (node.isNumber()) {
            sb.append(trimScale(node.decimalValue()));
            return;
        }
        if (node.isBoolean()) {
            sb.append(node.booleanValue());
            return;
        }
        writeString(node.asText(), sb);
    }

    /**
     * jsonb orders object keys by length first, then by byte value - not by plain
     * lexicographic order. For the ASCII parameter keys used here the two agree
     * only when lengths match, so the length tie-break has to be explicit.
     */
    private static int compareJsonbKeys(String a, String b) {
        if (a.length() != b.length()) {
            return Integer.compare(a.length(), b.length());
        }
        return a.compareTo(b);
    }

    /** Postgres trim_scale() equivalent: 9.00 -> 9, 1.50 -> 1.5, 900 -> 900. */
    private static String trimScale(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** Canonical form of an already-sorted param map, for log lines. */
    public static String describe(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }
}
