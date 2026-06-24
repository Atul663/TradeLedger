package com.example.tradeLedger.controller;

import com.example.tradeLedger.service.StrategyService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/strategy")
public class StrategyController {

    private static final Logger log = LoggerFactory.getLogger(StrategyController.class);

    private final StrategyService strategyService;

    public StrategyController(StrategyService strategyService) {
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
