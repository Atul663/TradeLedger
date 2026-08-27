package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;
import java.util.UUID;

/**
 * Create / update one credential-form field descriptor.
 *
 * PUT is partial: an absent field keeps its stored value. {@code description},
 * {@code placeholder}, {@code defaultValue}, {@code validation} and
 * {@code helpUrl} are the nullable ones - send an empty string, or an empty
 * object for validation, to clear them.
 */
@Schema(name = "BrokerCredentialFieldRequest",
        description = """
                One field on one broker's credential form: which credential it binds to, what \
                it is called, what type of input renders it and where it sits.

                A DESCRIPTOR, not a credential. The VALUE of the field lives on \
                broker_credentials, encrypted, and is written through \
                /api/v1/my-brokers and /api/v1/trading-accounts. Nothing here holds or reveals \
                a secret, which is why a secret field is not allowed to carry a defaultValue.

                fieldKey is the machine key and must name a real broker_credentials column, so \
                a form binds a descriptor to a field without a mapping. It is UNIQUE per \
                broker: one descriptor per column, per broker.""")
public class BrokerCredentialFieldRequest {

    @Schema(description = "The broker whose form this field belongs to. Required on create "
            + "unless brokerCode is sent.",
            example = "8f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f", format = "uuid")
    private UUID brokerId;

    @Schema(description = "The broker by catalog code, for a caller that has not resolved the "
            + "id. Ignored when brokerId is sent.",
            example = "ZERODHA", maxLength = 30)
    private String brokerCode;

    @Schema(description = "The broker_credentials column this input binds to.",
            example = "api_secret",
            allowableValues = {"api_key", "api_secret", "access_token", "refresh_token",
                    "totp_secret", "redirect_url", "client_id", "vault_ref"})
    private String fieldKey;

    @Schema(description = "What a human sees next to the input. Not always the field key: "
            + "Angel One calls its api_secret an MPIN.",
            example = "API Secret", maxLength = 100)
    private String label;

    @Schema(description = "Help text shown under the input.",
            example = "Shown once when the app is created. It signs the checksum that turns a "
                    + "request token into an access token.")
    private String description;

    @Schema(description = "A sample of the shape. Never send a real credential.",
            example = "abcd1234efgh5678", maxLength = 200)
    private String placeholder;

    @Schema(description = "Which input to render, and whether to mask it.",
            example = "secret", allowableValues = {"text", "secret", "url"},
            defaultValue = "text")
    private String dataType;

    @Schema(description = "What a form pre-fills. Refused on a secret field - a descriptor "
            + "must never carry a credential.",
            example = "https://your-app.example.com/broker/zerodha/callback")
    private String defaultValue;

    @Schema(description = "The bounds a form should enforce: minLength, maxLength, pattern.",
            example = "{\"maxLength\": 100}")
    private Map<String, Object> validation;

    @Schema(description = "credentials is what the user types in; session is what the auth "
            + "flow produces afterwards.",
            example = "credentials", allowableValues = {"credentials", "session"},
            defaultValue = "credentials")
    private String fieldGroup;

    @Schema(description = "Position within its group. Ties break on fieldKey.",
            example = "2", minimum = "0", defaultValue = "0")
    private Integer displayOrder;

    @Schema(description = "Whether a form must refuse to submit without it.",
            example = "true", defaultValue = "true")
    private Boolean required;

    @Schema(description = "False for a field the auth flow fills rather than the user - an "
            + "OAuth access token. A form shows those as status, not as an input.",
            example = "true", defaultValue = "true")
    private Boolean userSupplied;

    @Schema(description = "Where the user goes to get this one. Must be http or https.",
            example = "https://developers.kite.trade")
    private String helpUrl;

    @Schema(description = "An inactive descriptor is hidden from forms without being deleted.",
            example = "true", defaultValue = "true")
    private Boolean active;

    public UUID getBrokerId() { return brokerId; }
    public void setBrokerId(UUID brokerId) { this.brokerId = brokerId; }

    public String getBrokerCode() { return brokerCode; }
    public void setBrokerCode(String brokerCode) { this.brokerCode = brokerCode; }

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

    public Map<String, Object> getValidation() { return validation; }
    public void setValidation(Map<String, Object> validation) { this.validation = validation; }

    public String getFieldGroup() { return fieldGroup; }
    public void setFieldGroup(String fieldGroup) { this.fieldGroup = fieldGroup; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }

    public Boolean getUserSupplied() { return userSupplied; }
    public void setUserSupplied(Boolean userSupplied) { this.userSupplied = userSupplied; }

    public String getHelpUrl() { return helpUrl; }
    public void setHelpUrl(String helpUrl) { this.helpUrl = helpUrl; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
