package com.example.tradeLedger.dto;

import java.util.UUID;

/**
 * The whole "add a broker" wizard in one request: the setup, its first account,
 * and the API key - created together or not at all.
 *
 * Doing it as three calls works and stays supported, but leaves a hole: if the
 * key turns out to be wrong on the third call, the setup and account are already
 * saved and the user is looking at a half-built broker. Here the three run inside
 * one transaction, so a rejected key takes the setup and the account back out
 * with it.
 *
 * <pre>
 * { "brokerCode": "DELTA",
 *   "label": "My Delta",
 *   "account": { "accountName": "main", "brokerAccountId": "42891" },
 *   "credentials": { "apiKey": "...", "apiSecret": "..." } }
 * </pre>
 *
 * {@code account} and {@code credentials} are both optional - omit either to
 * build the rest now and finish later through the individual endpoints.
 */
public class BrokerSetupRequest {

    /** Where the credentials are written. */
    public enum CredentialsScope {
        /**
         * On the setup, shared by every account under it. The default, and right
         * for the usual case where the key belongs to the login rather than to
         * one account - a second account then needs no key at all.
         */
        SETUP,
        /**
         * On the account alone, as an override. Only for brokers that really do
         * issue a separate key per sub-account. Requires {@code account}.
         */
        ACCOUNT
    }

    /** Required on create. */
    private UUID brokerId;

    /** Alternative to brokerId - brokers.code is unique, e.g. DELTA. */
    private String brokerCode;

    /** Optional; defaults to the broker's own name. */
    private String label;

    private Boolean active;

    /** The first account under the setup. Optional. */
    private Account account;

    /** The API key. Optional. */
    private BrokerCredentialRequest credentials;

    /** Defaults to SETUP. */
    private CredentialsScope credentialsScope;

    /** The account half of the wizard - the same fields as a plain create. */
    public static class Account {
        private String accountName;
        private String brokerAccountId;
        private Boolean active;

        public String getAccountName() { return accountName; }
        public void setAccountName(String accountName) { this.accountName = accountName; }

        public String getBrokerAccountId() { return brokerAccountId; }
        public void setBrokerAccountId(String brokerAccountId) { this.brokerAccountId = brokerAccountId; }

        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }

    public UUID getBrokerId() { return brokerId; }
    public void setBrokerId(UUID brokerId) { this.brokerId = brokerId; }

    public String getBrokerCode() { return brokerCode; }
    public void setBrokerCode(String brokerCode) { this.brokerCode = brokerCode; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }

    public BrokerCredentialRequest getCredentials() { return credentials; }
    public void setCredentials(BrokerCredentialRequest credentials) { this.credentials = credentials; }

    public CredentialsScope getCredentialsScope() { return credentialsScope; }
    public void setCredentialsScope(CredentialsScope credentialsScope) {
        this.credentialsScope = credentialsScope;
    }
}
