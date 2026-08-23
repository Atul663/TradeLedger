package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.StrategyDeployRequest;
import com.example.tradeLedger.dto.StrategyDeploymentResponse;
import com.example.tradeLedger.dto.UserStrategyRequest;
import com.example.tradeLedger.dto.UserStrategyResponse;
import com.example.tradeLedger.dto.UserStrategyRuntimeResponse;
import com.example.tradeLedger.service.UserStrategyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * A user's own strategies ({@code user_strategies}).
 *
 * A strategy holds its whole configuration in typed columns - the market, the
 * instrument, the CE and PE strikes, the averaging ladder, the exits - plus one
 * row per indicator carrying that indicator's values. The template it is built
 * from supplies the logic and is never written from here.
 *
 * Everything is scoped to the authenticated caller: another user's strategy is
 * not visible, not editable, and reports as 404 rather than 403.
 */
@RestController
@RequestMapping("/api/v1/my-strategies")
@Tag(name = "My strategies",
        description = """
                A user's own strategies, and deploying them to brokers.

                One strategy row holds the ENTIRE configuration: market, instrument, CE and PE \
                strikes, averaging ladder, exits, and one entry per indicator. Deployments point \
                at it, so editing a strategy moves every broker it runs on.""")
public class UserStrategyController extends SecuredController {

    private static final Logger log = LoggerFactory.getLogger(UserStrategyController.class);

    private final UserStrategyService userStrategyService;

    public UserStrategyController(UserStrategyService userStrategyService) {
        this.userStrategyService = userStrategyService;
    }

    @GetMapping
    @Operation(summary = "List the caller's strategies")
    public List<UserStrategyResponse> list(
            @Parameter(description = "true for live, false for archived, omit for all")
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Only strategies built from this template")
            @RequestParam(required = false) UUID strategyId) {
        String email = currentEmail();
        log.info("GET strategies active={} template={} | user={}", active, strategyId, email);
        return userStrategyService.list(email, active, strategyId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one strategy: every setting, plus each indicator's schema",
            description = "The editor shape. The ce*/pe* fields are what you PUT back; legs[] is "
                    + "the same choice derived for display.")
    public UserStrategyResponse get(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("GET strategy={} | user={}", id, email);
        return userStrategyService.get(email, id);
    }

    @GetMapping("/{id}/runtime")
    @Operation(summary = "Bot view: legs, values and signal params, already resolved",
            description = "The legs resolved to what they trade, indicator values coerced to "
                    + "their declared types, and signalParams exactly as they were hashed. "
                    + "No fallback logic left for the caller.")
    public UserStrategyRuntimeResponse runtime(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("GET strategy={} runtime | user={}", id, email);
        return userStrategyService.runtime(email, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Build a strategy from a template",
            description = "Absent fields take their column defaults and absent indicator values "
                    + "take their schema defaults, so a body naming only the template saves a "
                    + "runnable strategy on platform defaults.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = UserStrategyRequest.class),
                    examples = {
                            @ExampleObject(name = "Options, both sides (the spreadsheet)",
                                    description = "EMA High (K) 21 against EMA (D) 9 on NIFTY 5m, "
                                            + "traded as options one strike OTM on both sides, "
                                            + "sized 65 on a doubling ladder with two averaging entries.",
                                    value = """
                                            {
                                              "strategyName": "EMA Averaging",
                                              "name": "NIFTY 21/9 both sides",
                                              "symbol": "NIFTY",
                                              "exchangeCode": "NSE",
                                              "candleDuration": "5m",
                                              "triggerDuration": "5m",
                                              "derivative": "OPTION",
                                              "ceEnabled": true, "ceMoneyness": "OTM", "ceStrikeOffset": 1,
                                              "peEnabled": true, "peMoneyness": "OTM", "peStrikeOffset": 1,
                                              "lotRule": "DOUBLE",
                                              "baseLot": 65,
                                              "averagingCount": 2,
                                              "slPct": 1.5,
                                              "tpPct": 3.0,
                                              "indicators": [
                                                { "indicatorName": "EMA AVERAGING", "params": { "k": 21, "d": 9 } }
                                              ]
                                            }"""),
                            @ExampleObject(name = "Calls only, deep OTM",
                                    description = "One side, five strikes out, no averaging.",
                                    value = """
                                            {
                                              "strategyName": "EMA Averaging",
                                              "name": "NIFTY calls OTM5",
                                              "symbol": "NIFTY",
                                              "exchangeCode": "NSE",
                                              "candleDuration": "15m",
                                              "derivative": "OPTION",
                                              "ceEnabled": true, "ceMoneyness": "OTM", "ceStrikeOffset": 5,
                                              "lotRule": "FIXED",
                                              "baseLot": 75,
                                              "slPct": 2.0,
                                              "indicators": [
                                                { "indicatorName": "EMA AVERAGING", "params": { "k": 50, "d": 21 } }
                                              ]
                                            }"""),
                            @ExampleObject(name = "Futures",
                                    description = "No strike to choose, so both option sides stay off.",
                                    value = """
                                            {
                                              "strategyName": "EMA Averaging",
                                              "name": "NIFTY futures 50/21",
                                              "symbol": "NIFTY",
                                              "exchangeCode": "NSE",
                                              "candleDuration": "5m",
                                              "derivative": "FUT",
                                              "lotRule": "FIXED",
                                              "baseLot": 75,
                                              "indicators": [
                                                { "indicatorName": "EMA AVERAGING", "params": { "k": 50, "d": 21 } }
                                              ]
                                            }"""),
                            @ExampleObject(name = "Minimal - template only",
                                    description = "Valid, and saves on platform defaults. Not "
                                            + "deployable until a symbol and candle are set.",
                                    value = """
                                            { "strategyName": "EMA Averaging" }""")
                    }))
    public UserStrategyResponse create(@RequestBody UserStrategyRequest request) {
        String email = currentEmail();
        log.info("CREATE strategy template={} name={} | user={}",
                request != null ? request.getStrategyName() : null,
                request != null ? request.getName() : null, email);
        return userStrategyService.create(email, request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Retune, re-strike, re-point at another market, or archive",
            description = "Partial: a present field is applied, an absent one is left alone. "
                    + "**Every broker this strategy is deployed on picks the change up at "
                    + "once** - deployments point at this row, they do not copy it.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = UserStrategyRequest.class),
                    examples = {
                            @ExampleObject(name = "Retune the indicator",
                                    description = "params is MERGED, so d keeps its value. This "
                                            + "changes configHash and moves the strategy to a "
                                            + "different shared computation.",
                                    value = """
                                            { "indicators": [
                                                { "indicatorName": "EMA AVERAGING", "params": { "k": 50 } } ] }"""),
                            @ExampleObject(name = "Move the call strike",
                                    value = """
                                            { "ceMoneyness": "OTM", "ceStrikeOffset": 3 }"""),
                            @ExampleObject(name = "Park the put side",
                                    description = "Keeps its tuning; simply stops trading it.",
                                    value = """
                                            { "peEnabled": false }"""),
                            @ExampleObject(name = "Change the ladder",
                                    value = """
                                            { "lotRule": "CUMULATIVE", "baseLot": 65, "averagingCount": 3 }"""),
                            @ExampleObject(name = "Change the exits",
                                    value = """
                                            { "slPct": 2.0, "tpPct": 5.0 }"""),
                            @ExampleObject(name = "Archive",
                                    description = "An archived strategy cannot be deployed. "
                                            + "Existing deployments are untouched.",
                                    value = """
                                            { "active": false }""")
                    }))
    public UserStrategyResponse update(@PathVariable UUID id, @RequestBody UserStrategyRequest request) {
        String email = currentEmail();
        log.info("UPDATE strategy={} | user={}", id, email);
        return userStrategyService.update(email, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete; refused while the strategy is still deployed anywhere",
            description = "409 with the deployment count if any broker still runs it - a cascade "
                    + "here would silently stop trading everywhere. Withdraw them first, or "
                    + "archive with PUT { \"active\": false }.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("DELETE strategy={} | user={}", id, email);
        userStrategyService.delete(email, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deploy")
    @Operation(summary = "Deploy one strategy onto several broker accounts at once",
            description = """
                    Each account gets its own transaction and its own outcome, so an account that \
                    already runs this strategy does not stop the others from starting.

                    **The status is 200 whenever the request itself was well-formed, even if \
                    every target failed** - read `deployed` / `failed` for the summary and \
                    `results[]` for the per-broker detail. Render results, not the status code.""")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = StrategyDeployRequest.class),
                    examples = {
                            @ExampleObject(name = "A whole broker setup",
                                    description = "Fans out to every trading account under that "
                                            + "setup - what \"deploy it on my Dhan\" means when "
                                            + "the login carries three sub-accounts.",
                                    value = """
                                            { "tradeMode": "paper",
                                              "targets": [ { "userBrokerId": "ub000000-1111-4222-8333-444444444444" } ] }"""),
                            @ExampleObject(name = "One account",
                                    value = """
                                            { "tradeMode": "paper",
                                              "multiplier": 1,
                                              "targets": [ { "tradingAccountId": "ta000000-1111-4222-8333-444444444444" } ] }"""),
                            @ExampleObject(name = "Several brokers, per-broker overrides",
                                    description = "Request-level fields are defaults; a target "
                                            + "that sets the same field wins. Here everything "
                                            + "runs on paper at 1x except one account, live at 2x.",
                                    value = """
                                            {
                                              "tradeMode": "paper",
                                              "multiplier": 1,
                                              "executionMode": "FIXED_QTY",
                                              "riskProfileId": "4f5e6d7c-8b9a-4c1d-9e2f-3a4b5c6d7e8f",
                                              "targets": [
                                                { "userBrokerId": "ub000000-1111-4222-8333-444444444444" },
                                                { "tradingAccountId": "ta000000-1111-4222-8333-999999999999",
                                                  "multiplier": 2,
                                                  "tradeMode": "live" }
                                              ]
                                            }""")
                    }))
    public StrategyDeploymentResponse deploy(@PathVariable UUID id,
                                             @RequestBody StrategyDeployRequest request) {
        String email = currentEmail();
        log.info("DEPLOY strategy={} targets={} | user={}",
                id, request != null && request.getTargets() != null ? request.getTargets().size() : 0, email);
        return userStrategyService.deploy(email, id, request);
    }
}
