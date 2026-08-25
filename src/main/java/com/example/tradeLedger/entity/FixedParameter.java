package com.example.tradeLedger.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Table {@code fixed_parameters}: what each FIXED knob is called, what type it
 * takes, and what applies when nobody touches it.
 *
 * <b>A descriptor catalog, not a value store.</b> The fixed parameters are the
 * settings the platform defines for every strategy - the candle, the derivative,
 * the strike selection, the ladder, the exits, the deployment sizing - and their
 * VALUES stay where they already live: typed columns on {@code user_strategies}
 * and {@code user_strategy_subscriptions}, with real defaults and real CHECK
 * constraints behind them. Nothing in this table is read to decide what a
 * strategy runs with.
 *
 * What it holds is the metadata a form needs to render those fields without
 * hardcoding them - {@link #label}, {@link #dataType}, {@link #defaultValue},
 * {@link #validation}, {@link #paramGroup}, {@link #displayOrder} - and the one
 * place an admin can retune a suggested default or reword a label without a
 * deploy. It is the fixed-field counterpart of {@code indicators.param_schema},
 * which does the same job for the one pluggable thing on the platform.
 *
 * <b>It is deliberately NOT the old {@code parameters} catalog.</b> That table
 * held user VALUES as {@code text} rows joined through link tables, so the same
 * fact lived in a row AND a column; it was dropped, and {@code SchemaMappingTest}
 * asserts it stays dropped. Nothing references this table, no user row hangs off
 * it, and emptying it changes no behaviour except how a form renders.
 *
 * <pre>
 *   fixed_parameters                    user_strategies
 *     name    'slPct'                     sl_pct numeric(6,2)   &lt;- the value
 *     label   'SL %'
 *     type    'decimal'
 *     default '2.5'                     the descriptor, and the column it describes
 * </pre>
 */
@Entity
@Table(name = "fixed_parameters",
        uniqueConstraints = @UniqueConstraint(name = "uq_fixed_parameters_name",
                columnNames = "name"),
        check = @CheckConstraint(name = "ck_fixed_parameters_display_order",
                constraint = "display_order >= 0"))
public class FixedParameter {

    /**
     * The legal {@link #dataType} values.
     *
     * The same vocabulary {@code indicators.param_schema} declares for its
     * entries, plus two the indicator side has no use for: {@code timeframe},
     * which the fixed side has two of (candle duration and trigger duration), and
     * {@code symbol}, which the fixed side has one of.
     */
    public static final String TYPE_INT = "int";
    public static final String TYPE_DECIMAL = "decimal";
    public static final String TYPE_BOOL = "bool";
    public static final String TYPE_ENUM = "enum";
    public static final String TYPE_TIMEFRAME = "timeframe";
    public static final String TYPE_TEXT = "text";

    /**
     * A choice, like {@link #TYPE_ENUM}, whose options are ROWS rather than a
     * fixed vocabulary - the active {@code symbols}.
     *
     * It is a type of its own because an enum's options are stored in
     * {@link #validation} and an admin's to author, and these are neither: a list
     * copied into this table would be stale the moment an instrument is listed or
     * retired. The knob declares what it is, and the read path fills
     * {@code validation.options} from the table on the way out - so nothing here
     * is stored, and nothing here can go stale.
     */
    public static final String TYPE_SYMBOL = "symbol";

    public static final Set<String> TYPES = Set.of(
            TYPE_INT, TYPE_DECIMAL, TYPE_BOOL, TYPE_ENUM, TYPE_TIMEFRAME, TYPE_TEXT, TYPE_SYMBOL);

    /**
     * Whether the knob changes WHAT is computed or only HOW it is executed.
     *
     * The same split {@code shared_strategy_configs} draws: a signal-scope knob is
     * part of a strategy's identity, so two users who differ on one cannot share a
     * computation. An execution-scope knob is personal and never enters the hash.
     */
    public static final String SCOPE_SIGNAL = "signal";
    public static final String SCOPE_EXECUTION = "execution";

    public static final Set<String> SCOPES = Set.of(SCOPE_SIGNAL, SCOPE_EXECUTION);

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The machine key, and the business key of this table - 'slPct', 'baseLot',
     * 'candleDuration'. UNIQUE, and by convention the API field name of the column
     * it describes, so a form binds a descriptor to a field without a mapping.
     */
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    /** What a human sees next to the field - 'SL %'. */
    @Column(name = "label", nullable = false, length = 100)
    private String label;

    /** Help text for the field. */
    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** int | decimal | bool | enum | timeframe | text - see {@link #TYPES}. */
    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType;

    /** signal | execution - see {@link #SCOPES}. */
    @Column(name = "scope", nullable = false, length = 20)
    private String scope = SCOPE_EXECUTION;

    /**
     * What applies when the user leaves the knob alone, as text.
     *
     * One column for six types, coerced by {@link #dataType} - the same trade the
     * old catalog made, and what makes it safe here is that nothing coerces it at
     * runtime. The column this describes carries the default the engine actually
     * uses; this is the suggestion a form pre-fills.
     */
    @Column(name = "default_value", columnDefinition = "text")
    private String defaultValue;

    /**
     * The bounds a form should enforce, mirroring the CHECK constraint behind the
     * column - {@code {"min":0,"max":100}}, or {@code {"options":["ATM","ITM","OTM"]}},
     * which an enum requires.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation", columnDefinition = "jsonb")
    private String validation;

    /** The section of the form it belongs to - 'Market', 'Instrument', 'Sizing', 'Exits'. */
    @Column(name = "param_group", length = 50)
    private String paramGroup;

    /** Position within {@link #paramGroup}. Ties break on name. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    /** Whether a form must refuse to submit without it. */
    @Column(name = "is_required", nullable = false)
    private boolean required = false;

    /** Hides the descriptor without deleting it - the non-destructive retire. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getValidation() { return validation; }
    public void setValidation(String validation) { this.validation = validation; }

    public String getParamGroup() { return paramGroup; }
    public void setParamGroup(String paramGroup) { this.paramGroup = paramGroup; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
