package com.example.tradeLedger.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Table {@code indicators}: a compute primitive (EMA, RSI, ...) and the SHAPE of
 * its knobs.
 *
 * <b>The only pluggable thing on the platform, and therefore the only thing with
 * a dynamic schema.</b> An indicator declares what it takes in
 * {@link #paramSchema} -
 *
 * <pre>{"k":{"type":"int","min":1,"max":300,"default":21,"label":"Short (k)"},
 * "d":{"type":"int","min":1,"max":300,"default":9,"label":"Long (d)"}}</pre>
 *
 * - and a user's concrete values live in {@code user_strategy_indicators.params}
 * as jsonb, validated against this schema on every write. Two tables, one FK
 * between them, no parameter catalog and no link tables: the schema IS the
 * catalog.
 *
 * {@code default} is what applies when a user leaves a knob alone, which is how
 * retuning a platform default here moves every user who never set it and nobody
 * who did.
 *
 * A rule tree's {@code $key} bindings name keys in this schema, which is what
 * lets one template use one indicator at several parameterizations and lets
 * EMA(9) be shared across strategies.
 *
 * No {@code updated_at}: it is a catalog of primitives, deactivated via
 * {@link #active} rather than deleted once anything references it.
 */
@Entity
@Table(name = "indicators")
public class Indicator {

    /**
     * The legal {@code type} values inside {@link #paramSchema}.
     *
     * They live here because this is the only table left that declares a type at
     * all - every other value on the platform is a typed column, and its type is
     * the column's.
     */
    public static final String TYPE_INT = "int";
    public static final String TYPE_DECIMAL = "decimal";
    public static final String TYPE_BOOL = "bool";
    public static final String TYPE_ENUM = "enum";
    public static final String TYPE_TEXT = "text";

    public static final Set<String> TYPES =
            Set.of(TYPE_INT, TYPE_DECIMAL, TYPE_BOOL, TYPE_ENUM, TYPE_TEXT);

    /**
     * The optional display name of a parameter, inside its spec:
     * {@code {"k":{"type":"int","default":21,"label":"Long (d)"}}}.
     *
     * Optional because an indicator is authored by hand and a catalogue predating
     * labels must keep working; {@code IndicatorParams.labelled} fills the key in
     * on the way out, so a form always has one to render.
     */
    public static final String KEY_LABEL = "label";

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** 'EMA', 'RSI' - matched against the "ind" values in a strategy rule tree. */
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    /** e.g. {@code {"period":{"type":"int","min":2,"max":300,"default":9,"label":"Period"}}} */
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
