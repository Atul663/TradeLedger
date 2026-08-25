package com.example.tradeLedger.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Table {@code strategy_templates}: the strategy LOGIC - "the math", and nothing
 * a user configures.
 *
 * A template declares which indicators it uses through {@link #ruleTree}, which
 * holds conditions over indicator references with {@code $key} bindings:
 *
 * <pre>
 * {"entry":{"ind":"EMA Crossover","params":{"k":"$k","d":"$d"}}}
 * </pre>
 *
 * That is the whole of the template-to-indicator relationship: {@code "ind"}
 * values resolve by name against {@link Indicator}, and each {@code $key} names a
 * parameter that indicator's own {@code param_schema} declares. There is no index
 * table beside it - the tree is read directly wherever the indicator set is
 * needed, which is the only way the two can never fall out of step.
 *
 * A template holds no defaults, no strikes, no sizing and no exits. Those are
 * typed columns on {@link UserStrategy}, because they belong to a user's
 * configuration rather than to the logic. New strategies are INSERTs here, never
 * new columns.
 */
@Entity
@Table(name = "strategy_templates")
public class StrategyTemplate {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    /** Bumped by the caller when the rule tree changes meaning. */
    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** Platform-supplied strategies are protected from edit and delete. */
    @Column(name = "is_system", nullable = false)
    private boolean system = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_tree", nullable = false, columnDefinition = "jsonb")
    private String ruleTree;

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

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isSystem() { return system; }
    public void setSystem(boolean system) { this.system = system; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getRuleTree() { return ruleTree; }
    public void setRuleTree(String ruleTree) { this.ruleTree = ruleTree; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
