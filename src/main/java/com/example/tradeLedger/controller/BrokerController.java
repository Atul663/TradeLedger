package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.BrokerRequest;
import com.example.tradeLedger.dto.BrokerResponse;
import com.example.tradeLedger.service.BrokerService;
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
 * The {@code brokers} catalog - who the platform can route orders through.
 *
 * The list here is what fills the broker dropdown; a user then creates their own
 * setup against one of these at {@code POST /api/v1/my-brokers}.
 *
 * <p><b>Platform master data, not per-user.</b> Unlike everything else in this
 * module these rows are shared, so the reads are unscoped and the writes change
 * what every user sees. Two rules follow from that and are enforced here:
 * {@code code} is immutable, and delete is refused while any setup points at the
 * row - retiring a broker means deactivating it, which stops new setups without
 * breaking the ones that exist.
 *
 * <p><b>Not yet gated by role.</b> Any authenticated caller can write. The rest
 * of the platform's shared catalogs ({@code exchanges}, {@code symbols},
 * {@code risk_profiles}) are read-only for exactly that reason - see
 * {@code ReferenceDataService}. Put these behind an admin check before the API is
 * open to end users.
 */
@RestController
@RequestMapping("/api/v1/brokers")
@Tag(name = "Brokers", description = "The catalog of brokers the platform can trade through")
public class BrokerController extends SecuredController {

    private static final Logger log = LoggerFactory.getLogger(BrokerController.class);

    private final BrokerService brokerService;

    public BrokerController(BrokerService brokerService) {
        this.brokerService = brokerService;
    }

    @GetMapping
    @Operation(summary = "List the brokers an account can trade through")
    public List<BrokerResponse> list(@RequestParam(defaultValue = "true") boolean activeOnly) {
        log.info("GET brokers activeOnly={} | user={}", activeOnly, currentEmail());
        return brokerService.list(activeOnly);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one broker")
    public BrokerResponse get(@PathVariable UUID id) {
        log.info("GET broker={} | user={}", id, currentEmail());
        return brokerService.get(id);
    }

    /** Codes are unique, so an adapter can look its own row up without an id. */
    @GetMapping("/by-code/{code}")
    @Operation(summary = "Get one broker by its unique code, e.g. DELTA")
    public BrokerResponse getByCode(@PathVariable String code) {
        log.info("GET broker code={} | user={}", code, currentEmail());
        return brokerService.getByCode(code);
    }

    /**
     * {@code authType} decides which credential fields the UI asks for later, so
     * it is worth getting right at creation: {@code api_key},
     * {@code oauth_redirect} or {@code totp}.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a broker to the catalog")
    public BrokerResponse create(@RequestBody BrokerRequest request) {
        log.info("CREATE broker '{}' | user={}",
                request != null ? request.getCode() : null, currentEmail());
        return brokerService.create(request);
    }

    /**
     * All or nothing. Useful for seeding the catalog in one call instead of
     * several that could half-succeed.
     */
    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add several brokers in one transaction")
    public List<BrokerResponse> createAll(@RequestBody List<BrokerRequest> requests) {
        log.info("CREATE {} broker(s) | user={}",
                requests != null ? requests.size() : 0, currentEmail());
        return brokerService.createAll(requests);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a broker; its code cannot change")
    public BrokerResponse update(@PathVariable UUID id, @RequestBody BrokerRequest request) {
        log.info("UPDATE broker={} | user={}", id, currentEmail());
        return brokerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a broker; refused while any user's setup points at it")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("DELETE broker={} | user={}", id, currentEmail());
        brokerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
