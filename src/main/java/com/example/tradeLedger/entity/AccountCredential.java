package com.example.tradeLedger.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Table {@code account_credentials}: 1:1 with {@link TradingAccount}, Vault
 * reference only.
 *
 * There are deliberately no api_key / api_secret / passphrase columns - the
 * secret lives in Vault and only the pointer is stored, so a database dump
 * leaks nothing and rotation never touches this row's contents.
 */
@Entity
@Table(name = "account_credentials")
public class AccountCredential {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trading_account_id", nullable = false, unique = true)
    private TradingAccount tradingAccount;

    /** e.g. {@code secret/brokers/deribit/acct-123} */
    @Column(name = "vault_ref", nullable = false, columnDefinition = "text")
    private String vaultRef;

    @Column(name = "rotated_at")
    private OffsetDateTime rotatedAt;

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

    public TradingAccount getTradingAccount() { return tradingAccount; }
    public void setTradingAccount(TradingAccount tradingAccount) { this.tradingAccount = tradingAccount; }

    public String getVaultRef() { return vaultRef; }
    public void setVaultRef(String vaultRef) { this.vaultRef = vaultRef; }

    public OffsetDateTime getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(OffsetDateTime rotatedAt) { this.rotatedAt = rotatedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
