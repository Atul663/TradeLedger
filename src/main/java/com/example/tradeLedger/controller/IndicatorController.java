package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.IndicatorRequest;
import com.example.tradeLedger.dto.IndicatorResponse;
import com.example.tradeLedger.service.IndicatorCatalogService;
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
 * Indicator management over the {@code indicators} table.
 *
 * An indicator's parameters ARE its {@code paramSchema} - there is no parameter
 * table behind it, so declaring a knob is an edit to this row. The values a user
 * picks for them live on their own strategy, in
 * {@code user_strategy_indicators.params}, and are validated against this schema
 * every time they are written.
 */
@RestController
@RequestMapping("/api/v1/indicators")
@Tag(name = "Indicators", description = "Compute primitives (EMA, RSI, ...) and their parameter schemas")
public class IndicatorController extends SecuredController {

    private static final Logger log = LoggerFactory.getLogger(IndicatorController.class);

    private final IndicatorCatalogService indicatorService;

    public IndicatorController(IndicatorCatalogService indicatorService) {
        this.indicatorService = indicatorService;
    }

    @GetMapping
    @Operation(summary = "List indicators, optionally filtered by active flag")
    public List<IndicatorResponse> list(@RequestParam(required = false) Boolean active) {
        log.info("GET indicators active={} | user={}", active, currentEmail());
        return indicatorService.list(active);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one indicator with its parameter schema")
    public IndicatorResponse get(@PathVariable UUID id) {
        log.info("GET indicator={} | user={}", id, currentEmail());
        return indicatorService.get(id);
    }

    @GetMapping("/by-name/{name}")
    @Operation(summary = "Get one indicator by its unique name, e.g. EMA")
    public IndicatorResponse getByName(@PathVariable String name) {
        log.info("GET indicator name='{}' | user={}", name, currentEmail());
        return indicatorService.getByName(name);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an indicator and its parameter schema",
            description = "The paramSchema IS the parameter declaration - there is no parameter "
                    + "table behind it. Every entry needs a type and a default. The name is "
                    + "uppercased on save.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = IndicatorRequest.class),
                    examples = {
                            @ExampleObject(name = "Numeric knobs with a cross-field rule",
                                    description = "lt names another key of the SAME indicator, so "
                                            + "d must stay under k.",
                                    value = """
                                            { "name": "ema fast slow",
                                              "paramSchema": {
                                                "k": { "type": "int", "min": 1, "max": 300, "default": 21 },
                                                "d": { "type": "int", "min": 1, "max": 300, "default": 9, "lt": "k" }
                                              } }"""),
                            @ExampleObject(name = "Mixed types including an enum",
                                    value = """
                                            { "name": "supertrend",
                                              "paramSchema": {
                                                "period":     { "type": "int",     "min": 1,   "max": 100, "default": 10 },
                                                "multiplier": { "type": "decimal", "min": 0.5, "max": 10,  "default": 3.0 },
                                                "source":     { "type": "enum", "options": ["close","hl2","hlc3"], "default": "close" }
                                              } }""")
                    }))
    public IndicatorResponse create(@RequestBody IndicatorRequest request) {
        log.info("CREATE indicator '{}' | user={}",
                request != null ? request.getName() : null, currentEmail());
        return indicatorService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an indicator's parameter schema or active flag")
    public IndicatorResponse update(@PathVariable UUID id, @RequestBody IndicatorRequest request) {
        log.info("UPDATE indicator={} | user={}", id, currentEmail());
        return indicatorService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an indicator; refused while a strategy rule tree references it")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("DELETE indicator={} | user={}", id, currentEmail());
        indicatorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
