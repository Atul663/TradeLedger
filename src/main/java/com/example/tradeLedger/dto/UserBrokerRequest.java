package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * One user's setup with a broker - the parent of every account reached through it.
 */
@Schema(name = "UserBrokerRequest",
        description = "Your own setup with a catalog broker. Prefer POST /api/v1/my-brokers/setup, "
                + "which creates the setup, its first account and its key in one transaction.")
public class UserBrokerRequest {

    @Schema(description = "The catalog broker. Send this or brokerCode.",
            example = "b1000000-1111-4222-8333-444444444444")
    private UUID brokerId;

    @Schema(description = "Alternative to brokerId - brokers.code is unique.", example = "DHAN")
    private String brokerCode;

    @Schema(description = "Your own name for this setup. Unique per user.", example = "My Dhan")
    private String label;

    @Schema(example = "true", defaultValue = "true")
    private Boolean active;

    public UUID getBrokerId() { return brokerId; }
    public void setBrokerId(UUID brokerId) { this.brokerId = brokerId; }

    public String getBrokerCode() { return brokerCode; }
    public void setBrokerCode(String brokerCode) { this.brokerCode = brokerCode; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
