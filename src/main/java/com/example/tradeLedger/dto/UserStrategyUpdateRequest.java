package com.example.tradeLedger.dto;

import java.util.List;
import java.util.UUID;

/**
 * Partial update of a user strategy. Every field is optional; only what is
 * present changes.
 *
 * {@code overrides} is applied entry by entry, not as a replacement set: a knob
 * not mentioned keeps whatever it had, and an entry with a null {@code value}
 * clears that one override so the knob returns to the global default.
 *
 * The template is not changeable here - a different template means a different
 * indicator and parameter set, which is a new user strategy rather than an edit.
 */
public class UserStrategyUpdateRequest {

    private String name;

    private String description;

    private UUID symbolId;

    private String symbol;

    private String exchangeCode;

    private String timeframe;

    /** Applied entry by entry; null value clears that override. */
    private List<ParameterOverrideRequest> overrides;

    /** Enable or disable one indicator usage without losing its tuning. */
    private List<IndicatorToggle> indicators;

    /** Archive without deleting. */
    private Boolean active;

    /** Turns one indicator usage on or off, addressed by id. */
    public static class IndicatorToggle {
        private UUID userStrategyIndicatorId;
        private UUID indicatorId;
        private String slot;
        private Boolean enabled;

        public UUID getUserStrategyIndicatorId() { return userStrategyIndicatorId; }
        public void setUserStrategyIndicatorId(UUID id) { this.userStrategyIndicatorId = id; }

        public UUID getIndicatorId() { return indicatorId; }
        public void setIndicatorId(UUID indicatorId) { this.indicatorId = indicatorId; }

        public String getSlot() { return slot; }
        public void setSlot(String slot) { this.slot = slot; }

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getSymbolId() { return symbolId; }
    public void setSymbolId(UUID symbolId) { this.symbolId = symbolId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getExchangeCode() { return exchangeCode; }
    public void setExchangeCode(String exchangeCode) { this.exchangeCode = exchangeCode; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    public List<ParameterOverrideRequest> getOverrides() { return overrides; }
    public void setOverrides(List<ParameterOverrideRequest> overrides) { this.overrides = overrides; }

    public List<IndicatorToggle> getIndicators() { return indicators; }
    public void setIndicators(List<IndicatorToggle> indicators) { this.indicators = indicators; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
