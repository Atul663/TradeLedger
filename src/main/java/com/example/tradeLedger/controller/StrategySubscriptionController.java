package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.StrategySubscriptionRequest;
import com.example.tradeLedger.dto.StrategySubscriptionResponse;
import com.example.tradeLedger.dto.StrategySubscriptionUpdateRequest;
import com.example.tradeLedger.service.StrategySubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * A user's own strategy configurations ({@code user_strategy_subscriptions}).
 *
 * Everything here is scoped to the authenticated caller: another user's
 * subscription is not visible, not editable, and reports as 404 rather than 403 -
 * the ownership filter is part of the query.
 */
@RestController
@RequestMapping("/api/v1/my-subscriptions")
@Tag(name = "My subscriptions", description = "The caller's running strategies - account, sizing and execution parameters")
public class StrategySubscriptionController extends SecuredController {

    private static final Logger log = LoggerFactory.getLogger(StrategySubscriptionController.class);

    private final StrategySubscriptionService subscriptionService;

    public StrategySubscriptionController(StrategySubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    @Operation(summary = "List the caller's subscriptions")
    public List<StrategySubscriptionResponse> list() {
        String email = currentEmail();
        log.info("GET subscriptions | user={}", email);
        return subscriptionService.list(email);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of the caller's subscriptions")
    public StrategySubscriptionResponse get(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("GET subscription={} | user={}", id, email);
        return subscriptionService.get(email, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Deploy a saved strategy onto one broker account",
            description = "The configuration comes from the strategy, one foreign key away. "
                    + "Only the per-account facts are in this body. For several accounts at "
                    + "once use POST /api/v1/my-strategies/{id}/deploy.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = StrategySubscriptionRequest.class),
                    examples = {
                            @ExampleObject(name = "Paper, default size",
                                    value = """
                                            { "userStrategyId": "us000000-1111-4222-8333-444444444444",
                                              "tradingAccountId": "ta000000-1111-4222-8333-444444444444",
                                              "tradeMode": "paper" }"""),
                            @ExampleObject(name = "Live at double size, under a risk profile",
                                    value = """
                                            { "userStrategyId": "us000000-1111-4222-8333-444444444444",
                                              "tradingAccountId": "ta000000-1111-4222-8333-444444444444",
                                              "riskProfileId": "4f5e6d7c-8b9a-4c1d-9e2f-3a4b5c6d7e8f",
                                              "multiplier": 2,
                                              "capitalAllocated": 500000,
                                              "executionMode": "CAPITAL_PERCENT",
                                              "tradeMode": "live" }""")
                    }))
    public StrategySubscriptionResponse create(@RequestBody StrategySubscriptionRequest request) {
        String email = currentEmail();
        log.info("DEPLOY strategy={} account={} | user={}",
                request != null ? request.getUserStrategyId() : null,
                request != null ? request.getTradingAccountId() : null, email);
        return subscriptionService.create(email, request);
    }

    /**
     * Partial update of how THIS account runs the strategy - its size, its risk
     * profile, paper or live, paused or not.
     *
     * Retuning the strategy itself is one PUT on /api/v1/my-strategies/{id},
     * which every broker running it picks up at once.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update the sizing, mode or active state of one deployment",
            description = "Only how THIS account runs the strategy. Retuning the strategy is "
                    + "one PUT on /api/v1/my-strategies/{id}, which every broker follows.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = StrategySubscriptionUpdateRequest.class),
                    examples = {
                            @ExampleObject(name = "Go live at double size",
                                    value = """
                                            { "tradeMode": "live", "multiplier": 2 }"""),
                            @ExampleObject(name = "Pause this broker",
                                    description = "Keeps the configuration. The shared computation "
                                            + "is retired once its last active deployment pauses.",
                                    value = """
                                            { "active": false }"""),
                            @ExampleObject(name = "Resume",
                                    value = """
                                            { "active": true }""")
                    }))
    public StrategySubscriptionResponse update(@PathVariable UUID id,
                                               @RequestBody StrategySubscriptionUpdateRequest request) {
        String email = currentEmail();
        log.info("UPDATE subscription={} | user={}", id, email);
        return subscriptionService.update(email, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Withdraw from this broker",
            description = "Retires the shared computation if this was its last active deployment. "
                    + "The strategy itself is untouched and stays deployed elsewhere.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("DELETE subscription={} | user={}", id, email);
        subscriptionService.delete(email, id);
        return ResponseEntity.noContent().build();
    }
}
