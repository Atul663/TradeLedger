package com.example.tradeLedger.dto;

import java.util.UUID;

/**
 * One value the user changed, addressed entirely by id.
 *
 * <pre>
 * {"indicatorId":"...", "parameterId":12, "value":"13"}   // indicator knob: k = 13
 * {"parameterId":31, "value":"2.5"}                       // strategy knob:  sl = 2.5
 * {"parameterId":31, "value":null}                        // clear: back to the global default
 * </pre>
 *
 * {@code indicatorId} null means the knob belongs to the strategy itself
 * ({@code sl}, {@code tp}, {@code quantity}, the durations) rather than to one of
 * its indicators. {@code slot} disambiguates only when a template uses the same
 * indicator more than once.
 *
 * A null {@code value} deletes the override row rather than storing a null, so
 * the knob falls back to the global default again - which is the only way to
 * "unset" without knowing what the default is.
 */
public class ParameterOverrideRequest {

    /** Null for a strategy-level knob. */
    private UUID indicatorId;

    /** Alternative to indicatorId: the id from a UserStrategyIndicatorResponse. */
    private UUID userStrategyIndicatorId;

    /** Only needed when one template uses an indicator twice. */
    private String slot;

    private Long parameterId;

    /** Null clears the override and restores the global default. */
    private String value;

    public UUID getIndicatorId() { return indicatorId; }
    public void setIndicatorId(UUID indicatorId) { this.indicatorId = indicatorId; }

    public UUID getUserStrategyIndicatorId() { return userStrategyIndicatorId; }
    public void setUserStrategyIndicatorId(UUID userStrategyIndicatorId) {
        this.userStrategyIndicatorId = userStrategyIndicatorId;
    }

    public String getSlot() { return slot; }
    public void setSlot(String slot) { this.slot = slot; }

    public Long getParameterId() { return parameterId; }
    public void setParameterId(Long parameterId) { this.parameterId = parameterId; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
