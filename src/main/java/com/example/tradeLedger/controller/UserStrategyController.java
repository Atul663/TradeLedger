package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.StrategySubscriptionResponse;
import com.example.tradeLedger.dto.UserStrategyRequest;
import com.example.tradeLedger.dto.UserStrategyResponse;
import com.example.tradeLedger.dto.UserStrategyRuntimeResponse;
import com.example.tradeLedger.dto.UserStrategySubscribeRequest;
import com.example.tradeLedger.dto.UserStrategyUpdateRequest;
import com.example.tradeLedger.service.UserStrategyService;
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
 * A user's own customizations of the global strategy templates
 * ({@code user_strategies}).
 *
 * The global template, its indicators and the parameter catalog are shared and
 * are never written from here. A user strategy stores foreign keys and the values
 * the user actually changed - so a knob left alone follows the platform default
 * forever, and a knob overridden stays put.
 *
 * Two reads over the same rows, for the two consumers:
 * <ul>
 *   <li>{@code GET /{id}} - the UI shape: indicators, and every knob with its
 *       global default beside the user's value</li>
 *   <li>{@code GET /{id}/runtime} - the bot shape: the values in force, already
 *       split into signal and execution scope</li>
 * </ul>
 *
 * Everything is scoped to the authenticated caller: another user's strategy is
 * not visible, not editable, and reports as 404 rather than 403.
 */
@RestController
@RequestMapping("/api/v1/my-strategies")
@Tag(name = "My strategies",
        description = "A user's customizations of the global templates, and subscribing them")
public class UserStrategyController extends SecuredController {

    private static final Logger log = LoggerFactory.getLogger(UserStrategyController.class);

    private final UserStrategyService userStrategyService;

    public UserStrategyController(UserStrategyService userStrategyService) {
        this.userStrategyService = userStrategyService;
    }

    @GetMapping
    @Operation(summary = "List the caller's customized strategies")
    public List<UserStrategyResponse> list(@RequestParam(required = false) Boolean active,
                                           @RequestParam(required = false) UUID strategyId) {
        String email = currentEmail();
        log.info("GET user strategies active={} strategyId={} | user={}", active, strategyId, email);
        return userStrategyService.list(email, active, strategyId);
    }

    /**
     * The UI shape. Indicators and knobs come back with {@code defaultValue},
     * {@code customValue}, {@code effectiveValue} and {@code overridden} on every
     * row, so the whole form renders from one call with nothing resolved
     * client-side.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get one customized strategy with its indicators, defaults and overrides")
    public UserStrategyResponse get(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("GET user strategy={} | user={}", id, email);
        return userStrategyService.get(email, id);
    }

    /**
     * The bot shape. Same rows, resolved and coerced: each indicator with the
     * values it runs on, plus the signal / execution split the execution path
     * expects. No fallback logic left for the caller.
     */
    @GetMapping("/{id}/runtime")
    @Operation(summary = "Bot view: indicators and effective parameter values, already resolved")
    public UserStrategyRuntimeResponse runtime(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("GET user strategy={} runtime | user={}", id, email);
        return userStrategyService.runtime(email, id);
    }

    /**
     * A body with no {@code overrides} saves a faithful copy of the template
     * sitting entirely on global defaults; only the knobs listed get a row.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Customize a global template and save it under the caller's own name")
    public UserStrategyResponse create(@RequestBody UserStrategyRequest request) {
        String email = currentEmail();
        log.info("CREATE user strategy template='{}' name='{}' | user={}",
                request != null ? request.getStrategyName() : null,
                request != null ? request.getName() : null, email);
        return userStrategyService.create(email, request);
    }

    /**
     * Partial update. Overrides are applied entry by entry - a knob not mentioned
     * keeps its value, and an entry with a null {@code value} clears that override
     * so the knob returns to the global default.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Rename, re-tune, re-point at another market, or archive")
    public UserStrategyResponse update(@PathVariable UUID id,
                                       @RequestBody UserStrategyUpdateRequest request) {
        String email = currentEmail();
        log.info("UPDATE user strategy={} | user={}", id, email);
        return userStrategyService.update(email, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete; subscriptions already made from it keep running")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("DELETE user strategy={} | user={}", id, email);
        userStrategyService.delete(email, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * The row is not consumed: subscribing the same customization to a second
     * trading account is a second subscription.
     */
    @PostMapping("/{id}/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Put a customized strategy to work on a trading account")
    public StrategySubscriptionResponse subscribe(@PathVariable UUID id,
                                                  @RequestBody UserStrategySubscribeRequest request) {
        String email = currentEmail();
        log.info("SUBSCRIBE user strategy={} account={} | user={}",
                id, request != null ? request.getTradingAccountId() : null, email);
        return userStrategyService.subscribe(email, id, request);
    }
}
