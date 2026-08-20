package com.example.tradeLedger.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Table {@code trading_accounts}: one account reached through a {@link UserBroker}
 * setup - the thing a strategy is actually subscribed against.
 *
 * <pre>
 * user_brokers      "My Delta"     the login and its API key
 *   trading_accounts  main, hedge  the accounts underneath it
 * </pre>
 *
 * <p><b>No exchange.</b> The account used to name one, which forced a Dhan login
 * that reaches NSE and BSE into two rows and made a venue-less broker like Delta
 * pick a meaningless one. Where an order goes is decided by the symbol, which
 * already knows its exchange, so the column is gone rather than left unread.
 *
 * <p>{@code user} is kept alongside {@code userBroker} even though the setup
 * already knows its owner. Ownership filtering happens on every read
 * ({@code findByIdAndUser_Id}) and joining through the parent to do it would turn
 * the cheapest, most frequent check in the module into a two-table query.
 *
 * Required by the strategy module because {@code subscriptions.trading_account_id}
 * is NOT NULL - and because UNIQUE(shared_config_id, trading_account_id) is
 * what makes "one config on two accounts" two independently tracked legs.
 */
@Entity
@Table(name = "trading_accounts",
        uniqueConstraints = @UniqueConstraint(name = "uq_taccounts_broker_account",
                columnNames = {"user_broker_id", "account_name"}))
public class TradingAccount {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_broker_id", nullable = false)
    private UserBroker userBroker;

    /** The user's own label for this account, unique within the setup. */
    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;

    /**
     * The broker's own identifier for this account - a Delta sub-account id, a
     * Dhan client id. This is what tells two accounts under one shared API key
     * apart when an order is placed, so it matters most exactly when the
     * credentials are inherited rather than overridden.
     */
    @Column(name = "broker_account_id", length = 100)
    private String brokerAccountId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

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

    /** Convenience for the many call sites that want the catalog row, not the setup. */
    public Broker getBroker() {
        return userBroker != null ? userBroker.getBroker() : null;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public UserBroker getUserBroker() { return userBroker; }
    public void setUserBroker(UserBroker userBroker) { this.userBroker = userBroker; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public String getBrokerAccountId() { return brokerAccountId; }
    public void setBrokerAccountId(String brokerAccountId) { this.brokerAccountId = brokerAccountId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
