package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.IndicatorPlanResponse;
import com.example.tradeLedger.dto.StrategyInstanceResponse;
import com.example.tradeLedger.service.StrategyInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Read-only view of {@code strategy_instances} - the shared, content-addressed
 * configurations that subscriptions point at.
 *
 * There is no create/update/delete here on purpose: instances are immutable and
 * appear only as a side effect of subscribing. The rows carry no user identity,
 * which is why they are readable without an ownership filter.
 */
@RestController
@RequestMapping("/api/v1/strategy-instances")
@Tag(name = "Strategy instances", description = "Shared, content-addressed strategy configurations")
public class StrategyInstanceController extends SecuredController {

    private static final Logger log = LoggerFactory.getLogger(StrategyInstanceController.class);

    private final StrategyInstanceService instanceService;

    public StrategyInstanceController(StrategyInstanceService instanceService) {
        this.instanceService = instanceService;
    }

    @GetMapping
    @Operation(summary = "List strategy instances, optionally filtered by status (active/retired)")
    public List<StrategyInstanceResponse> list(@RequestParam(required = false) String status) {
        log.info("GET strategy instances status={} | user={}", status, currentEmail());
        return instanceService.list(status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one strategy instance with its resolved indicator computations")
    public StrategyInstanceResponse get(@PathVariable UUID id) {
        log.info("GET strategy instance={} | user={}", id, currentEmail());
        return instanceService.get(id);
    }

    /**
     * The dedup report: active subscriptions against the distinct indicator
     * computations they actually cost. Three users on 9x21, 9x50 and 13x21 should
     * report 3 instances and 4 indicators.
     */
    @GetMapping("/indicator-plan")
    @Operation(summary = "Dedup report: subscriptions vs distinct indicator computations")
    public IndicatorPlanResponse indicatorPlan() {
        log.info("GET indicator plan | user={}", currentEmail());
        return instanceService.indicatorPlan();
    }
}
