package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.ExchangeRequest;
import com.example.tradeLedger.dto.ExchangeResponse;
import com.example.tradeLedger.dto.RiskProfileRequest;
import com.example.tradeLedger.dto.RiskProfileResponse;
import com.example.tradeLedger.dto.SymbolRequest;
import com.example.tradeLedger.dto.SymbolResponse;
import com.example.tradeLedger.dto.UserRiskLimitRequest;
import com.example.tradeLedger.dto.UserRiskLimitResponse;
import com.example.tradeLedger.service.ReferenceDataService;
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
 * The lookups a strategy configuration form needs: exchanges, symbols and risk
 * profiles, plus the caller's own aggregate risk limits.
 *
 * The three catalogs are SHARED master data with no owner column, so writes here
 * are not scoped to the caller - see {@link ReferenceDataService} for the stance
 * that takes the place of authorization.
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

    @PostMapping("/exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an exchange",
            description = "Shared master data: the venue is visible to every user. `code` is "
                    + "what a strategy sends as exchangeCode; both name and code are UNIQUE and "
                    + "code is uppercased on save.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = ExchangeRequest.class),
                    examples = {
                            @ExampleObject(name = "NSE",
                                    value = """
                                            { "name": "National Stock Exchange of India",
                                              "code": "NSE",
                                              "description": "Indian equity and derivatives",
                                              "status": "active" }"""),
                            @ExampleObject(name = "BSE",
                                    value = """
                                            { "name": "BSE Limited", "code": "BSE" }""")
                    }))
    public ExchangeResponse createExchange(@RequestBody ExchangeRequest request) {
        log.info("CREATE exchange code={} | user={}",
                request != null ? request.getCode() : null, currentEmail());
        return referenceDataService.createExchange(request);
    }

    @PutMapping("/exchanges/{id}")
    @Operation(summary = "Update an exchange",
            description = "Partial. `code` cannot change once symbols hang off the venue - it is "
                    + "what clients resolve them by.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = ExchangeRequest.class),
                    examples = {
                            @ExampleObject(name = "Disable the venue",
                                    description = "Its symbols stop being selectable. The "
                                            + "alternative to deleting.",
                                    value = """
                                            { "status": "disabled" }"""),
                            @ExampleObject(name = "Rename",
                                    value = """
                                            { "name": "NSE India" }""")
                    }))
    public ExchangeResponse updateExchange(@PathVariable UUID id,
                                           @RequestBody ExchangeRequest request) {
        log.info("UPDATE exchange={} | user={}", id, currentEmail());
        return referenceDataService.updateExchange(id, request);
    }

    @DeleteMapping("/exchanges/{id}")
    @Operation(summary = "Delete an exchange; refused while any symbol belongs to it",
            description = "409 with the symbol count. Disable it instead - that is the "
                    + "non-destructive path, and it stops new strategies picking its symbols "
                    + "without disturbing the ones already running.")
    public ResponseEntity<Void> deleteExchange(@PathVariable UUID id) {
        log.info("DELETE exchange={} | user={}", id, currentEmail());
        referenceDataService.deleteExchange(id);
        return ResponseEntity.noContent().build();
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

    @PostMapping("/symbols")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a symbol",
            description = "A strategy points at the UNDERLYING it watches, so for the option "
                    + "strategies this platform runs that is normally instrumentType `index` "
                    + "or `spot`. UNIQUE per (exchange, symbol); the ticker is uppercased.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = SymbolRequest.class),
                    examples = {
                            @ExampleObject(name = "Index underlying (what a strategy watches)",
                                    description = "The sheet's INDEX cell. Set contractSize and "
                                            + "minQty from the current contract spec - lot sizes change.",
                                    value = """
                                            { "exchangeCode": "NSE",
                                              "symbol": "NIFTY",
                                              "baseAsset": "NIFTY",
                                              "quoteAsset": "INR",
                                              "instrumentType": "index",
                                              "contractSize": 75,
                                              "tickSize": 0.05,
                                              "minQty": 75,
                                              "active": true }"""),
                            @ExampleObject(name = "Stock underlying",
                                    description = "The sheet's STOCK cell - same shape, "
                                            + "instrumentType spot.",
                                    value = """
                                            { "exchangeCode": "NSE",
                                              "symbol": "RELIANCE",
                                              "baseAsset": "RELIANCE",
                                              "quoteAsset": "INR",
                                              "instrumentType": "spot",
                                              "contractSize": 500,
                                              "tickSize": 0.05,
                                              "minQty": 500 }"""),
                            @ExampleObject(name = "A fully-specified option contract",
                                    description = "optionType and strikePrice are REQUIRED when "
                                            + "instrumentType is option, and rejected otherwise. "
                                            + "Not what a strategy points at - the strategy picks "
                                            + "its strike by moneyness at entry time.",
                                    value = """
                                            { "exchangeCode": "NSE",
                                              "symbol": "NIFTY25000CE",
                                              "instrumentType": "option",
                                              "optionType": "CALL",
                                              "strikePrice": 25000,
                                              "expiryAt": "2026-08-27T15:30:00+05:30",
                                              "contractSize": 75,
                                              "tickSize": 0.05,
                                              "minQty": 75 }""")
                    }))
    public SymbolResponse createSymbol(@RequestBody SymbolRequest request) {
        log.info("CREATE symbol {}:{} | user={}",
                request != null ? request.getExchangeCode() : null,
                request != null ? request.getSymbol() : null, currentEmail());
        return referenceDataService.createSymbol(request);
    }

    @PutMapping("/symbols/{id}")
    @Operation(summary = "Update a symbol",
            description = "Partial. A symbol cannot move between exchanges - create it on the "
                    + "other venue instead.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = SymbolRequest.class),
                    examples = {
                            @ExampleObject(name = "Correct the lot size",
                                    description = "The usual edit - contract specs change.",
                                    value = """
                                            { "contractSize": 65, "minQty": 65 }"""),
                            @ExampleObject(name = "Retire it",
                                    description = "An expired contract. Strategies already "
                                            + "watching it keep running; new ones cannot pick it.",
                                    value = """
                                            { "active": false }""")
                    }))
    public SymbolResponse updateSymbol(@PathVariable UUID id, @RequestBody SymbolRequest request) {
        log.info("UPDATE symbol={} | user={}", id, currentEmail());
        return referenceDataService.updateSymbol(id, request);
    }

    @DeleteMapping("/symbols/{id}")
    @Operation(summary = "Delete a symbol; refused while any strategy watches it",
            description = "409 with the strategy count. Deactivate it instead - that is what "
                    + "retiring an expired contract should do.")
    public ResponseEntity<Void> deleteSymbol(@PathVariable UUID id) {
        log.info("DELETE symbol={} | user={}", id, currentEmail());
        referenceDataService.deleteSymbol(id);
        return ResponseEntity.noContent().build();
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

    @PostMapping("/risk-profiles")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a risk profile",
            description = "A named set of caps a DEPLOYMENT points at. Distinct from "
                    + "/api/v1/me/risk-limits, which is one row per user holding aggregate caps "
                    + "across all their deployments. Every cap is optional; absent means uncapped.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = RiskProfileRequest.class),
                    examples = {
                            @ExampleObject(name = "Conservative",
                                    value = """
                                            { "name": "Conservative",
                                              "description": "Tight daily stop, small size",
                                              "maxDailyLoss": 5000,
                                              "maxDrawdown": 10000,
                                              "maxPositionSize": 100000,
                                              "maxTotalExposure": 500000,
                                              "maxTradesPerDay": 10,
                                              "killSwitchEnabled": true }"""),
                            @ExampleObject(name = "Aggressive",
                                    value = """
                                            { "name": "Aggressive",
                                              "description": "Wider limits",
                                              "maxDailyLoss": 25000,
                                              "maxTotalExposure": 2500000,
                                              "maxTradesPerDay": 40 }"""),
                            @ExampleObject(name = "Daily loss cap only",
                                    description = "Every other cap left uncapped.",
                                    value = """
                                            { "name": "Daily stop only", "maxDailyLoss": 10000 }""")
                    }))
    public RiskProfileResponse createRiskProfile(@RequestBody RiskProfileRequest request) {
        log.info("CREATE risk profile name={} | user={}",
                request != null ? request.getName() : null, currentEmail());
        return referenceDataService.createRiskProfile(request);
    }

    @PutMapping("/risk-profiles/{id}")
    @Operation(summary = "Update a risk profile",
            description = "Partial. Deployments pointing at it pick the change up immediately.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = RiskProfileRequest.class),
                    examples = {
                            @ExampleObject(name = "Tighten the daily stop",
                                    value = """
                                            { "maxDailyLoss": 3000 }"""),
                            @ExampleObject(name = "Turn the kill switch off",
                                    value = """
                                            { "killSwitchEnabled": false }""")
                    }))
    public RiskProfileResponse updateRiskProfile(@PathVariable UUID id,
                                                 @RequestBody RiskProfileRequest request) {
        log.info("UPDATE risk profile={} | user={}", id, currentEmail());
        return referenceDataService.updateRiskProfile(id, request);
    }

    @DeleteMapping("/risk-profiles/{id}")
    @Operation(summary = "Delete a risk profile; refused while any deployment runs under it",
            description = "409. Point those deployments at another profile first - or at none, "
                    + "since riskProfileId is optional.")
    public ResponseEntity<Void> deleteRiskProfile(@PathVariable UUID id) {
        log.info("DELETE risk profile={} | user={}", id, currentEmail());
        referenceDataService.deleteRiskProfile(id);
        return ResponseEntity.noContent().build();
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
