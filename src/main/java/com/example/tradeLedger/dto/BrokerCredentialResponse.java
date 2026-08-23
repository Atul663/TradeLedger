package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
 */
@Schema(name = "BrokerCredentialResponse",
        description = """
                What a credential form is allowed to see. NO SECRET IS EVER RETURNED - not even \
                to the user who wrote it. The API key comes back as its last four characters and \
                every other secret as a boolean, which is all a form needs to show "set, change?".

                Read at the ACCOUNT level the flags describe the EFFECTIVE state - inherited from \
                the setup unless this account overrode it - and overriddenFields names what came \
                from the account's own row. An empty list there means it is running entirely on \
                the setup's credentials.""")
public record BrokerCredentialResponse(

        @Schema(example = "ub000000-1111-4222-8333-444444444444")
        UUID userBrokerId,

        @Schema(description = "Null on a setup-level read; set when reading one account's override.",
                example = "null")
        UUID tradingAccountId,

        @Schema(example = "DHAN")
        String brokerCode,

        @Schema(example = "api_key", allowableValues = {"api_key", "oauth_redirect", "totp"})
        String authType,

        @Schema(description = "Enough of the key to tell two apart. Never the whole key.",
                example = "dhan…xxxx")
        String apiKeyHint,

        @Schema(example = "true")
        boolean hasApiKey,

        @Schema(example = "true")
        boolean hasApiSecret,

        @Schema(example = "false")
        boolean hasAccessToken,

        @Schema(example = "false")
        boolean hasRefreshToken,

        @Schema(example = "false")
        boolean hasTotpSecret,

        @Schema(description = "Not a secret, so it is returned in full.",
                example = "https://app.example.com/broker/callback")
        String redirectUrl,

        @Schema(description = "Not a secret.", example = "1100112233")
        String clientId,

        @Schema(example = "2026-08-24T09:15:00+05:30")
        OffsetDateTime tokenExpiresAt,

        @Schema(description = "Derived from tokenExpiresAt - prompt the user to re-authorize.",
                example = "false")
        boolean tokenExpired,

        @Schema(description = "Account-level reads only: which fields came from this account's "
                + "own row rather than the setup's. Empty means fully inherited.",
                example = "[]")
        List<String> overriddenFields,

        @Schema(description = "Pointer to an external secret store, when one is used.",
                example = "null")
        String vaultRef,

        @Schema(description = "When the credentials were last written.",
                example = "2026-08-23T19:41:02.114+05:30")
        OffsetDateTime rotatedAt,

        @Schema(example = "2026-08-23T19:41:02.114+05:30")
        OffsetDateTime createdAt,

        @Schema(example = "2026-08-23T19:41:02.114+05:30")
        OffsetDateTime updatedAt) {
}
