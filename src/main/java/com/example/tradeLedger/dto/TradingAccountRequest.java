package com.example.tradeLedger.dto;

import java.util.UUID;

/**
 * Create / update body for {@code trading_accounts}.
 *
 * {@code vaultRef} is written to {@code account_credentials} - the pointer to the
 * secret, never the secret itself. The schema has no api_key / api_secret columns
 * by design.
 */
public class TradingAccountRequest {

    private UUID exchangeId;

    /** Alternative to exchangeId - exchanges.code is unique. */
    private String exchangeCode;

    private String accountName;

    private Boolean active;

    /** e.g. secret/brokers/dhan/acct-123 */
    private String vaultRef;

    public UUID getExchangeId() { return exchangeId; }
    public void setExchangeId(UUID exchangeId) { this.exchangeId = exchangeId; }

    public String getExchangeCode() { return exchangeCode; }
    public void setExchangeCode(String exchangeCode) { this.exchangeCode = exchangeCode; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public String getVaultRef() { return vaultRef; }
    public void setVaultRef(String vaultRef) { this.vaultRef = vaultRef; }
}
