package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
 * {@code account} and {@code credentials} are both optional - omit either to
 * build the rest now and finish later through the individual endpoints.
 */
@Schema(name = "BrokerSetupRequest",
        description = """
                The whole "add a broker" wizard in ONE transaction: the setup, its first account \
                and the API key. If the key is rejected, the setup and the account go back out \
                with it - which is the hole three separate calls leave.

                account and credentials are both optional; omit either to build the rest now and \
                finish later through the individual endpoints.

                Send whatever the broker's authType needs: api_key wants apiKey + apiSecret + \
                clientId, oauth_redirect wants redirectUrl and the token pair, totp wants \
                totpSecret. Secrets are stored AES-GCM encrypted and are never returned - reads \
                give you has* booleans and an apiKeyHint.""")
public class BrokerSetupRequest {

    /** Where the credentials are written. */
    @Schema(name = "CredentialsScope",
            description = "SETUP writes the key on the setup so every account under it inherits "
                    + "the key - the usual case, and a second account then needs no key at all. "
                    + "ACCOUNT writes it on the account alone, for brokers that really do issue "
                    + "a separate key per sub-account.")
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

    @Schema(description = "The catalog broker. Send this or brokerCode.",
            example = "b1000000-1111-4222-8333-444444444444")
    private UUID brokerId;

    @Schema(description = "Alternative to brokerId - brokers.code is unique.", example = "DHAN")
    private String brokerCode;

    @Schema(description = "Your own name for this setup. Unique per user; defaults to the "
            + "broker's own name.", example = "My Dhan")
    private String label;

    @Schema(example = "true", defaultValue = "true")
    private Boolean active;

    @Schema(description = "The first account under the setup. Optional.")
    private Account account;

    @Schema(description = "The API key. Optional.")
    private BrokerCredentialRequest credentials;

    @Schema(description = "Where the key is written.", example = "SETUP", defaultValue = "SETUP")
    private CredentialsScope credentialsScope;

    /** The account half of the wizard - the same fields as a plain create. */
    @Schema(name = "BrokerSetupAccount",
            description = "The first trading account under the setup. Add more later with "
                    + "POST /api/v1/trading-accounts - that is what makes a userBrokerId deploy "
                    + "target fan out to several accounts.")
    public static class Account {

        @Schema(description = "Your own label, unique within this setup.", example = "main")
        private String accountName;

        @Schema(description = "The broker's own identifier for the account - what tells two "
                + "accounts under one shared key apart when an order is placed.",
                example = "1100112233")
        private String brokerAccountId;

        @Schema(example = "true", defaultValue = "true")
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
