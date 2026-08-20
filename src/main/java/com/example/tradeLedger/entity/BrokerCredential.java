package com.example.tradeLedger.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Table {@code broker_credentials}: what authenticates to a broker, at either of
 * two levels.
 *
 * <pre>
 * trading_account_id IS NULL  -> the setup's own key, used by all its accounts
 * trading_account_id IS SET   -> one account's override of it
 * </pre>
 *
 * Two levels rather than one because that is how brokers differ. A Dhan login
 * issues one key for everything under it; a Delta sub-account can be issued its
 * own. Both are expressible without a second table or a nullable mess, and
 * {@code user_broker_id} is always set so every row is reachable from the setup.
 *
 * <p><b>Resolution is per field, not per row.</b> An account row holding only an
 * {@code accessToken} still inherits the setup's {@code apiKey} and
 * {@code apiSecret}. That is the same rule {@code user_strategy_parameters} uses
 * for parameter defaults, and it is what makes "this one sub-account has its own
 * session token" a one-field write instead of a full copy that then drifts.
 *
 * <p><b>The secret columns hold ciphertext, not secrets.</b> They are written and
 * read through {@code SecretCipher} (AES-GCM, key from
 * {@code CREDENTIAL_ENCRYPTION_KEY}). Nothing in this class decrypts; the service
 * owns that, which keeps the set of places a plaintext secret exists small enough
 * to audit.
 *
 * <p>{@code redirectUrl} and {@code clientId} are plaintext on purpose - a
 * callback URL is registered publicly with the broker and a client id is an
 * account number. Encrypting them would cost searchability and protect nothing.
 */
@Entity
@Table(name = "broker_credentials")
public class BrokerCredential {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Always set, including on an account-level override. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_broker_id", nullable = false)
    private UserBroker userBroker;

    /** Null for the setup's own credentials; set for one account's override. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trading_account_id")
    private TradingAccount tradingAccount;

    /** Optional Vault pointer, for installations that keep secrets there. */
    @Column(name = "vault_ref", columnDefinition = "text")
    private String vaultRef;

    /** Ciphertext. */
    @Column(name = "api_key", columnDefinition = "text")
    private String apiKey;

    /** Ciphertext. */
    @Column(name = "api_secret", columnDefinition = "text")
    private String apiSecret;

    /** Ciphertext. */
    @Column(name = "access_token", columnDefinition = "text")
    private String accessToken;

    /** Ciphertext. */
    @Column(name = "refresh_token", columnDefinition = "text")
    private String refreshToken;

    /** Ciphertext. */
    @Column(name = "totp_secret", columnDefinition = "text")
    private String totpSecret;

    /** Plaintext: a public OAuth callback, not a secret. */
    @Column(name = "redirect_url", columnDefinition = "text")
    private String redirectUrl;

    /** Plaintext: the broker's login identifier, not a secret by itself. */
    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "token_expires_at")
    private OffsetDateTime tokenExpiresAt;

    /** Stamped whenever a secret changes - the audit trail the schema asks for. */
    @Column(name = "rotated_at")
    private OffsetDateTime rotatedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /** True when this row overrides a setup's credentials for one account. */
    public boolean isAccountScoped() {
        return tradingAccount != null;
    }

    /** True when the row still holds something; an emptied override is deleted. */
    public boolean hasAnyValue() {
        return vaultRef != null || apiKey != null || apiSecret != null
                || accessToken != null || refreshToken != null || totpSecret != null
                || redirectUrl != null || clientId != null || tokenExpiresAt != null;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UserBroker getUserBroker() { return userBroker; }
    public void setUserBroker(UserBroker userBroker) { this.userBroker = userBroker; }

    public TradingAccount getTradingAccount() { return tradingAccount; }
    public void setTradingAccount(TradingAccount tradingAccount) { this.tradingAccount = tradingAccount; }

    public String getVaultRef() { return vaultRef; }
    public void setVaultRef(String vaultRef) { this.vaultRef = vaultRef; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getApiSecret() { return apiSecret; }
    public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getTotpSecret() { return totpSecret; }
    public void setTotpSecret(String totpSecret) { this.totpSecret = totpSecret; }

    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public OffsetDateTime getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(OffsetDateTime tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }

    public OffsetDateTime getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(OffsetDateTime rotatedAt) { this.rotatedAt = rotatedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
