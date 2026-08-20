package com.example.tradeLedger.service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Decrypted, fully resolved credentials for one trading account - what a broker
 * adapter needs to sign a request.
 *
 * Resolved means the account's own values where it has them and the setup's
 * everywhere else, merged field by field. A caller never has to know which level
 * a value came from.
 *
 * <p><b>This must never be returned from a controller.</b> It is deliberately not
 * in the {@code dto} package, so returning one from an endpoint requires an
 * import that reads as obviously wrong in review. The REST surface returns
 * {@code BrokerCredentialResponse}, which is masked.
 *
 * Hold one no longer than the call that needs it: fetch, sign, discard. Caching
 * these turns one leak into every leak.
 */
public record BrokerCredentials(
        UUID userBrokerId,
        UUID tradingAccountId,
        String brokerCode,
        String authType,
        String apiBaseUrl,
        /** The broker's own id for this account - which sub-account to act on. */
        String brokerAccountId,
        String apiKey,
        String apiSecret,
        String accessToken,
        String refreshToken,
        String totpSecret,
        String redirectUrl,
        String clientId,
        OffsetDateTime tokenExpiresAt,
        String vaultRef) {

    /** True when a token is set and has passed its expiry - re-auth before using. */
    public boolean tokenExpired() {
        return tokenExpiresAt != null && tokenExpiresAt.isBefore(OffsetDateTime.now());
    }

    /** Keeps a stray log line or stack trace from printing the secrets. */
    @Override
    public String toString() {
        return "BrokerCredentials[tradingAccountId=" + tradingAccountId
                + ", brokerCode=" + brokerCode + ", brokerAccountId=" + brokerAccountId
                + ", secrets omitted]";
    }
}
