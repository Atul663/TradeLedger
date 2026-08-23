package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.BrokerCredentialRequest;
import com.example.tradeLedger.dto.BrokerCredentialResponse;
import com.example.tradeLedger.dto.BrokerSetupRequest;
import com.example.tradeLedger.dto.BrokerSetupResponse;
import com.example.tradeLedger.dto.UserBrokerRequest;
import com.example.tradeLedger.dto.UserBrokerResponse;
import com.example.tradeLedger.service.BrokerCredentialService;
import com.example.tradeLedger.service.UserBrokerService;
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
 * The caller's broker setups - step one of getting ready to trade.
 *
 * <pre>
 * POST /api/v1/my-brokers                  set the broker up
 * PUT  /api/v1/my-brokers/{id}/credentials give it an API key
 * POST /api/v1/trading-accounts            create the accounts under it
 * </pre>
 *
 * The key lives here, on the setup, because that is how brokers work: one login,
 * several accounts underneath it. An individual account can still override a
 * field when it genuinely has its own, and resolution is per field - so an
 * account with its own session token keeps inheriting the setup's API key.
 *
 * Everything is scoped to the authenticated caller: another user's setup is not
 * visible, not editable, and reports as 404 rather than 403.
 */
@RestController
@RequestMapping("/api/v1/my-brokers")
@Tag(name = "My brokers", description = "The caller's broker setups and the API keys behind them")
public class UserBrokerController extends SecuredController {

    private static final Logger log = LoggerFactory.getLogger(UserBrokerController.class);

    private final UserBrokerService userBrokerService;
    private final BrokerCredentialService credentialService;

    public UserBrokerController(UserBrokerService userBrokerService,
                                BrokerCredentialService credentialService) {
        this.userBrokerService = userBrokerService;
        this.credentialService = credentialService;
    }

    @GetMapping
    @Operation(summary = "List the caller's broker setups")
    public List<UserBrokerResponse> list(@RequestParam(required = false) UUID brokerId,
                                         @RequestParam(required = false) Boolean active) {
        String email = currentEmail();
        log.info("GET broker setups brokerId={} active={} | user={}", brokerId, active, email);
        return userBrokerService.list(email, brokerId, active);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one broker setup")
    public UserBrokerResponse get(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("GET broker setup={} | user={}", id, email);
        return userBrokerService.get(email, id);
    }

    /**
     * {@code label} is optional and defaults to the broker's own name, so the
     * common case of one setup per broker is a one-field request. Two logins with
     * the same broker need two distinct labels.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Set up a broker for the caller")
    public UserBrokerResponse create(@RequestBody UserBrokerRequest request) {
        String email = currentEmail();
        log.info("CREATE broker setup broker='{}' label='{}' | user={}",
                request != null ? request.getBrokerCode() : null,
                request != null ? request.getLabel() : null, email);
        return userBrokerService.create(email, request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename or deactivate a setup; the broker itself cannot change")
    public UserBrokerResponse update(@PathVariable UUID id, @RequestBody UserBrokerRequest request) {
        String email = currentEmail();
        log.info("UPDATE broker setup={} | user={}", id, email);
        return userBrokerService.update(email, id, request);
    }

    /**
     * The whole "add a broker" wizard in one call: setup, first account and
     * API key, in one transaction.
     *
     * The three individual endpoints still work and are still the way to change
     * any one of these later. This exists because doing them in sequence from a
     * form leaves a hole - a key rejected on the third call would strand a setup
     * and an account that cannot authenticate. Here that rolls all three back.
     *
     * {@code account} and {@code credentials} are both optional. Credentials go
     * on the SETUP by default, which is what you want when the key belongs to
     * the login rather than to one account - every later account inherits it.
     */
    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a broker setup, its first account and its API key in one call",
            description = "One transaction: a rejected key takes the setup and the account back "
                    + "out with it. Add more accounts later with POST /api/v1/trading-accounts - "
                    + "that is what makes a userBrokerId deploy target fan out.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = BrokerSetupRequest.class),
                    examples = {
                            @ExampleObject(name = "api_key broker (Dhan)",
                                    description = "The key belongs to the login, so it goes on the "
                                            + "SETUP and every later account inherits it.",
                                    value = """
                                            {
                                              "brokerCode": "DHAN",
                                              "label": "My Dhan",
                                              "account": { "accountName": "main", "brokerAccountId": "1100112233" },
                                              "credentials": {
                                                "apiKey": "dhan-api-key-xxxx",
                                                "apiSecret": "dhan-api-secret-yyyy",
                                                "clientId": "1100112233"
                                              },
                                              "credentialsScope": "SETUP"
                                            }"""),
                            @ExampleObject(name = "oauth_redirect broker (Zerodha)",
                                    value = """
                                            {
                                              "brokerCode": "ZERODHA",
                                              "label": "My Zerodha",
                                              "account": { "accountName": "kite-main", "brokerAccountId": "AB1234" },
                                              "credentials": {
                                                "apiKey": "kite-api-key",
                                                "apiSecret": "kite-api-secret",
                                                "redirectUrl": "https://app.example.com/broker/callback",
                                                "accessToken": "kite-access-token",
                                                "tokenExpiresAt": "2026-08-24T09:15:00+05:30"
                                              },
                                              "credentialsScope": "SETUP"
                                            }"""),
                            @ExampleObject(name = "Setup only, finish later",
                                    description = "Both account and credentials are optional.",
                                    value = """
                                            { "brokerCode": "DHAN", "label": "My Dhan" }""")
                    }))
    public BrokerSetupResponse setup(@RequestBody BrokerSetupRequest request) {
        String email = currentEmail();
        // The body holds secrets: log the broker, never the credentials.
        log.info("SETUP broker='{}' | user={}",
                request != null ? request.getBrokerCode() : null, email);
        return userBrokerService.setup(email, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a setup; refused while trading accounts still use it")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("DELETE broker setup={} | user={}", id, email);
        userBrokerService.delete(email, id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------ credentials

    /**
     * Masked. Secrets are never returned - not even to the user who wrote them.
     * The API key comes back as its last four characters and every other secret
     * as a boolean, which is all a form needs to show "set, change?".
     */
    @GetMapping("/{id}/credentials")
    @Operation(summary = "Credential status for a setup: what is set, never the values")
    public BrokerCredentialResponse getCredentials(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("GET credentials for broker setup={} | user={}", id, email);
        return credentialService.getForSetup(email, id);
    }

    /**
     * Create or rotate the key every account under this setup uses. Partial by
     * design: a field left out keeps its stored value, so posting today's access
     * token does not mean resending the API secret the caller cannot read back.
     * An empty string clears a field.
     */
    @PutMapping("/{id}/credentials")
    @Operation(summary = "Store or rotate the API credentials for a broker setup")
    public BrokerCredentialResponse putCredentials(@PathVariable UUID id,
                                                   @RequestBody BrokerCredentialRequest request) {
        String email = currentEmail();
        // The body holds secrets: log which setup, never what was sent.
        log.info("PUT credentials for broker setup={} | user={}", id, email);
        return credentialService.upsertForSetup(email, id, request);
    }

    /** Accounts that override with their own credentials keep working. */
    @DeleteMapping("/{id}/credentials")
    @Operation(summary = "Remove a setup's credentials")
    public ResponseEntity<Void> deleteCredentials(@PathVariable UUID id) {
        String email = currentEmail();
        log.info("DELETE credentials for broker setup={} | user={}", id, email);
        credentialService.deleteForSetup(email, id);
        return ResponseEntity.noContent().build();
    }
}
