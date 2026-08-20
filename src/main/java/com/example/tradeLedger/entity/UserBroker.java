package com.example.tradeLedger.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Table {@code user_brokers}: one user's authenticated setup with one
 * {@link Broker} - and the parent of every trading account reached through it.
 *
 * The same shape as {@code user_strategies}: {@code brokers} is the global
 * catalog every user shares, and this is one user's instance of a row in it.
 *
 * <pre>
 * brokers            DELTA, DHAN, ZERODHA          shared catalog
 *   user_brokers     "My Delta"                    one user's setup + its API key
 *     trading_accounts  main, hedge, algo-1        the accounts it reaches
 * </pre>
 *
 * The API key lives here rather than on the account because that is how brokers
 * actually work: one login, several accounts underneath it. An individual account
 * can still override a field when it genuinely has its own - see
 * {@link BrokerCredential}.
 *
 * Uniqueness is on {@code (user_id, label)}, not {@code (user_id, broker_id)}, so
 * two separate Delta logins are two setups rather than a conflict.
 */
@Entity
@Table(name = "user_brokers",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_brokers_user_label",
                columnNames = {"user_id", "label"}))
public class UserBroker {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broker_id", nullable = false)
    private Broker broker;

    /** The user's own name for this setup, e.g. "My Delta". */
    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "userBroker")
    private List<TradingAccount> tradingAccounts = new ArrayList<>();

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

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Broker getBroker() { return broker; }
    public void setBroker(Broker broker) { this.broker = broker; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public List<TradingAccount> getTradingAccounts() { return tradingAccounts; }
    public void setTradingAccounts(List<TradingAccount> tradingAccounts) { this.tradingAccounts = tradingAccounts; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
