package com.example.tradeLedger.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Table {@code strategy_parameters}: the parameters that belong to a strategy
 * directly rather than to one of its indicators.
 *
 * <pre>
 *   EMA Crossover ──┬── SL
 *                   ├── TP
 *                   ├── Quantity
 *                   ├── Candle Duration
 *                   └── Trigger Duration
 * </pre>
 *
 * This is the second half of the ownership distinction the model needs: an
 * indicator parameter feeds a computation ({@link IndicatorParameter}), a
 * strategy parameter configures execution. Both point at the same
 * {@link Parameter} catalog, so a parameter has one definition and many
 * explicitly recorded usages.
 *
 * UNIQUE {@code (strategy_id, parameter_id)}. Rows for
 * {@link Parameter#isUniversal()} parameters are maintained automatically, so
 * every strategy carries SL/TP/quantity without anyone linking them by hand -
 * and they are still real rows, so the hierarchy stays uniform.
 */
@Entity
@Table(name = "strategy_parameters",
        uniqueConstraints = @UniqueConstraint(name = "uq_strategy_parameters",
                columnNames = {"strategy_id", "parameter_id"}))
public class StrategyParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_id", nullable = false)
    private Strategy strategy;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "parameter_id", nullable = false)
    private Parameter parameter;

    @Column(name = "default_value", columnDefinition = "text")
    private String defaultValue;

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

    public String effectiveDefault() {
        return defaultValue != null ? defaultValue : parameter.getDefaultValue();
    }

    public String effectiveValidation() {
        return validation != null ? validation : parameter.getValidation();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Strategy getStrategy() { return strategy; }
    public void setStrategy(Strategy strategy) { this.strategy = strategy; }

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
