package com.example.tradeLedger.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "platform_strategy_toggles")
public class PlatformStrategyToggle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String strategyName;

    private boolean enabled = false;

    @Column(columnDefinition = "TEXT")
    private String configJson;

    private long updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStrategyName() { return strategyName; }
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
