package com.example.tradeLedger.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Authenticated encryption for broker credentials at rest.
 *
 * AES-GCM with a fresh random IV per call, not the {@code Cipher.getInstance("AES")}
 * that {@link CryptoUtil} uses. That default is ECB with no authentication, which
 * means two accounts holding the same API secret produce identical ciphertext, and
 * a tampered value decrypts to garbage instead of failing. Neither is acceptable
 * for a key that can move money, so this is a separate class rather than a change
 * to the Google-token path already in production.
 *
 * Stored form is {@code v1:base64(iv || ciphertext || tag)}. The version prefix is
 * what makes a future key rotation or algorithm change decidable per row instead
 * of requiring the whole table to be migrated at once.
 *
 * <p><b>Key.</b> {@code CREDENTIAL_ENCRYPTION_KEY}, base64 of 16, 24 or 32 raw
 * bytes:
 * <pre>openssl rand -base64 32</pre>
 *
 * When it is unset the application still starts - the rest of the platform does
 * not depend on this - but every credential read and write fails loudly. Starting
 * with no key and silently storing plaintext would be the worse failure.
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";
    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private static final String NO_KEY =
            "CREDENTIAL_ENCRYPTION_KEY is not set, so broker credentials cannot be stored or read. "
                    + "Generate one with: openssl rand -base64 32";

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public SecretCipher(@Value("${credentials.encryption-key:}") String configuredKey) {
        this.key = parseKey(configuredKey);
        if (this.key == null) {
            log.error("SecretCipher has no key. {}", NO_KEY);
        }
    }

    /** True when a usable key was supplied; lets callers report the cause once. */
    public boolean isConfigured() {
        return key != null;
    }

    /** Null in, null out - an absent credential field is not an empty ciphertext. */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] packed = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(sealed, 0, packed, iv.length, sealed.length);

            return PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (Exception e) {
            // The message never carries the plaintext or the key.
            throw new IllegalStateException("Failed to encrypt credential", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        requireKey();
        if (!stored.startsWith(PREFIX)) {
            throw new IllegalStateException(
                    "Credential is not in the expected v1 format. It was probably written before "
                            + "encryption was added, or with a different key.");
        }
        try {
            byte[] packed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(packed, 0, iv, 0, IV_BYTES);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(packed, IV_BYTES, packed.length - IV_BYTES);

            return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to decrypt credential - wrong CREDENTIAL_ENCRYPTION_KEY, or the value was altered", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException(NO_KEY);
        }
    }

    private static SecretKeySpec parseKey(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException e) {
            log.error("CREDENTIAL_ENCRYPTION_KEY is not valid base64. Generate one with: openssl rand -base64 32");
            return null;
        }
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            log.error("CREDENTIAL_ENCRYPTION_KEY decodes to {} bytes; AES needs 16, 24 or 32. "
                    + "Generate one with: openssl rand -base64 32", raw.length);
            return null;
        }
        return new SecretKeySpec(raw, ALGORITHM);
    }
}
