package com.example.tradeLedger.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Table {@code strategy_param_definitions}: ONE ROW PER KNOB.
 *
 * Long-narrow so EMA params, RSI params and anything added later coexist without
 * schema changes - a new strategy's configuration form is an INSERT, never an
 * ALTER TABLE.
 *
 * {@link #scope} drives the split that keeps dedup alive across personal settings:
 * <ul>
 *   <li><b>signal</b>    &rarr; goes on {@link SharedStrategyConfig}, inside the config hash</li>
 *   <li><b>execution</b> &rarr; goes on {@link StrategySubscription}, personal, never hashed</li>
 * </ul>
 *
 * Note the PK is {@code bigserial} here, not uuid - this table is not on the hot
 * path, so the schema keeps it cheap.
 */
@Entity
@Table(name = "strategy_param_definitions",
        uniqueConstraints = @UniqueConstraint(name = "uq_param_definitions_strategy_key",
                columnNames = {"strategy_id", "parameter_key"}))
public class StrategyParamDefinition {

    public static final String SCOPE_SIGNAL = "signal";
    public static final String SCOPE_EXECUTION = "execution";

    public static final String TYPE_INT = "int";
    public static final String TYPE_DECIMAL = "decimal";
    public static final String TYPE_BOOL = "bool";
    public static final String TYPE_ENUM = "enum";
    public static final String TYPE_TIMEFRAME = "timeframe";
    public static final String TYPE_TEXT = "text";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_id", nullable = false)
    private StrategyTemplate strategy;

    /** StrategyTemplate-local: "fast" in two strategies does not collide. */
    @Column(name = "parameter_key", nullable = false, length = 100)
    private String parameterKey;

    /** int | decimal | bool | enum | timeframe | text */
    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType;

    /** signal | execution */
    @Column(name = "scope", nullable = false, length = 20)
    private String scope;

    @Column(name = "default_value", columnDefinition = "text")
    private String defaultValue;

    /** {@code {"min":2,"max":200}} / {@code {"options":[...]}} / {@code {"gt":"fast"}} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation", columnDefinition = "jsonb")
    private String validation;

    @Column(name = "display_label", length = 100)
    private String displayLabel;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "is_required", nullable = false)
    private boolean required = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public StrategyTemplate getStrategy() { return strategy; }
    public void setStrategy(StrategyTemplate strategy) { this.strategy = strategy; }

    public String getParameterKey() { return parameterKey; }
    public void setParameterKey(String parameterKey) { this.parameterKey = parameterKey; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getValidation() { return validation; }
    public void setValidation(String validation) { this.validation = validation; }

    public String getDisplayLabel() { return displayLabel; }
    public void setDisplayLabel(String displayLabel) { this.displayLabel = displayLabel; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
