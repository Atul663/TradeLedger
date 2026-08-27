package com.example.tradeLedger.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Table {@code broker_credential_fields}: what a credential form renders, per
 * broker.
 *
 * <p>The same idea as {@link FixedParameter}, for the other form on the platform.
 * A <b>descriptor catalog, not a value store</b>: the value of every field
 * described here is a column on {@link BrokerCredential}, encrypted where it
 * needs to be. Nothing joins to this table, no user row hangs off it, and
 * emptying it changes nothing except that a form has no labels.
 *
 * <pre>
 *   fieldKey  -&gt; the broker_credentials COLUMN the input binds to
 *   label     -&gt; what the input is called on screen
 *   dataType  -&gt; which input to render, and whether to mask it
 * </pre>
 *
 * <p><b>One row per (broker, field).</b> Zerodha wants a key, a secret and a
 * redirect URL; Dhan wants a client id and a pasted token; Angel One wants five
 * things, one of which it calls an MPIN. That is five different forms, and
 * without this table it is five layouts hard-coded in the UI - the same problem
 * {@code strategy_param_definitions} solves for strategy knobs. A new broker is
 * an INSERT, never a UI release.
 *
 * <p>{@link Broker#getAuthType()} stays: it is the coarse grouping that says
 * WHICH FLOW to run. This says which boxes that flow needs filled, which is
 * finer than three auth types can express - Zerodha and Upstox share an auth type
 * and do not share a form.
 */
@Entity
@Table(name = "broker_credential_fields")
public class BrokerCredentialField {

    /** Renders as a plain input. */
    public static final String TYPE_TEXT = "text";
    /** Renders masked, and is stored as ciphertext on {@link BrokerCredential}. */
    public static final String TYPE_SECRET = "secret";
    /** Renders as a URL input; the callback the broker redirects back to. */
    public static final String TYPE_URL = "url";

    public static final Set<String> TYPES = Set.of(TYPE_TEXT, TYPE_SECRET, TYPE_URL);

    /** What the user types in to connect. */
    public static final String GROUP_CREDENTIALS = "credentials";
    /** What the auth flow produces afterwards - shown as status, not as an input. */
    public static final String GROUP_SESSION = "session";

    public static final Set<String> GROUPS = Set.of(GROUP_CREDENTIALS, GROUP_SESSION);

    /**
     * The columns on {@code broker_credentials} a descriptor is allowed to bind
     * to, mirroring the CHECK constraint on the table.
     *
     * Held here as well as in the schema so the API can refuse a bad key with the
     * list of good ones instead of surfacing a constraint violation, and so the
     * two go out of step loudly - a column added to {@code broker_credentials}
     * that nobody adds here simply cannot be described.
     */
    public static final Set<String> FIELD_KEYS = Set.of(
            "api_key", "api_secret", "access_token", "refresh_token",
            "totp_secret", "redirect_url", "client_id", "vault_ref");

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** The broker whose form this field belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broker_id", nullable = false)
    private Broker broker;

    /**
     * The machine key, and half the business key of this table: the
     * {@code broker_credentials} column the input binds to - api_key,
     * totp_secret, client_id. UNIQUE per broker, and constrained by the table to
     * real column names, so a form binds a descriptor to a field without a
     * mapping and a typo cannot render an input that saves nowhere.
     */
    @Column(name = "field_key", nullable = false, length = 50)
    private String fieldKey;

    /**
     * What a human sees next to the input. Usually the obvious thing, sometimes
     * not: for Angel One this is MPIN, which is what {@code api_secret} actually
     * carries there.
     */
    @Column(name = "label", nullable = false, length = 100)
    private String label;

    /** Help text under the input: what this is and where the broker hands it over. */
    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** A sample of the shape, never a real value. */
    @Column(name = "placeholder", length = 200)
    private String placeholder;

    /** text | secret | url - see {@link #TYPES}. */
    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType = TYPE_TEXT;

    /** Pre-filled when the broker has a conventional value; almost always null. */
    @Column(name = "default_value", columnDefinition = "text")
    private String defaultValue;

    /** Bounds a form should enforce - {@code {"maxLength":100}}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation", columnDefinition = "jsonb")
    private String validation;

    /** credentials | session - see {@link #GROUPS}. */
    @Column(name = "field_group", nullable = false, length = 50)
    private String fieldGroup = GROUP_CREDENTIALS;

    /** Position within {@link #fieldGroup}. Ties break on fieldKey. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    /** Whether a form must refuse to submit without it. */
    @Column(name = "is_required", nullable = false)
    private boolean required = true;

    /**
     * False for a field the FLOW fills rather than the user: the Kite access
     * token arrives on the OAuth redirect, the Angel One jwt comes back from
     * generateSession. Those rows exist so a form can show a connection and its
     * expiry instead of an input nobody should type into.
     */
    @Column(name = "is_user_supplied", nullable = false)
    private boolean userSupplied = true;

    /** Where the user goes to get this one - the console page, not the home page. */
    @Column(name = "help_url", columnDefinition = "text")
    private String helpUrl;

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

    /** True when the field is masked on screen and encrypted at rest. */
    public boolean isSecret() {
        return TYPE_SECRET.equals(dataType);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Broker getBroker() { return broker; }
    public void setBroker(Broker broker) { this.broker = broker; }

    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPlaceholder() { return placeholder; }
    public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getValidation() { return validation; }
    public void setValidation(String validation) { this.validation = validation; }

    public String getFieldGroup() { return fieldGroup; }
    public void setFieldGroup(String fieldGroup) { this.fieldGroup = fieldGroup; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public boolean isUserSupplied() { return userSupplied; }
    public void setUserSupplied(boolean userSupplied) { this.userSupplied = userSupplied; }

    public String getHelpUrl() { return helpUrl; }
    public void setHelpUrl(String helpUrl) { this.helpUrl = helpUrl; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
