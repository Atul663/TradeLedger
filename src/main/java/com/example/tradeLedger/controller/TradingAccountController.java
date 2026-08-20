package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.BrokerCredentialRequest;
import com.example.tradeLedger.dto.BrokerCredentialResponse;
import com.example.tradeLedger.dto.TradingAccountRequest;
import com.example.tradeLedger.dto.TradingAccountResponse;
import com.example.tradeLedger.service.BrokerCredentialService;
import com.example.tradeLedger.service.TradingAccountService;
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
 * The accounts under a broker setup - what a strategy is actually subscribed
 * against.
 *
 * An account belongs to a {@code /api/v1/my-brokers} setup and inherits its API
 * key, so creating one needs only the setup and a name. Where an order goes is
 * decided by the symbol, which already knows its venue, so there is no exchange
 * field here.
 *
 * The {@code /credentials} sub-resource writes an <b>override</b>: only the
 * fields sent become this account's own, everything else keeps inheriting. That
 * is what makes "this one sub-account has its own session token" a one-field
 * write rather than a full copy of the setup's key that then drifts.
 *
 * Everything is scoped to the authenticated caller: another user's account is not
 * visible, not editable, and reports as 404 rather than 403.
 */
@RestController
@RequestMapping("/api/v1/trading-accounts")
@Tag(name = "Trading accounts", description = "The accounts under a broker setup, and their credential overrides")
public class TradingAccountController extends SecuredController {

    private static final Logger log = LoggerFactory.getLogger(TradingAccountController.class);

    private final TradingAccountService tradingAccountService;
    private final BrokerCredentialService credentialService;

    public TradingAccountController(TradingAccountService tradingAccountService,
                                    BrokerCredentialService credentialService) {
        this.tradingAccountService = tradingAccountService;
        this.credentialService = credentialService;
    }

    @GetMapping
    @Operation(summary = "List the caller's trading accounts, optionally within one setup")
    public List<TradingAccountResponse> list(@RequestParam(required = false) UUID userBrokerId) {
        String email = currentEmail();
        log.info("GET trading accounts setup={} | user={}", userBrokerId, email);
        return tradingAccountService.list(email, userBrokerId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of the caller's trading accounts")
    public TradingAccountResponse get(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("GET trading account={} | user={}", id, email);
        return tradingAccountService.get(email, id);
    }

    /**
     * {@code brokerAccountId} is the broker's own id for this account - a Delta
     * sub-account id, a Dhan client id. It is what tells two accounts under one
     * shared API key apart when an order is placed.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account under one of the caller's broker setups")
    public TradingAccountResponse create(@RequestBody TradingAccountRequest request) {
        String email = currentEmail();
        log.info("CREATE trading account '{}' setup={} | user={}",
                request != null ? request.getAccountName() : null,
                request != null ? request.getUserBrokerId() : null, email);
        return tradingAccountService.create(email, request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename or deactivate; an account cannot move between setups")
    public TradingAccountResponse update(@PathVariable UUID id, @RequestBody TradingAccountRequest request) {
        String email = currentEmail();
        log.info("UPDATE trading account={} | user={}", id, email);
        return tradingAccountService.update(email, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a trading account; refused while active subscriptions use it")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("DELETE trading account={} | user={}", id, email);
        tradingAccountService.delete(email, id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------ credentials

    /**
     * This account's effective view: the setup's values with its own overrides
     * applied field by field, plus {@code overriddenFields} naming which were its
     * own. An empty list means it is running entirely on the setup's key.
     */
    @GetMapping("/{id}/credentials")
    @Operation(summary = "Effective credential status: inherited from the setup unless overridden")
    public BrokerCredentialResponse getCredentials(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("GET credentials for trading account={} | user={}", id, email);
        return credentialService.getForAccount(email, id);
    }

    /**
     * Override one or more fields for this account alone. Clearing the last one
     * removes the override entirely and the account goes back to inheriting.
     */
    @PutMapping("/{id}/credentials")
    @Operation(summary = "Give this account its own value for one or more credential fields")
    public BrokerCredentialResponse putCredentials(@PathVariable UUID id,
                                                   @RequestBody BrokerCredentialRequest request) {
        String email = currentEmail();
        // The body holds secrets: log which account, never what was sent.
        log.info("PUT credential override for trading account={} | user={}", id, email);
        return credentialService.upsertForAccount(email, id, request);
    }

    @DeleteMapping("/{id}/credentials")
    @Operation(summary = "Drop the override; the account falls back to the setup's credentials")
    public ResponseEntity<Void> deleteCredentials(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("DELETE credential override for trading account={} | user={}", id, email);
        credentialService.deleteForAccount(email, id);
        return ResponseEntity.noContent().build();
    }
}
