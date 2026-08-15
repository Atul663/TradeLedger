package com.example.tradeLedger.utils;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Content addressing for {@code strategy_instances}.
 *
 * Reproduces the schema's {@code compute_config_hash()} exactly:
 * <pre>
 * sha256(strategy_id::text || '|' || symbol_id::text || '|' ||
 *        timeframe || '|' || canonical_jsonb(params)::text)
 * </pre>
 *
 * Only SIGNAL-scope params take part. Execution params (SL/TP) live on the
 * subscription and are deliberately excluded, which is what lets two users with
 * different stop losses still share one indicator computation.
 *
 * The database trigger recomputes this on INSERT and wins; the application
 * computes it up front only so an existing instance can be found before an
 * insert is attempted. The two agreeing is a correctness requirement, not an
 * optimization - see StrategyDedupTest.
 */
public final class ConfigHashUtil {

    private ConfigHashUtil() {
    }

    public static String configHash(UUID strategyId, UUID symbolId, String timeframe, JsonNode signalParams) {
        String payload = strategyId + "|" + symbolId + "|" + timeframe + "|"
                + CanonicalJson.canonicalize(signalParams);
        return sha256Hex(payload);
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
