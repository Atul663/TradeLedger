package com.example.tradeLedger.dto;

/**
 * Create / update body for the {@code brokers} catalog.
 *
 * This is platform master data, not one user's data: every {@code user_brokers}
 * row points at one of these, so a change here is visible to everybody.
 *
 * <pre>
 * { "code": "DELTA",
 *   "name": "Delta Exchange",
 *   "description": "Delta India: broker and venue in one",
 *   "apiBaseUrl": "https://api.india.delta.exchange",
 *   "authType": "api_key" }
 * </pre>
 *
 * {@code authType} is what a credential form switches on, so getting it right
 * matters more than it looks - it decides which fields the UI asks for:
 * <ul>
 *   <li>{@code api_key} - key + secret, no browser step</li>
 *   <li>{@code oauth_redirect} - key + secret + redirect URL, exchanged for a
 *       daily access token</li>
 *   <li>{@code totp} - key + secret + client id + TOTP seed</li>
 * </ul>
 */
public class BrokerRequest {

    /**
     * Stable handle the adapters switch on. Uppercased on the way in, so
     * {@code delta} and {@code DELTA} are the same broker rather than two.
     */
    private String code;

    private String name;

    private String description;

    private String apiBaseUrl;

    /** api_key | oauth_redirect | totp. Defaults to api_key. */
    private String authType;

    private Boolean active;

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

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
