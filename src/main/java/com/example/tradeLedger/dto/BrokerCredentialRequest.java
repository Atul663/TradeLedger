package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Broker API credentials. Stored AES-GCM encrypted; never returned in a read.
 *
 * PUT is partial: a field left out keeps its stored value, and a field sent as
 * {@code ""} clears it.
 */
@Schema(name = "BrokerCredentialRequest",
        description = """
                Broker API credentials. Send only what the broker's authType needs:

                  api_key         apiKey, apiSecret, clientId
                  oauth_redirect  apiKey, apiSecret, redirectUrl, accessToken, refreshToken, tokenExpiresAt
                  totp            apiKey, clientId, totpSecret

                Stored AES-GCM encrypted and NEVER returned - reads give has* booleans and an \
                apiKeyHint. PUT is partial: an omitted field keeps its stored value, and a field \
                sent as "" clears it.

                Requires CREDENTIAL_ENCRYPTION_KEY to be set on the server, or every read and \
                write here fails.""")
public class BrokerCredentialRequest {

    @Schema(example = "dhan-api-key-xxxx")
    private String apiKey;

    @Schema(example = "dhan-api-secret-yyyy")
    private String apiSecret;

    @Schema(description = "oauth_redirect brokers only.", example = "https://app.example.com/broker/callback")
    private String redirectUrl;

    @Schema(description = "The broker's client / user id.", example = "1100112233")
    private String clientId;

    @Schema(description = "oauth_redirect brokers only.", example = "eyJhbGciOiJIUzI1NiJ9…")
    private String accessToken;

    @Schema(description = "oauth_redirect brokers only.")
    private String refreshToken;

    @Schema(description = "totp brokers only - the shared secret behind the rotating code.")
    private String totpSecret;

    @Schema(description = "When accessToken expires; drives the tokenExpired flag on read.",
            example = "2026-08-24T09:15:00+05:30")
    private OffsetDateTime tokenExpiresAt;

    @Schema(description = "Pointer to an external secret store, instead of storing the secret here.",
            example = "vault://kv/brokers/dhan/main")
    private String vaultRef;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getApiSecret() { return apiSecret; }
    public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }

    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getTotpSecret() { return totpSecret; }
    public void setTotpSecret(String totpSecret) { this.totpSecret = totpSecret; }

    public OffsetDateTime getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(OffsetDateTime tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }

    public String getVaultRef() { return vaultRef; }
    public void setVaultRef(String vaultRef) { this.vaultRef = vaultRef; }
}
