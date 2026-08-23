package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Create / update an exchange - the venue a symbol trades on.
 *
 * PUT is partial: an absent field keeps its stored value.
 */
@Schema(name = "ExchangeRequest",
        description = """
                A trading venue. SHARED platform master data - it has no owner, so anything \
                written here is visible to every user.

                code is the business key the rest of the API accepts (exchangeCode on a strategy). \
                Both name and code are UNIQUE, and code is uppercased on save. A disabled exchange \
                still resolves, but its symbols cannot be used.""")
public class ExchangeRequest {

    @Schema(description = "Display name. UNIQUE.",
            example = "National Stock Exchange of India", maxLength = 50)
    private String name;

    @Schema(description = "The business key. UNIQUE, uppercased on save - this is what a "
            + "strategy sends as exchangeCode.", example = "NSE", maxLength = 20)
    private String code;

    @Schema(example = "Indian equity and derivatives")
    private String description;

    @Schema(description = "A disabled exchange's symbols are refused when a strategy picks them.",
            example = "active", allowableValues = {"active", "disabled"}, defaultValue = "active")
    private String status;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
