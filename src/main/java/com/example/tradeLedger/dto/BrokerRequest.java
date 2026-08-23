package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The broker catalog - shared master data, not per-user.
 */
@Schema(name = "BrokerRequest",
        description = "A broker in the platform catalog. SHARED master data, not per-user: "
                + "a user's own setup with a broker is /api/v1/my-brokers. authType decides "
                + "which credential fields that broker needs.")
public class BrokerRequest {

    @Schema(description = "Unique business key, uppercase by convention.", example = "DHAN", maxLength = 30)
    private String code;

    @Schema(example = "Dhan", maxLength = 100)
    private String name;

    @Schema(example = "Dhan HQ trading API")
    private String description;

    @Schema(example = "https://api.dhan.co")
    private String apiBaseUrl;

    @Schema(description = "Decides which credential fields a setup for this broker needs.",
            example = "api_key",
            allowableValues = {"api_key", "oauth_redirect", "totp"},
            defaultValue = "api_key")
    private String authType;

    @Schema(example = "true", defaultValue = "true")
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
