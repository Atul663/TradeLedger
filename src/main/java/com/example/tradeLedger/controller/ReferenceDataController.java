package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.BrokerResponse;
import com.example.tradeLedger.dto.ExchangeResponse;
import com.example.tradeLedger.dto.RiskProfileResponse;
import com.example.tradeLedger.dto.SymbolResponse;
import com.example.tradeLedger.dto.UserRiskLimitRequest;
import com.example.tradeLedger.dto.UserRiskLimitResponse;
import com.example.tradeLedger.service.ReferenceDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * The lookups a strategy configuration form needs: exchanges, symbols and risk
 * profiles, plus the caller's own aggregate risk limits.
 *
 * The three catalogs are read-only - see {@link ReferenceDataService} for why.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reference data", description = "Exchanges, symbols, risk profiles and the caller's risk limits")
public class ReferenceDataController extends SecuredController {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataController.class);

    private final ReferenceDataService referenceDataService;

    public ReferenceDataController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping("/exchanges")
    @Operation(summary = "List exchanges, optionally filtered by status")
    public List<ExchangeResponse> listExchanges(@RequestParam(required = false) String status) {
        log.info("GET exchanges status={} | user={}", status, currentEmail());
        return referenceDataService.listExchanges(status);
    }

    @GetMapping("/exchanges/{id}")
    @Operation(summary = "Get one exchange")
    public ExchangeResponse getExchange(@PathVariable UUID id) {
        log.info("GET exchange={} | user={}", id, currentEmail());
        return referenceDataService.getExchange(id);
    }

    @GetMapping("/brokers")
    @Operation(summary = "List the brokers an account can trade through")
    public List<BrokerResponse> listBrokers(@RequestParam(defaultValue = "true") boolean activeOnly) {
        log.info("GET brokers activeOnly={} | user={}", activeOnly, currentEmail());
        return referenceDataService.listBrokers(activeOnly);
    }

    @GetMapping("/brokers/{id}")
    @Operation(summary = "Get one broker")
    public BrokerResponse getBroker(@PathVariable UUID id) {
        log.info("GET broker={} | user={}", id, currentEmail());
        return referenceDataService.getBroker(id);
    }

    @GetMapping("/symbols")
    @Operation(summary = "List symbols, optionally scoped to one exchange")
    public List<SymbolResponse> listSymbols(@RequestParam(required = false) UUID exchangeId,
                                            @RequestParam(defaultValue = "true") boolean activeOnly) {
        log.info("GET symbols exchange={} activeOnly={} | user={}", exchangeId, activeOnly, currentEmail());
        return referenceDataService.listSymbols(exchangeId, activeOnly);
    }

    @GetMapping("/symbols/{id}")
    @Operation(summary = "Get one symbol")
    public SymbolResponse getSymbol(@PathVariable UUID id) {
        log.info("GET symbol={} | user={}", id, currentEmail());
        return referenceDataService.getSymbol(id);
    }

    @GetMapping("/risk-profiles")
    @Operation(summary = "List the reusable per-subscription risk profiles")
    public List<RiskProfileResponse> listRiskProfiles() {
        log.info("GET risk profiles | user={}", currentEmail());
        return referenceDataService.listRiskProfiles();
    }

    @GetMapping("/risk-profiles/{id}")
    @Operation(summary = "Get one risk profile")
    public RiskProfileResponse getRiskProfile(@PathVariable UUID id) {
        log.info("GET risk profile={} | user={}", id, currentEmail());
        return referenceDataService.getRiskProfile(id);
    }

    @GetMapping("/me/risk-limits")
    @Operation(summary = "Get the caller's aggregate risk limits across every subscription")
    public UserRiskLimitResponse getRiskLimits() {
        String email = currentEmail();
        log.info("GET risk limits | user={}", email);
        return referenceDataService.getRiskLimits(email);
    }

    @PutMapping("/me/risk-limits")
    @Operation(summary = "Set the caller's aggregate risk limits")
    public UserRiskLimitResponse setRiskLimits(@RequestBody UserRiskLimitRequest request) {
        String email = currentEmail();
        log.info("PUT risk limits | user={}", email);
        return referenceDataService.setRiskLimits(email, request);
    }
}
