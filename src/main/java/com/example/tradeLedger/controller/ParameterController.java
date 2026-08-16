package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.ParameterResponse;
import com.example.tradeLedger.dto.ParameterUsageResponse;
import com.example.tradeLedger.service.ParameterCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The parameter catalog: every knob the platform knows about, and where each is
 * used.
 *
 * Read-only for now. Parameters are predefined system rows; authoring them
 * through the API belongs with custom strategy creation, and the model is already
 * shaped for it - a POST here plus a link row is the whole of it.
 */
@RestController
@RequestMapping("/api/v1/parameters")
@Tag(name = "Parameters", description = "The parameter catalog and its usages")
public class ParameterController extends SecuredController {

    private static final Logger log = LoggerFactory.getLogger(ParameterController.class);

    private final ParameterCatalogService parameterService;

    public ParameterController(ParameterCatalogService parameterService) {
        this.parameterService = parameterService;
    }

    @GetMapping
    @Operation(summary = "List the parameter catalog, optionally filtered by scope (signal/execution)")
    public List<ParameterResponse> list(@RequestParam(required = false) String scope) {
        log.info("GET parameters scope={} | user={}", scope, currentEmail());
        return parameterService.list(scope);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one parameter by its catalog id")
    public ParameterResponse get(@PathVariable Long id) {
        log.info("GET parameter={} | user={}", id, currentEmail());
        return parameterService.get(id);
    }

    @GetMapping("/by-code/{code}")
    @Operation(summary = "Get one parameter by its unique code, e.g. sl")
    public ParameterResponse getByCode(@PathVariable String code) {
        log.info("GET parameter code='{}' | user={}", code, currentEmail());
        return parameterService.getByCode(code);
    }

    @GetMapping("/{id}/usage")
    @Operation(summary = "Every indicator and strategy this parameter is attached to")
    public ParameterUsageResponse usage(@PathVariable Long id) {
        log.info("GET parameter={} usage | user={}", id, currentEmail());
        return parameterService.usage(id);
    }
}
