package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.FixedParameterGroupResponse;
import com.example.tradeLedger.dto.FixedParameterRequest;
import com.example.tradeLedger.dto.FixedParameterResponse;
import com.example.tradeLedger.service.FixedParameterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * CRUD over {@code fixed_parameters} - the catalog describing the platform's
 * FIXED knobs: their name, label, type, default and bounds.
 *
 * <b>Descriptors, not values.</b> A knob's VALUE is a typed column on
 * {@code user_strategies} or {@code user_strategy_subscriptions} and is written
 * through {@code /api/v1/my-strategies} and {@code /api/v1/subscriptions}.
 * Nothing here changes what a strategy runs with - it changes what a form shows
 * and what it pre-fills, which is the one thing the fixed fields had no home for.
 *
 * The dynamic half of the same job is {@code /api/v1/indicators}, where an
 * indicator declares its own knobs in {@code paramSchema}.
 */
@RestController
@RequestMapping("/api/v1/fixed-parameters")
@Tag(name = "Fixed parameters",
        description = "The name, label, type, default and bounds of every fixed strategy knob")
public class FixedParameterController extends SecuredController {

    private static final Logger log = LoggerFactory.getLogger(FixedParameterController.class);

    private final FixedParameterService fixedParameterService;

    public FixedParameterController(FixedParameterService fixedParameterService) {
        this.fixedParameterService = fixedParameterService;
    }

    @GetMapping
    @Operation(summary = "List fixed parameters, ordered the way a form lays them out",
            description = "By group, then position within it, then name. Filter by `group` to "
                    + "render one section, by `scope` to separate what changes the signal from "
                    + "what only changes execution, and by `active` to hide retired knobs.")
    public List<FixedParameterResponse> list(@RequestParam(required = false) String group,
                                             @RequestParam(required = false) String scope,
                                             @RequestParam(required = false) Boolean active) {
        log.info("GET fixed parameters group={} scope={} active={} | user={}",
                group, scope, active, currentEmail());
        return fixedParameterService.list(group, scope, active);
    }

    @GetMapping("/grouped")
    @Operation(summary = "List fixed parameters grouped by paramGroup",
            description = "The same rows as the flat list, arranged one group per `paramGroup` "
                    + "- the sections a form renders. Groups come back in catalog order, rows "
                    + "inside a group by position then name, and the same `group`, `scope` and "
                    + "`active` filters apply - `group` narrows it to that one group. "
                    + "Descriptors with no group collect in a single entry whose `paramGroup` "
                    + "is null.")
    public List<FixedParameterGroupResponse> listGrouped(@RequestParam(required = false) String group,
                                                         @RequestParam(required = false) String scope,
                                                         @RequestParam(required = false) Boolean active) {
        log.info("GET fixed parameters grouped group={} scope={} active={} | user={}",
                group, scope, active, currentEmail());
        return fixedParameterService.listGrouped(group, scope, active);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one fixed parameter")
    public FixedParameterResponse get(@PathVariable UUID id) {
        log.info("GET fixed parameter={} | user={}", id, currentEmail());
        return fixedParameterService.get(id);
    }

    @GetMapping("/by-name/{name}")
    @Operation(summary = "Get one fixed parameter by its unique name, e.g. slPct",
            description = "The lookup a form uses: the name is the API field name of the column "
                    + "the descriptor describes. Matched case-insensitively.")
    public FixedParameterResponse getByName(@PathVariable String name) {
        log.info("GET fixed parameter name='{}' | user={}", name, currentEmail());
        return fixedParameterService.getByName(name);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a fixed parameter descriptor",
            description = "`name` is the machine key and is UNIQUE case-insensitively. "
                    + "`defaultValue` is text whatever the type is and is parsed against "
                    + "`dataType` and `validation` on save - an int default that is not an "
                    + "integer, a decimal outside its own min/max, or an enum default that is "
                    + "not one of its options are all refused.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = FixedParameterRequest.class),
                    examples = {
                            @ExampleObject(name = "A bounded percentage",
                                    description = "The descriptor behind user_strategies.sl_pct.",
                                    value = """
                                            { "name": "slPct",
                                              "label": "Stop loss %",
                                              "description": "Percent move against the position that closes it.",
                                              "dataType": "decimal",
                                              "scope": "execution",
                                              "defaultValue": "2.5",
                                              "validation": { "min": 0, "max": 100 },
                                              "paramGroup": "exits",
                                              "displayOrder": 1,
                                              "required": false }"""),
                            @ExampleObject(name = "A choice",
                                    description = "options is REQUIRED for an enum, and the "
                                            + "default has to be one of them.",
                                    value = """
                                            { "name": "lotRule",
                                              "label": "Averaging rule",
                                              "dataType": "enum",
                                              "scope": "execution",
                                              "defaultValue": "FIXED",
                                              "validation": { "options": ["FIXED", "DOUBLE", "CUMULATIVE"] },
                                              "paramGroup": "sizing",
                                              "displayOrder": 1,
                                              "required": true }"""),
                            @ExampleObject(name = "A candle",
                                    description = "A timeframe default is normalized the same "
                                            + "way a strategy's is, so '5M' stores as '5m'.",
                                    value = """
                                            { "name": "candleDuration",
                                              "label": "Time frame",
                                              "dataType": "timeframe",
                                              "scope": "signal",
                                              "defaultValue": "5m",
                                              "paramGroup": "market",
                                              "displayOrder": 2,
                                              "required": true }"""),
                            @ExampleObject(name = "A whole number with a floor and a ceiling",
                                    value = """
                                            { "name": "averagingCount",
                                              "label": "Averaging count",
                                              "dataType": "int",
                                              "defaultValue": "0",
                                              "validation": { "min": 0, "max": 10 },
                                              "paramGroup": "sizing",
                                              "displayOrder": 3 }""")
                    }))
    public FixedParameterResponse create(@RequestBody FixedParameterRequest request) {
        log.info("CREATE fixed parameter '{}' | user={}",
                request != null ? request.getName() : null, currentEmail());
        return fixedParameterService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a fixed parameter descriptor",
            description = "Partial. The type, the default and the bounds are re-validated "
                    + "together against the RESULTING row, so retyping a knob while its stored "
                    + "default no longer fits is refused rather than half-applied. Send an empty "
                    + "string to clear the description, default or group, and an empty object to "
                    + "clear the bounds.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = FixedParameterRequest.class),
                    examples = {
                            @ExampleObject(name = "Retune the default a form pre-fills",
                                    value = """
                                            { "defaultValue": "3.0" }"""),
                            @ExampleObject(name = "Reword the label",
                                    value = """
                                            { "label": "Stop loss (%)" }"""),
                            @ExampleObject(name = "Widen the bounds",
                                    description = "Sent whole - validation replaces the stored "
                                            + "rules rather than merging into them.",
                                    value = """
                                            { "validation": { "min": 0, "max": 200 } }"""),
                            @ExampleObject(name = "Retire it",
                                    description = "Hidden from forms, kept as a row.",
                                    value = """
                                            { "active": false }""")
                    }))
    public FixedParameterResponse update(@PathVariable UUID id,
                                         @RequestBody FixedParameterRequest request) {
        log.info("UPDATE fixed parameter={} | user={}", id, currentEmail());
        return fixedParameterService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a fixed parameter descriptor",
            description = "Always allowed: nothing points at a descriptor, and the column it "
                    + "describes is unaffected. Deactivating is the reversible path and keeps "
                    + "the knob's label and default history.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("DELETE fixed parameter={} | user={}", id, currentEmail());
        fixedParameterService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
