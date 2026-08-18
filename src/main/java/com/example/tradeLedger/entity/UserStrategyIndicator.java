package com.example.tradeLedger.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Table {@code user_strategy_indicators}: one indicator usage inside a
 * {@link UserStrategy}.
 *
 * The foreign key points at {@link Indicator} - authored master data that is
 * never regenerated - rather than at {@code strategy_indicator_links}, which
 * {@code StrategyIndicatorLinkSync} rebuilds from the template's rule tree on
 * every save. That the indicator really belongs to the template is checked when
 * the row is written, not carried as a foreign key to a derived index.
 *
 * Nothing about the indicator is copied here. Its name, its parameter set and
 * their defaults are read through {@code indicator_parameter_links} at query
 * time, so a platform change to the catalog reaches every user's strategy for
 * free.
 *
 * {@link #slot} exists for the case where one template uses the same indicator
 * more than once - two plain EMAs rather than one composite EMA CROSSOVER. It is
 * null for every strategy that uses each indicator once, which is all of them
 * today, and it is part of the unique key so the two usages stay distinct rows
 * without a schema change later.
 */
@Entity
@Table(name = "user_strategy_indicators",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_strategy_indicators",
                columnNames = {"user_strategy_id", "indicator_id", "slot"}))
public class UserStrategyIndicator {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_strategy_id", nullable = false)
    private UserStrategy userStrategy;

    /** The catalog indicator, by id. Never a name. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "indicator_id", nullable = false)
    private Indicator indicator;

    /** 'fast' / 'slow' when one template uses an indicator twice; null otherwise. */
    @Column(name = "slot", length = 50)
    private String slot;

    /**
     * Lets a user park an optional indicator without deleting their tuning of it.
     * A disabled row contributes no parameters to the effective set.
     */
    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @OneToMany(mappedBy = "userStrategyIndicator", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserStrategyParameter> parameters = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UserStrategy getUserStrategy() { return userStrategy; }
    public void setUserStrategy(UserStrategy userStrategy) { this.userStrategy = userStrategy; }

    public Indicator getIndicator() { return indicator; }
    public void setIndicator(Indicator indicator) { this.indicator = indicator; }

    public String getSlot() { return slot; }
    public void setSlot(String slot) { this.slot = slot; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public List<UserStrategyParameter> getParameters() { return parameters; }
    public void setParameters(List<UserStrategyParameter> parameters) { this.parameters = parameters; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
