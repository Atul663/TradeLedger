package com.example.tradeLedger.controller;

import com.example.tradeLedger.service.PlatformStrategyToggleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Platform-wide on/off switches over {@code platform_strategy_toggles} - the
 * operator's kill switch, not a user feature.
 *
 * This predates the strategy module and is deliberately separate from it:
 * <ul>
 *   <li>rows are keyed by an UPPERCASED name string with no foreign key to
 *       {@code strategy_templates}, so a toggle can exist for a strategy the
 *       catalog has never heard of</li>
 *   <li>{@code PUT /{name}/toggle} is MUTUALLY EXCLUSIVE - enabling one row
 *       disables every other row, so at most one strategy is switched on at a
 *       time platform-wide</li>
 *   <li>{@code configJson} is a free-form blob with no validation against any
 *       parameter definition</li>
 * </ul>
 *
 * Nothing a user does here - a saved strategy, a subscription - passes through
 * this table. Use {@code /api/v1/strategy-templates} for the catalog and
 * {@code /api/v1/my-strategies} for a user's own configurations.
 */
@RestController
@RequestMapping("/api/v1/strategy-toggles")
@Tag(name = "Strategy toggles (legacy)",
        description = "Platform-wide on/off switches; mutually exclusive, unrelated to strategy templates")
public class PlatformStrategyToggleController {

    private static final Logger log = LoggerFactory.getLogger(PlatformStrategyToggleController.class);

    private final PlatformStrategyToggleService strategyService;

    public PlatformStrategyToggleController(PlatformStrategyToggleService strategyService) {
        this.strategyService = strategyService;
    }

    private String getEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal != null ? principal.toString() : null;
    }

    private ResponseEntity<?> unauthorized(HttpServletRequest request) {
        log.warn("401 UNAUTHORIZED - No valid token on {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(401).body("Unauthorized: valid Bearer token required");
    }

    @GetMapping
    public ResponseEntity<?> getAllStrategies(HttpServletRequest request) {
        String email = getEmail();
        if (email == null) return unauthorized(request);
        log.info("GET all strategies | user={}", email);
        return ResponseEntity.ok(strategyService.getAllStrategies());
    }

    @GetMapping("/{name}")
    public ResponseEntity<?> getStrategy(@PathVariable String name, HttpServletRequest request) {
        String email = getEmail();
        if (email == null) return unauthorized(request);
        log.info("GET strategy='{}' | user={}", name, email);
        return strategyService.getStrategy(name);
    }

    @PostMapping("/{name}")
    public ResponseEntity<?> createStrategy(@PathVariable String name,
                                            @RequestBody(required = false) Map<String, Object> config,
                                            HttpServletRequest request) {
        String email = getEmail();
        if (email == null) return unauthorized(request);
        log.info("CREATE strategy='{}' | user={}", name, email);
        return strategyService.createStrategy(name, config);
    }

    @PutMapping("/{name}/toggle")
    public ResponseEntity<?> toggleStrategy(@PathVariable String name, HttpServletRequest request) {
        String email = getEmail();
        if (email == null) return unauthorized(request);
        log.info("TOGGLE strategy='{}' | user={}", name, email);
        return strategyService.toggleStrategy(name);
    }

    @PutMapping("/{name}/config")
    public ResponseEntity<?> updateConfig(@PathVariable String name,
                                          @RequestBody Map<String, Object> config,
                                          HttpServletRequest request) {
        String email = getEmail();
        if (email == null) return unauthorized(request);
        log.info("UPDATE config strategy='{}' | user={}", name, email);
        return strategyService.updateConfig(name, config);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<?> deleteStrategy(@PathVariable String name, HttpServletRequest request) {
        String email = getEmail();
        if (email == null) return unauthorized(request);
        log.info("DELETE strategy='{}' | user={}", name, email);
        return strategyService.deleteStrategy(name);
    }
}
