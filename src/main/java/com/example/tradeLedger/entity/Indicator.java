package com.example.tradeLedger.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Table {@code indicators}: a compute primitive (EMA, RSI, ...).
 *
 * <b>Indicator parameters live here as a JSON schema, not as a separate table.</b>
 * The design is explicit about this (§4.8, and gap #7 "EAV dropped"): an
 * indicator declares the SHAPE of its knobs in {@link #paramSchema} -
 *
 * <pre>{"period":{"type":"int","min":2,"max":300}}</pre>
 *
 * - while the concrete VALUES arrive through two other places, never through a
 * per-indicator parameter row:
 * <ul>
 *   <li>{@link StrategyParamDefinition} - the strategy-level knob a user actually sets
 *       ({@code fast}, {@code slow}), with its own validation rules</li>
 *   <li>the {@code $key} bindings inside {@link StrategyTemplate#getRuleTree()}, which map
 *       those knobs onto this indicator's parameters
 *       ({@code {"ind":"EMA","params":{"period":"$fast"}}})</li>
 * </ul>
 *
 * That indirection is what lets one strategy use one indicator at several
 * parameterizations, and lets EMA(9) be shared across strategies.
 *
 * Note the schema gives this table no {@code updated_at} column: it is a
 * catalog of primitives, deactivated via {@link #active} rather than deleted
 * once anything references it.
 */
@Entity
@Table(name = "indicators")
public class Indicator {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** 'EMA', 'RSI' - matched against the "ind" values in a strategy rule tree. */
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    /** e.g. {@code {"period":{"type":"int","min":2,"max":300}}} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "param_schema", nullable = false, columnDefinition = "jsonb")
    private String paramSchema;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getParamSchema() { return paramSchema; }
    public void setParamSchema(String paramSchema) { this.paramSchema = paramSchema; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
