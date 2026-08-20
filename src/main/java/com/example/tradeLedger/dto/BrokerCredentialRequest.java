package com.example.tradeLedger.dto;

import java.time.OffsetDateTime;

/**
 * Credentials for a broker setup, or one account's override of them.
 *
 * The same body serves both levels - {@code PUT /my-brokers/{id}/credentials}
 * writes the setup's, {@code PUT /trading-accounts/{id}/credentials} writes an
 * override for that one account. Which fields matter depends on the broker's
 * {@code authType}; this carries the union.
 *
 * <p><b>Partial by design.</b> A field left out, or sent as null, keeps whatever
 * was stored. That is what makes "store today's access token" a one-field request
 * without resending an API secret the caller cannot read back. To remove a value,
 * send an empty string:
 *
 * <pre>
 * {"accessToken": "eyJ..."}   // rotate the token, everything else untouched
 * {"totpSecret": ""}          // clear the TOTP seed
 * </pre>
 *
 * <p><b>At the account level, fields inherit.</b> An override row holding only an
 * access token still uses the setup's API key and secret - resolution is per
 * field, not per row. Clearing the last field on an override deletes the row, and
 * the account goes back to inheriting everything.
 *
 * Secret fields are encrypted before being written and are never returned.
 */
public class BrokerCredentialRequest {

    /** Public half of the API credential pair. Encrypted at rest regardless. */
    private String apiKey;

    /** Secret half. Encrypted at rest, never returned. */
    private String apiSecret;

    /**
     * OAuth callback the broker redirects to after login. Not a secret - it is
     * registered publicly with the broker - so it is stored and returned as-is.
     */
    private String redirectUrl;

    /** The broker's login identifier (Dhan client id, Angel client code). */
    private String clientId;

    /** Session token, usually short-lived. Encrypted at rest, never returned. */
    private String accessToken;

    /** Used to mint a new access token where the broker supports it. */
    private String refreshToken;

    /** TOTP seed for brokers whose login requires a rolling code. */
    private String totpSecret;

    /** When the access token stops working; drives the "needs re-auth" flag. */
    private OffsetDateTime tokenExpiresAt;

    /** Optional pointer for installations that do keep secrets in Vault. */
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
