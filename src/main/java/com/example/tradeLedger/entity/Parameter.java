package com.example.tradeLedger.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Table {@code parameters}: the canonical catalog - one row per distinct knob the
 * platform knows about, with a stable id.
 *
 * A parameter exists independently of whoever uses it. {@code SL} is one row,
 * linked from every strategy that has a stop loss; {@code K} is one row, linked
 * from the indicator that computes with it. That is what makes the hierarchy
 * navigable by id in both directions - "which parameters does this indicator
 * have" and "where is this parameter used" are both index lookups against
 * {@link IndicatorParameter} / {@link StrategyParameter} rather than a scan of
 * JSON documents.
 *
 * {@link #code} is the business key and the wire name: it is what a subscribe
 * request sends, what {@code strategy_param_defs.parameter_key} is generated
 * from, and what a rule tree binds with {@code $code}. {@link #name} is the
 * human label and may be changed freely.
 *
 * {@link #scope} decides which side of the dedup line a value falls on, and is a
 * property of the parameter itself rather than of any one usage - {@code k} is
 * signal-scope wherever it appears, {@code sl} is execution-scope wherever it
 * appears. See {@link StrategyParamDef} for what that split buys.
 *
 * Ranges and defaults live here as the canonical values, and may be narrowed per
 * usage on the link row - EMA and RSI can share a {@code period} parameter while
 * declaring different maxima.
 */
@Entity
@Table(name = "parameters")
public class Parameter {

    public static final String SCOPE_SIGNAL = StrategyParamDef.SCOPE_SIGNAL;
    public static final String SCOPE_EXECUTION = StrategyParamDef.SCOPE_EXECUTION;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Business key and wire name: 'k', 'sl', 'candle_duration'. UNIQUE. */
    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    /** Display label: 'K', 'SL', 'Candle Duration'. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** int | decimal | bool | enum | timeframe | text */
    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType;

    /** signal (hashed, shared) | execution (personal, never hashed) */
    @Column(name = "scope", nullable = false, length = 20)
    private String scope;

    @Column(name = "default_value", columnDefinition = "text")
    private String defaultValue;

    /** {@code {"min":2,"max":300}} - canonical rules, narrowable per usage. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation", columnDefinition = "jsonb")
    private String validation;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /**
     * Attached to every strategy automatically.
     *
     * SL, TP, quantity and the durations are universal - every strategy has them
     * with the same meaning. Marking them here means a new strategy gets them
     * without anyone remembering to link them, while the link rows still exist so
     * the hierarchy stays explicit and id-addressable.
     */
    @Column(name = "is_universal", nullable = false)
    private boolean universal = false;

    /** Catalog-wide ordering, so a form renders SL before TP before Quantity. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    /** Platform-supplied catalog rows are protected from edit and delete. */
    @Column(name = "is_system", nullable = false)
    private boolean system = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getValidation() { return validation; }
    public void setValidation(String validation) { this.validation = validation; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isUniversal() { return universal; }
    public void setUniversal(boolean universal) { this.universal = universal; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public boolean isSystem() { return system; }
    public void setSystem(boolean system) { this.system = system; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
