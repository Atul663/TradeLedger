package com.example.tradeLedger.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Table {@code brokers}: who the order is placed through.
 *
 * Deliberately separate from {@link Exchange}. An exchange is the venue an
 * instrument trades on and is what {@link Symbol} hangs off; a broker is the API
 * the platform authenticates against to reach that venue. One account trades NSE
 * through Dhan, and folding both into {@code exchanges} would put venue rows and
 * broker rows in the table that {@code symbols} has a foreign key to.
 *
 * {@code authType} tells the UI which credential fields actually matter for this
 * broker, since {@link BrokerCredential} carries the union of them:
 * <ul>
 *   <li>{@code api_key}      - key + secret, no browser step (Dhan partner API)</li>
 *   <li>{@code oauth_redirect} - key + secret + redirect URL, exchanged for a
 *       daily access token (Zerodha Kite, Upstox)</li>
 *   <li>{@code totp}         - key + secret + client id + TOTP seed (Angel One)</li>
 * </ul>
 */
@Entity
@Table(name = "brokers")
public class Broker {

    public static final String AUTH_API_KEY = "api_key";
    public static final String AUTH_OAUTH_REDIRECT = "oauth_redirect";
    public static final String AUTH_TOTP = "totp";

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Stable uppercase handle the adapters switch on: DHAN, ZERODHA, UPSTOX. */
    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** Root of the broker's REST API, so the adapter is not hard-coded to one host. */
    @Column(name = "api_base_url", columnDefinition = "text")
    private String apiBaseUrl;

    @Column(name = "auth_type", nullable = false, length = 30)
    private String authType = AUTH_API_KEY;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

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

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }

    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
