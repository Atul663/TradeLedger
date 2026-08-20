package com.example.tradeLedger.dto;

import java.util.UUID;

/**
 * Create / update body for {@code user_brokers} - a user's setup with one broker.
 *
 * This is step one: set the broker up, then create accounts under it. The API key
 * is a separate call to {@code PUT /api/v1/my-brokers/{id}/credentials}, so a
 * secret is never a field on a body that also does renames.
 */
public class UserBrokerRequest {

    /** Required on create. */
    private UUID brokerId;

    /** Alternative to brokerId - brokers.code is unique, e.g. DELTA. */
    private String brokerCode;

    /**
     * The user's own name for this setup. Defaults to the broker's name, so the
     * common case of one setup per broker needs no label at all. Two logins with
     * the same broker need two distinct labels.
     */
    private String label;

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
