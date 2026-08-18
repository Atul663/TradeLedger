package com.example.tradeLedger.dto;

import java.util.Map;

public class PlatformStrategyToggleResponse {

    private String strategyName;
    private boolean enabled;
    private Map<String, Object> config;
    private long updatedAt;

    public PlatformStrategyToggleResponse(String strategyName, boolean enabled, Map<String, Object> config, long updatedAt) {
        this.strategyName = strategyName;
        this.enabled = enabled;
        this.config = config;
        this.updatedAt = updatedAt;
    }

    public String getStrategyName() { return strategyName; }
    public boolean isEnabled() { return enabled; }
    public Map<String, Object> getConfig() { return config; }
    public long getUpdatedAt() { return updatedAt; }
}
