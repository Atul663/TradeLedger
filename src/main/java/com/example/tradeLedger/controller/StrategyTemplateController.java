package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.StrategyTemplateDetailResponse;
import com.example.tradeLedger.dto.StrategyTemplateRequest;
import com.example.tradeLedger.service.StrategyTemplateService;
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
 * The template catalog: the strategy logic a user picks from.
 *
 * A template is a rule tree over indicators and nothing else. Each response
 * carries the indicators that tree names together with their parameter schemas,
 * which is everything a "new strategy" form needs that varies per template - the
 * instrument, strike, ladder and exit fields are the same on all of them.
 *
 * Distinct from the pre-existing {@code /api/v1/strategy} endpoints, which manage
 * the platform-wide on/off switches in {@code platform_strategy_toggles} and are
 * left exactly as they were.
 */
@RestController
@RequestMapping("/api/v1/strategy-templates")
@Tag(name = "Strategy templates", description = "Strategy logic: rule trees and the indicators they use")
public class StrategyTemplateController extends SecuredController {

    private static final Logger log = LoggerFactory.getLogger(StrategyTemplateController.class);

    private final StrategyTemplateService strategyService;

    public StrategyTemplateController(StrategyTemplateService strategyService) {
        this.strategyService = strategyService;
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
    @Operation(summary = "Publish a new template",
            description = "A template is LOGIC only - a rule tree over indicators. There is no "
                    + "knob list: each indicator declares its own parameters, and every other "
                    + "setting is a fixed column on user_strategies. is_system is forced false.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = StrategyTemplateRequest.class),
                    examples = {
                            @ExampleObject(name = "One indicator",
                                    value = """
                                            { "name": "RSI Reversal",
                                              "description": "Long when RSI leaves oversold.",
                                              "ruleTree": { "entry": { "ind": "RSI", "params": { "period": "$period" } } } }"""),
                            @ExampleObject(name = "Two indicators combined",
                                    description = "Nodes nest freely under any object or array. "
                                            + "Each $key binds to a key of that indicator's own schema.",
                                    value = """
                                            {
                                              "name": "EMA with RSI filter",
                                              "description": "EMA averaging, gated on RSI.",
                                              "ruleTree": {
                                                "entry": {
                                                  "and": [
                                                    { "ind": "EMA Averaging", "params": { "k": "$k", "d": "$d" } },
                                                    { "ind": "RSI", "params": { "period": "$period" } }
                                                  ]
                                                }
                                              }
                                            }""")
                    }))
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

}
