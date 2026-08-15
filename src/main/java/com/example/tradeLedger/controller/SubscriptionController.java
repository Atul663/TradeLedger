package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.SubscriptionRequest;
import com.example.tradeLedger.dto.SubscriptionResponse;
import com.example.tradeLedger.dto.SubscriptionUpdateRequest;
import com.example.tradeLedger.service.SubscriptionService;
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
 * A user's own strategy configurations ({@code subscriptions}).
 *
 * Everything here is scoped to the authenticated caller: another user's
 * subscription is not visible, not editable, and reports as 404 rather than 403 -
 * the ownership filter is part of the query.
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
@Tag(name = "Subscriptions", description = "Per-user strategy configurations and their execution parameters")
public class SubscriptionController extends SecuredController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    @Operation(summary = "List the caller's subscriptions")
    public List<SubscriptionResponse> list() {
        String email = currentEmail();
        log.info("GET subscriptions | user={}", email);
        return subscriptionService.list(email);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of the caller's subscriptions")
    public SubscriptionResponse get(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("GET subscription={} | user={}", id, email);
        return subscriptionService.get(email, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subscribe to a strategy with the caller's own parameters")
    public SubscriptionResponse create(@RequestBody SubscriptionRequest request) {
        String email = currentEmail();
        log.info("SUBSCRIBE request strategy='{}' | user={}",
                request != null ? request.getStrategyName() : null, email);
        return subscriptionService.create(email, request);
    }

    /**
     * Partial update. Parameters are merged over the current configuration, so
     * {@code {"params":{"fast":13}}} is a valid body: signal-scope changes
     * repoint the subscription at a different (possibly already shared) instance,
     * execution-scope changes stay local, and {@code active} pauses or resumes
     * without losing the configuration.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update parameters, sizing, mode or active state of a subscription")
    public SubscriptionResponse update(@PathVariable UUID id,
                                       @RequestBody SubscriptionUpdateRequest request) {
        String email = currentEmail();
        log.info("UPDATE subscription={} keys={} | user={}",
                id, request != null && request.getParams() != null ? request.getParams().keySet() : null, email);
        return subscriptionService.update(email, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Unsubscribe; retires the strategy instance if nobody else uses it")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("DELETE subscription={} | user={}", id, email);
        subscriptionService.delete(email, id);
        return ResponseEntity.noContent().build();
    }
}
