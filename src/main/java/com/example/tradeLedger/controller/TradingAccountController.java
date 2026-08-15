package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.TradingAccountRequest;
import com.example.tradeLedger.dto.TradingAccountResponse;
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
 * The caller's broker accounts. A subscription must name one, so this is part of
 * the strategy module's minimum surface rather than a separate feature.
 */
@RestController
@RequestMapping("/api/v1/trading-accounts")
@Tag(name = "Trading accounts", description = "The caller's broker accounts and their Vault credential references")
public class TradingAccountController extends SecuredController {

    private static final Logger log = LoggerFactory.getLogger(TradingAccountController.class);

    private final TradingAccountService tradingAccountService;

    public TradingAccountController(TradingAccountService tradingAccountService) {
        this.tradingAccountService = tradingAccountService;
    }

    @GetMapping
    @Operation(summary = "List the caller's trading accounts")
    public List<TradingAccountResponse> list() {
        String email = currentEmail();
        log.info("GET trading accounts | user={}", email);
        return tradingAccountService.list(email);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of the caller's trading accounts")
    public TradingAccountResponse get(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("GET trading account={} | user={}", id, email);
        return tradingAccountService.get(email, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a trading account, optionally with its Vault reference")
    public TradingAccountResponse create(@RequestBody TradingAccountRequest request) {
        String email = currentEmail();
        log.info("CREATE trading account '{}' | user={}",
                request != null ? request.getAccountName() : null, email);
        return tradingAccountService.create(email, request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename, deactivate or rotate the Vault reference of a trading account")
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
}
