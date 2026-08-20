package com.example.tradeLedger.dto;

import java.util.UUID;

/**
 * Create / update body for {@code trading_accounts}.
 *
 * An account belongs to a broker setup, so {@code userBrokerId} is the only
 * pointer it needs - the broker, and the credentials it inherits, come from
 * there. There is no exchange field: where an order goes is decided by the
 * symbol, which already knows its venue.
 */
public class TradingAccountRequest {

    /** The setup this account lives under. Required on create. */
    private UUID userBrokerId;

    private String accountName;

    /**
     * The broker's own id for this account - a Delta sub-account id, a Dhan
     * client id. What tells two accounts under one shared API key apart.
     */
    private String brokerAccountId;

    private Boolean active;

    public UUID getUserBrokerId() { return userBrokerId; }
    public void setUserBrokerId(UUID userBrokerId) { this.userBrokerId = userBrokerId; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public String getBrokerAccountId() { return brokerAccountId; }
    public void setBrokerAccountId(String brokerAccountId) { this.brokerAccountId = brokerAccountId; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
