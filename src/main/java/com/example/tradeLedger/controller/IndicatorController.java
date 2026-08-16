package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.IndicatorDefRequest;
import com.example.tradeLedger.dto.IndicatorDefResponse;
import com.example.tradeLedger.dto.ParameterResponse;
import com.example.tradeLedger.service.IndicatorDefinitionService;
import com.example.tradeLedger.service.ParameterCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Indicator management over the {@code indicator_defs} table.
 *
 * An indicator's parameters are its {@code paramSchema} - the design stores them
 * as a JSON schema on the indicator row rather than in a parameter table, so
 * parameter CRUD happens through create/update here. The VALUES a user picks for
 * those parameters live on the strategy, under
 * {@code /api/v1/strategies/{id}/params}.
 */
@RestController
@RequestMapping("/api/v1/indicators")
@Tag(name = "Indicators", description = "Compute primitives (EMA, RSI, ...) and their parameter schemas")
public class IndicatorController extends SecuredController {

    private static final Logger log = LoggerFactory.getLogger(IndicatorController.class);

    private final IndicatorDefinitionService indicatorService;
    private final ParameterCatalogService parameterService;

    public IndicatorController(IndicatorDefinitionService indicatorService,
                               ParameterCatalogService parameterService) {
        this.indicatorService = indicatorService;
        this.parameterService = parameterService;
    }

    @GetMapping
    @Operation(summary = "List indicators, optionally filtered by active flag")
    public List<IndicatorDefResponse> list(@RequestParam(required = false) Boolean active) {
        log.info("GET indicators active={} | user={}", active, currentEmail());
        return indicatorService.list(active);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one indicator with its parameter schema")
    public IndicatorDefResponse get(@PathVariable UUID id) {
        log.info("GET indicator={} | user={}", id, currentEmail());
        return indicatorService.get(id);
    }

    @GetMapping("/by-name/{name}")
    @Operation(summary = "Get one indicator by its unique name, e.g. EMA")
    public IndicatorDefResponse getByName(@PathVariable String name) {
        log.info("GET indicator name='{}' | user={}", name, currentEmail());
        return indicatorService.getByName(name);
    }

    /**
     * The second level of the hierarchy on its own, for a client that already has
     * an indicator id and does not want the whole strategy.
     */
    @GetMapping("/{id}/parameters")
    @Operation(summary = "The parameters of one indicator, by id")
    public List<ParameterResponse> parameters(@PathVariable UUID id) {
        log.info("GET indicator={} parameters | user={}", id, currentEmail());
        return parameterService.forIndicator(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an indicator and its parameter schema")
    public IndicatorDefResponse create(@RequestBody IndicatorDefRequest request) {
        log.info("CREATE indicator '{}' | user={}",
                request != null ? request.getName() : null, currentEmail());
        return indicatorService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an indicator's parameter schema or active flag")
    public IndicatorDefResponse update(@PathVariable UUID id, @RequestBody IndicatorDefRequest request) {
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
