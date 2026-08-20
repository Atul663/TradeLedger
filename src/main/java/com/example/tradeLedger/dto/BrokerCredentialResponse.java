package com.example.tradeLedger.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * What a credential form is allowed to see.
 *
 * No secret is returned, ever - not even to the user who wrote it. The API key is
 * shown as its last four characters so a person can tell two keys apart, and
 * every other secret is reduced to a boolean. Anything that genuinely needs the
 * values decrypts them server-side through
 * {@code BrokerCredentialService.resolve}.
 *
 * <p>Read at the account level, the flags describe the <b>effective</b> state -
 * inherited from the setup unless this account overrode it - and
 * {@code overriddenFields} names the fields that came from the account's own row.
 * An empty list on an account view means it is running entirely on the setup's
 * credentials.
 */
public record BrokerCredentialResponse(
        UUID userBrokerId,
        /** Null on a setup-level read; set when reading one account's view. */
        UUID tradingAccountId,
        String brokerCode,
        String authType,

        /** Last four characters only, e.g. {@code ****f31a}. Null when unset. */
        String apiKeyHint,
        boolean hasApiKey,
        boolean hasApiSecret,
        boolean hasAccessToken,
        boolean hasRefreshToken,
        boolean hasTotpSecret,

        /** Not secret: registered publicly with the broker. */
        String redirectUrl,
        String clientId,

        OffsetDateTime tokenExpiresAt,
        /** True when a token is set and its expiry has passed - time to re-auth. */
        boolean tokenExpired,

        /** Field names this account overrides. Always empty on a setup read. */
        List<String> overriddenFields,

        String vaultRef,
        OffsetDateTime rotatedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
