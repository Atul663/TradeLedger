package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.ParameterResponse;
import com.example.tradeLedger.dto.StrategyTemplateDetailResponse;
import com.example.tradeLedger.dto.StrategyParamDefinitionRequest;
import com.example.tradeLedger.dto.StrategyParamDefinitionResponse;
import com.example.tradeLedger.dto.StrategyTemplateRequest;
import com.example.tradeLedger.service.ParameterCatalogService;
import com.example.tradeLedger.service.StrategyTemplateService;
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
 * StrategyTemplate management over the {@code strategy_templates} table.
 *
 * Distinct from the pre-existing {@code /api/v1/strategy} endpoints, which
 * manage the platform-wide on/off switches in {@code platform_strategy_toggles} and are
 *
 * left exactly as they were.
 */
@RestController
@RequestMapping("/api/v1/strategy-templates")
@Tag(name = "Strategy templates", description = "Strategy templates, their rule trees and their tunable parameters")
public class StrategyTemplateController extends SecuredController {

    private static final Logger log = LoggerFactory.getLogger(StrategyTemplateController.class);

    private final StrategyTemplateService strategyService;
    private final ParameterCatalogService parameterService;

    public StrategyTemplateController(StrategyTemplateService strategyService,
                                      ParameterCatalogService parameterService) {
        this.strategyService = strategyService;
        this.parameterService = parameterService;
    }

    @GetMapping
    @Operation(summary = "List strategies, optionally filtered by active flag and name fragment")
    public List<StrategyTemplateDetailResponse> list(@RequestParam(required = false) Boolean active,
                                             @RequestParam(required = false) String search) {
        log.info("GET strategies active={} search='{}' | user={}", active, search, currentEmail());
        return strategyService.list(active, search);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one strategy with its parameters and referenced indicators")
    public StrategyTemplateDetailResponse get(@PathVariable UUID id) {
        log.info("GET strategy={} | user={}", id, currentEmail());
        return strategyService.get(id);
    }

    @GetMapping("/by-name/{name}")
    @Operation(summary = "Get one strategy by its unique name")
    public StrategyTemplateDetailResponse getByName(@PathVariable String name) {
        log.info("GET strategy name='{}' | user={}", name, currentEmail());
        return strategyService.getByName(name);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a strategy, optionally with its full parameter set")
    public StrategyTemplateDetailResponse create(@RequestBody StrategyTemplateRequest request) {
        log.info("CREATE strategy '{}' | user={}",
                request != null ? request.getName() : null, currentEmail());
        return strategyService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a strategy; a supplied params list replaces the knob set")
    public StrategyTemplateDetailResponse update(@PathVariable UUID id, @RequestBody StrategyTemplateRequest request) {
        log.info("UPDATE strategy={} | user={}", id, currentEmail());
        return strategyService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a strategy; refused while strategy instances reference it")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("DELETE strategy={} | user={}", id, currentEmail());
        strategyService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --------------------------------------------------------- the hierarchy

    /**
     * The strategy's own parameters - SL, TP and friends - without its indicators'.
     * The full hierarchy comes back from {@code GET /api/v1/strategy-templates/{id}}; this
     * is the one branch of it on its own.
     */
    @GetMapping("/{id}/parameters")
    @Operation(summary = "Parameters belonging to the strategy directly, by id")
    public List<ParameterResponse> parameters(@PathVariable UUID id) {
        log.info("GET strategy={} parameters | user={}", id, currentEmail());
        return parameterService.forStrategy(id);
    }

    // ------------------------------------------------- indicator parameters

    @GetMapping("/{id}/params")
    @Operation(summary = "List a strategy's parameter definitions")
    public List<StrategyParamDefinitionResponse> listParams(@PathVariable UUID id) {
        log.info("GET strategy={} params | user={}", id, currentEmail());
        return strategyService.listParams(id);
    }

    @PostMapping("/{id}/params")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add one parameter definition to a strategy")
    public StrategyParamDefinitionResponse addParam(@PathVariable UUID id,
                                                  @RequestBody StrategyParamDefinitionRequest request) {
        log.info("ADD param '{}' strategy={} | user={}",
                request != null ? request.getParameterKey() : null, id, currentEmail());
        return strategyService.addParam(id, request);
    }

    @PutMapping("/{id}/params/{paramId}")
    @Operation(summary = "Update one parameter definition")
    public StrategyParamDefinitionResponse updateParam(@PathVariable UUID id,
                                                     @PathVariable Long paramId,
                                                     @RequestBody StrategyParamDefinitionRequest request) {
        log.info("UPDATE param={} strategy={} | user={}", paramId, id, currentEmail());
        return strategyService.updateParam(id, paramId, request);
    }

    @DeleteMapping("/{id}/params/{paramId}")
    @Operation(summary = "Delete one parameter definition; refused while the rule tree binds it")
    public ResponseEntity<Void> deleteParam(@PathVariable UUID id, @PathVariable Long paramId) {
        log.info("DELETE param={} strategy={} | user={}", paramId, id, currentEmail());
        strategyService.deleteParam(id, paramId);
        return ResponseEntity.noContent().build();
    }
}
