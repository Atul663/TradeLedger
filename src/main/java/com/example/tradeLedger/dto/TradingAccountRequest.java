package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * One account under a broker setup - the thing a strategy is deployed onto.
 */
@Schema(name = "TradingAccountRequest",
        description = """
                One account under a broker setup. This is what a strategy is deployed onto, and \
                adding a second account here is what makes a userBrokerId deploy target fan out.

                No exchange: where an order goes is decided by the strategy's symbol, which \
                already knows its venue. Credentials are inherited from the setup unless this \
                account overrides them at /api/v1/trading-accounts/{id}/credentials.""")
public class TradingAccountRequest {

    @Schema(description = "The setup this account hangs off. An account cannot move between setups.",
            example = "ub000000-1111-4222-8333-444444444444")
    private UUID userBrokerId;

    @Schema(description = "Your own label, unique within the setup.", example = "hedge", maxLength = 100)
    private String accountName;

    @Schema(description = "The broker's own identifier for the account.", example = "1100112244")
    private String brokerAccountId;

    @Schema(description = "An inactive account is refused at deploy time.",
            example = "true", defaultValue = "true")
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
