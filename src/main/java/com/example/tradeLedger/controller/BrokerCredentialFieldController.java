package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.BrokerCredentialFieldRequest;
import com.example.tradeLedger.dto.BrokerCredentialFieldResponse;
import com.example.tradeLedger.service.BrokerCredentialFieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * CRUD over {@code broker_credential_fields} - the catalog describing what each
 * broker's credential form renders: which credential the input binds to, what it
 * is called, what type it is and where it sits.
 *
 * <b>Descriptors, not credentials.</b> The VALUE of every field described here is
 * a column on {@code broker_credentials}, encrypted, and is written through
 * {@code /api/v1/my-brokers} and {@code /api/v1/trading-accounts}. Nothing here
 * holds or reveals a secret - a {@code secret} field is refused a default value
 * for that reason. This is the credential-form half of what
 * {@code /api/v1/fixed-parameters} does for strategy knobs.
 *
 * <b>The read a UI makes is the list filtered by broker.</b> Rows come back in
 * form order - group, then position, then field key - so rendering is a loop, and
 * a new broker is an INSERT here rather than a UI release.
 *
 * <p><b>Platform master data, not per-user.</b> Like {@code brokers} these rows
 * are shared, so reads are unscoped and a write changes what every user sees.
 * <b>Not yet gated by role</b>: any authenticated caller can write. Put these
 * behind an admin check before the API is open to end users - the same caveat
 * {@link BrokerController} carries.
 */
@RestController
@RequestMapping("/api/v1/broker-credential-fields")
@Tag(name = "Broker credential fields",
        description = "The label, type, order and bounds of every input on a broker's credential form")
public class BrokerCredentialFieldController extends SecuredController {

    private static final Logger log = LoggerFactory.getLogger(BrokerCredentialFieldController.class);

    private final BrokerCredentialFieldService fieldService;

    public BrokerCredentialFieldController(BrokerCredentialFieldService fieldService) {
        this.fieldService = fieldService;
    }

    @GetMapping
    @Operation(summary = "List credential fields, ordered the way a form lays them out",
            description = "By group, then position within it, then field key. Filter by "
                    + "`brokerId` or `brokerCode` to get one broker's form - the read a UI "
                    + "makes - by `group` to separate what the user types (`credentials`) from "
                    + "what the auth flow produces (`session`), and by `active` to hide retired "
                    + "descriptors.\n\n"
                    + "Unfiltered it returns every broker's form, ordered by broker name.",
            responses = @ApiResponse(responseCode = "200",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = BrokerCredentialFieldResponse.class)),
                            examples = @ExampleObject(name = "The Zerodha form",
                                    description = "Three inputs the user fills, then the token "
                                            + "the Kite login fills for them.",
                                    value = """
                                            [ { "id": "5c1f9e2a-1d3b-4c6e-9a80-2b7f4d6e8a10",
                                                "brokerId": "8f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
                                                "brokerCode": "ZERODHA",
                                                "brokerName": "Zerodha",
                                                "fieldKey": "api_key",
                                                "label": "API Key",
                                                "description": "From your Kite Connect app.",
                                                "placeholder": "abcd1234efgh5678",
                                                "dataType": "text",
                                                "defaultValue": null,
                                                "validation": {},
                                                "fieldGroup": "credentials",
                                                "displayOrder": 1,
                                                "required": true,
                                                "userSupplied": true,
                                                "helpUrl": "https://developers.kite.trade",
                                                "active": true,
                                                "createdAt": "2026-08-27T14:42:10.221+05:30",
                                                "updatedAt": "2026-08-27T14:42:10.221+05:30" },
                                              { "fieldKey": "api_secret", "label": "API Secret",
                                                "dataType": "secret", "defaultValue": null,
                                                "validation": {}, "fieldGroup": "credentials",
                                                "displayOrder": 2, "required": true,
                                                "userSupplied": true, "active": true },
                                              { "fieldKey": "redirect_url", "label": "Redirect URL",
                                                "placeholder": "https://your-app.example.com/broker/zerodha/callback",
                                                "dataType": "url", "fieldGroup": "credentials",
                                                "displayOrder": 3, "required": true,
                                                "userSupplied": true, "active": true },
                                              { "fieldKey": "access_token", "label": "Access Token",
                                                "description": "Filled in by the Kite login, not typed.",
                                                "dataType": "secret", "fieldGroup": "session",
                                                "displayOrder": 4, "required": false,
                                                "userSupplied": false, "active": true } ]"""))))
    public List<BrokerCredentialFieldResponse> list(@RequestParam(required = false) UUID brokerId,
                                                    @RequestParam(required = false) String brokerCode,
                                                    @RequestParam(required = false) String group,
                                                    @RequestParam(required = false) Boolean active) {
        log.info("GET broker credential fields brokerId={} brokerCode={} group={} active={} | user={}",
                brokerId, brokerCode, group, active, currentEmail());
        return fieldService.list(brokerId, brokerCode, group, active);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one credential field descriptor")
    public BrokerCredentialFieldResponse get(@PathVariable UUID id) {
        log.info("GET broker credential field={} | user={}", id, currentEmail());
        return fieldService.get(id);
    }

    @GetMapping("/by-key")
    @Operation(summary = "Get one descriptor by its business key: a broker and a credential column",
            description = "The lookup a form uses when it already holds the broker and the "
                    + "field it is rendering. `fieldKey` is matched case-insensitively.")
    public BrokerCredentialFieldResponse getByKey(@RequestParam UUID brokerId,
                                                  @RequestParam String fieldKey) {
        log.info("GET broker credential field broker={} key='{}' | user={}",
                brokerId, fieldKey, currentEmail());
        return fieldService.getByBrokerAndKey(brokerId, fieldKey);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a credential field descriptor",
            description = "`fieldKey` must name a real `broker_credentials` column - one of "
                    + "api_key, api_secret, access_token, refresh_token, totp_secret, "
                    + "redirect_url, client_id, vault_ref - and is UNIQUE per broker. Send the "
                    + "broker as `brokerId`, or as `brokerCode` if you have not resolved one.\n\n"
                    + "**A secret field cannot carry a `defaultValue`.** This table is "
                    + "plaintext platform metadata; a default on a masked field would be a "
                    + "working credential sitting in a catalog every user can read.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = BrokerCredentialFieldRequest.class),
                    examples = {
                            @ExampleObject(name = "A masked secret",
                                    description = "The Zerodha API secret: masked, required, no default.",
                                    value = """
                                            { "brokerCode": "ZERODHA",
                                              "fieldKey": "api_secret",
                                              "label": "API Secret",
                                              "description": "Shown once when the app is created.",
                                              "dataType": "secret",
                                              "fieldGroup": "credentials",
                                              "displayOrder": 2,
                                              "required": true,
                                              "helpUrl": "https://developers.kite.trade" }"""),
                            @ExampleObject(name = "A plain input with a bound",
                                    description = "A client id is not a secret, so it renders "
                                            + "unmasked - and the column it lands in is "
                                            + "varchar(100), which validation mirrors.",
                                    value = """
                                            { "brokerCode": "DHAN",
                                              "fieldKey": "client_id",
                                              "label": "Dhan Client ID",
                                              "placeholder": "1100123456",
                                              "dataType": "text",
                                              "validation": { "maxLength": 100 },
                                              "fieldGroup": "credentials",
                                              "displayOrder": 1,
                                              "required": true }"""),
                            @ExampleObject(name = "A callback URL",
                                    description = "A url field may carry a default, and it has "
                                            + "to be one a browser will follow.",
                                    value = """
                                            { "brokerCode": "UPSTOX",
                                              "fieldKey": "redirect_url",
                                              "label": "Redirect URL",
                                              "description": "Must match the redirect_uri registered on the app.",
                                              "dataType": "url",
                                              "defaultValue": "https://your-app.example.com/broker/upstox/callback",
                                              "fieldGroup": "credentials",
                                              "displayOrder": 3,
                                              "required": true }"""),
                            @ExampleObject(name = "Something the flow fills",
                                    description = "userSupplied false, so a form shows the "
                                            + "connection and its expiry instead of an input.",
                                    value = """
                                            { "brokerCode": "ANGELONE",
                                              "fieldKey": "access_token",
                                              "label": "Access Token (JWT)",
                                              "description": "Returned by generateSession and renewed automatically.",
                                              "dataType": "secret",
                                              "fieldGroup": "session",
                                              "displayOrder": 5,
                                              "required": false,
                                              "userSupplied": false }""")
                    }))
    public BrokerCredentialFieldResponse create(@RequestBody BrokerCredentialFieldRequest request) {
        log.info("CREATE broker credential field '{}' | user={}",
                request != null ? request.getFieldKey() : null, currentEmail());
        return fieldService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a credential field descriptor",
            description = "Partial - an absent field keeps its stored value. The type and the "
                    + "default are judged against the RESULTING row, so retyping a field to "
                    + "`secret` while a default is still stored on it is refused rather than "
                    + "half-applied. Send an empty string to clear the description, "
                    + "placeholder, default or help URL, and an empty object to clear the bounds.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = BrokerCredentialFieldRequest.class),
                    examples = {
                            @ExampleObject(name = "Reword the label",
                                    description = "What Angel One calls the value in api_secret.",
                                    value = """
                                            { "label": "MPIN" }"""),
                            @ExampleObject(name = "Move it up the form",
                                    value = """
                                            { "displayOrder": 1 }"""),
                            @ExampleObject(name = "Tighten the bounds",
                                    description = "Sent whole - validation replaces the stored "
                                            + "rules rather than merging into them.",
                                    value = """
                                            { "validation": { "minLength": 16, "maxLength": 64 } }"""),
                            @ExampleObject(name = "Make it optional",
                                    value = """
                                            { "required": false }"""),
                            @ExampleObject(name = "Retire it",
                                    description = "Hidden from forms, kept as a row - what to "
                                            + "do when a broker drops a field.",
                                    value = """
                                            { "active": false }""")
                    }))
    public BrokerCredentialFieldResponse update(@PathVariable UUID id,
                                                @RequestBody BrokerCredentialFieldRequest request) {
        log.info("UPDATE broker credential field={} | user={}", id, currentEmail());
        return fieldService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a credential field descriptor",
            description = "Always allowed: nothing points at a descriptor, and the credential "
                    + "column it describes is unaffected - the stored value simply stops being "
                    + "explained. Deactivating is the reversible path.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("DELETE broker credential field={} | user={}", id, currentEmail());
        fieldService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
