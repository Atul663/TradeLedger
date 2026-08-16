package com.example.tradeLedger.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Table {@code indicator_parameters}: which parameters an indicator computes
 * with, by id.
 *
 * <pre>
 *   EMA CROSSOVER ──┬── K
 *                   └── D
 * </pre>
 *
 * UNIQUE {@code (indicator_id, parameter_id)} - an indicator cannot declare the
 * same parameter twice.
 *
 * {@link #defaultValue} and {@link #validation} are optional narrowings of the
 * catalog values, which is what lets two indicators share one {@link Parameter}
 * row while disagreeing about its range: EMA may allow {@code period} up to 300
 * where RSI stops at 100, with a single {@code period} row behind both.
 */
@Entity
@Table(name = "indicator_parameters",
        uniqueConstraints = @UniqueConstraint(name = "uq_indicator_parameters",
                columnNames = {"indicator_id", "parameter_id"}))
public class IndicatorParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "indicator_id", nullable = false)
    private IndicatorDef indicator;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "parameter_id", nullable = false)
    private Parameter parameter;

    /** Overrides the catalog default for this indicator only. */
    @Column(name = "default_value", columnDefinition = "text")
    private String defaultValue;

    /** Overrides the catalog validation for this indicator only. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation", columnDefinition = "jsonb")
    private String validation;

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

    /** The value in force: the per-indicator override when set, else the catalog value. */
    public String effectiveDefault() {
        return defaultValue != null ? defaultValue : parameter.getDefaultValue();
    }

    public String effectiveValidation() {
        return validation != null ? validation : parameter.getValidation();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public IndicatorDef getIndicator() { return indicator; }
    public void setIndicator(IndicatorDef indicator) { this.indicator = indicator; }

    public Parameter getParameter() { return parameter; }
    public void setParameter(Parameter parameter) { this.parameter = parameter; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getValidation() { return validation; }
    public void setValidation(String validation) { this.validation = validation; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
